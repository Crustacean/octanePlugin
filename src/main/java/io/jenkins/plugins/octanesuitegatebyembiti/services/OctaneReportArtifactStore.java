package io.jenkins.plugins.octanesuitegatebyembiti.services;

import hudson.model.Run;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportArtifactMetadata;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class OctaneReportArtifactStore {
  static final String ROOT_DIRECTORY = "octane-suite-gate";
  static final String INDEX_FILE = "octane-index.json";
  static final String RESULTS_FILE = "octane-results.json";
  static final String SNAPSHOT_FILE = "octane-snapshot.bin.gz";
  static final long MAX_ARTIFACT_BYTES = 64L * 1024L * 1024L;
  private static final long MAX_DESERIALIZED_REFERENCES = 1_000_000L;
  private static final long MAX_ARRAY_LENGTH = 1_000_000L;
  private static final long MAX_DESERIALIZATION_DEPTH = 64L;

  private final ObjectMapper objectMapper;
  private final OctaneReportDataMapper dataMapper;

  public OctaneReportArtifactStore() {
    this(new ObjectMapper(), new OctaneReportDataMapper());
  }

  OctaneReportArtifactStore(ObjectMapper objectMapper, OctaneReportDataMapper dataMapper) {
    this.objectMapper = objectMapper;
    this.dataMapper = dataMapper;
  }

  public OctaneReportArtifactMetadata publish(Run<?, ?> run, OctaneGateReportSnapshot snapshot)
      throws IOException {
    OctaneReportDataMapper.ReportData reportData = dataMapper.map(snapshot);
    byte[] completeBytes = objectMapper.writeValueAsBytes(reportData.complete());
    byte[] indexBytes = objectMapper.writeValueAsBytes(reportData.index());
    String checksum = sha256(completeBytes);
    Path root = root(run);
    Path destination = root.resolve(checksum);
    if (!Files.isDirectory(destination)) {
      Files.createDirectories(root);
      Path temporary = root.resolve(".tmp-" + UUID.randomUUID());
      Files.createDirectories(temporary);
      boolean published = false;
      try {
        writeBytes(temporary.resolve(RESULTS_FILE), completeBytes);
        writeBytes(temporary.resolve(INDEX_FILE), indexBytes);
        int sectionIndex = 0;
        for (Map<String, Object> section : reportData.sections()) {
          writeBytes(
              temporary.resolve(sectionFile(sectionIndex++)),
              objectMapper.writeValueAsBytes(section));
        }
        writeSnapshot(temporary.resolve(SNAPSHOT_FILE), snapshot);
        moveDirectory(temporary, destination);
        published = true;
      } finally {
        if (!published) {
          deleteRecursively(temporary);
        }
      }
    }
    return new OctaneReportArtifactMetadata(
        OctaneReportDataMapper.SCHEMA_VERSION,
        ROOT_DIRECTORY + "/" + checksum,
        checksum,
        snapshot.getUpdatedAt(),
        completeBytes.length,
        reportData.sections().size(),
        snapshot.isClientRenderedReport(),
        snapshot.isBuilding());
  }

  public OctaneGateReportSnapshot loadSnapshot(Run<?, ?> run, OctaneReportArtifactMetadata metadata)
      throws IOException {
    if (run == null || metadata == null || !metadata.isAvailable()) {
      return null;
    }
    Path path = artifactDirectory(run, metadata).resolve(SNAPSHOT_FILE);
    if (!Files.isRegularFile(path)) {
      return null;
    }
    verifyArtifactSize(path);
    try (ObjectInputStream input =
        new ObjectInputStream(
            new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))))) {
      input.setObjectInputFilter(OctaneReportArtifactStore::filterSnapshotObject);
      Object value = input.readObject();
      if (value instanceof OctaneGateReportSnapshot reportSnapshot) {
        return reportSnapshot;
      }
      throw new IOException("Octane report snapshot artifact has an unexpected type.");
    } catch (ClassNotFoundException e) {
      throw new IOException("Unable to load the Octane report snapshot artifact.", e);
    }
  }

  public byte[] readIndex(Run<?, ?> run, OctaneReportArtifactMetadata metadata) throws IOException {
    return readArtifact(run, metadata, INDEX_FILE);
  }

  public byte[] readResults(Run<?, ?> run, OctaneReportArtifactMetadata metadata)
      throws IOException {
    return readArtifact(run, metadata, RESULTS_FILE);
  }

  public byte[] readSectionPage(
      Run<?, ?> run, OctaneReportArtifactMetadata metadata, int section, int cursor, int limit)
      throws IOException {
    byte[] sectionBytes = readArtifact(run, metadata, sectionFile(section));
    ObjectNode source = (ObjectNode) objectMapper.readTree(sectionBytes);
    ArrayNode bars = source.withArray("bars");
    int safeCursor = Math.min(Math.max(0, cursor), bars.size());
    int safeLimit = Math.min(200, Math.max(1, limit));
    int end = Math.min(bars.size(), safeCursor + safeLimit);
    ArrayNode page = objectMapper.createArrayNode();
    for (int index = safeCursor; index < end; index++) {
      page.add(bars.get(index));
    }
    source.set("bars", page);
    source.put("cursor", safeCursor);
    source.put("nextCursor", end < bars.size() ? end : -1);
    source.put("totalBars", bars.size());
    return objectMapper.writeValueAsBytes(source);
  }

  public void deleteGeneration(Run<?, ?> run, OctaneReportArtifactMetadata metadata) {
    if (run == null || metadata == null || !metadata.isAvailable()) {
      return;
    }
    try {
      deleteRecursively(artifactDirectory(run, metadata));
    } catch (IOException ignored) {
      // A stale generation is harmless and can be cleaned with the build later.
    }
  }

  private byte[] readArtifact(Run<?, ?> run, OctaneReportArtifactMetadata metadata, String fileName)
      throws IOException {
    if (run == null || metadata == null || !metadata.isAvailable()) {
      throw new IOException("Octane report data is not available for this build.");
    }
    Path path = artifactDirectory(run, metadata).resolve(fileName).normalize();
    Path directory = artifactDirectory(run, metadata).normalize();
    if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
      throw new IOException("Octane report data is incomplete for this build.");
    }
    verifyArtifactSize(path);
    return Files.readAllBytes(path);
  }

  private static ObjectInputFilter.Status filterSnapshotObject(ObjectInputFilter.FilterInfo info) {
    if (exceedsDeserializationLimits(info)) {
      return ObjectInputFilter.Status.REJECTED;
    }
    Class<?> serialClass = info.serialClass();
    if (serialClass == null) {
      return ObjectInputFilter.Status.UNDECIDED;
    }
    while (serialClass.isArray()) {
      serialClass = serialClass.getComponentType();
    }
    return serialClass.isPrimitive() || isAllowedSnapshotClass(serialClass.getName())
        ? ObjectInputFilter.Status.ALLOWED
        : ObjectInputFilter.Status.REJECTED;
  }

  private static boolean exceedsDeserializationLimits(ObjectInputFilter.FilterInfo info) {
    return info.depth() > MAX_DESERIALIZATION_DEPTH
        || info.references() > MAX_DESERIALIZED_REFERENCES
        || info.streamBytes() > MAX_ARTIFACT_BYTES
        || (info.arrayLength() >= 0 && info.arrayLength() > MAX_ARRAY_LENGTH);
  }

  private static boolean isAllowedSnapshotClass(String className) {
    return className.startsWith("io.jenkins.plugins.octanesuitegatebyembiti.models.")
        || className.startsWith("io.jenkins.plugins.octanesuitegatebyembiti.entities.")
        || className.startsWith("java.lang.")
        || className.startsWith("java.time.")
        || className.startsWith("java.util.");
  }

  private void verifyArtifactSize(Path path) throws IOException {
    if (Files.size(path) > MAX_ARTIFACT_BYTES) {
      throw new IOException(
          "Octane report artifact exceeds the " + MAX_ARTIFACT_BYTES + " byte safety limit.");
    }
  }

  private Path artifactDirectory(Run<?, ?> run, OctaneReportArtifactMetadata metadata) {
    Path root = run.getRootDir().toPath().toAbsolutePath().normalize();
    Path directory = root.resolve(metadata.getArtifactDirectory()).normalize();
    if (!directory.startsWith(root)) {
      throw new IllegalArgumentException("Invalid Octane report artifact path.");
    }
    return directory;
  }

  private Path root(Run<?, ?> run) {
    return run.getRootDir().toPath().resolve(ROOT_DIRECTORY);
  }

  private void writeSnapshot(Path path, OctaneGateReportSnapshot snapshot) throws IOException {
    try (ObjectOutputStream output =
        new ObjectOutputStream(
            new GZIPOutputStream(
                new BufferedOutputStream(
                    Files.newOutputStream(
                        path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))))) {
      output.writeObject(snapshot);
    }
  }

  private void writeBytes(Path path, byte[] content) throws IOException {
    Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }

  private void moveDirectory(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.FileAlreadyExistsException e) {
      deleteRecursively(source);
    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
      Files.move(source, destination);
    }
  }

  private void deleteRecursively(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      for (Path item : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
        Files.deleteIfExists(item);
      }
    }
  }

  private String sectionFile(int section) {
    return "section-" + section + ".json";
  }

  private String sha256(byte[] value) throws IOException {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 is unavailable while writing Octane report data.", e);
    }
  }
}
