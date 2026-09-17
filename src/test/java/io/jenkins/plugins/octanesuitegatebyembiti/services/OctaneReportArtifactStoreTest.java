package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportArtifactMetadata;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import tools.jackson.databind.ObjectMapper;

public class OctaneReportArtifactStoreTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void publishesAtomicBoundedArtifactsAndReloadsCompatibilitySnapshot() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    OctaneGateReportSnapshot snapshot = OctaneScaleTestFixture.snapshot(0, 500, 1);
    OctaneReportArtifactStore store = new OctaneReportArtifactStore();

    OctaneReportArtifactMetadata metadata = store.publish(build, snapshot);

    assertTrue(metadata.isAvailable());
    assertEquals(OctaneReportDataMapper.SCHEMA_VERSION, metadata.getSchemaVersion());
    assertTrue(metadata.getJsonSize() < 5_000_000L);
    assertTrue(store.readIndex(build, metadata).length < 250_000);
    assertEquals(1, metadata.getSectionCount());
    OctaneGateReportSnapshot reloaded = store.loadSnapshot(build, metadata);
    assertNotNull(reloaded);
    assertEquals(snapshot.getUpdatedAt(), reloaded.getUpdatedAt());
    assertEquals(snapshot.getSuiteAttributions(), reloaded.getSuiteAttributions());
    assertEquals(1, reloaded.getReportSections().get(0).getSuiteRuns().size());
    assertEquals("Status", reloaded.getReportSections().get(0).getXAxis());
    assertEquals(500, reloaded.getProjectTestTotal());
    assertTrue(
        Files.isRegularFile(
            build
                .getRootDir()
                .toPath()
                .resolve(metadata.getArtifactDirectory())
                .resolve(OctaneReportArtifactStore.RESULTS_FILE)));
  }

  @Test
  public void rejectsUnexpectedClassesInPersistedSnapshotArtifact() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    String checksum = "a".repeat(64);
    String relativeDirectory = OctaneReportArtifactStore.ROOT_DIRECTORY + "/" + checksum;
    Path directory = build.getRootDir().toPath().resolve(relativeDirectory);
    Files.createDirectories(directory);
    Path snapshotPath = directory.resolve(OctaneReportArtifactStore.SNAPSHOT_FILE);
    try (ObjectOutputStream output =
        new ObjectOutputStream(
            new GZIPOutputStream(
                new BufferedOutputStream(
                    Files.newOutputStream(snapshotPath, StandardOpenOption.CREATE_NEW))))) {
      output.writeObject(new File("unexpected-class"));
    }
    OctaneReportArtifactMetadata metadata =
        new OctaneReportArtifactMetadata(
            1, relativeDirectory, checksum, Instant.now().toString(), 1L, 0, false, false);

    IOException failure =
        assertThrows(
            IOException.class, () -> new OctaneReportArtifactStore().loadSnapshot(build, metadata));

    assertTrue(failure.getMessage().contains("filter status: REJECTED"));
  }

  @Test
  public void deletesNestedArtifactTreesInPostOrder() throws Exception {
    Path root = Files.createTempDirectory("octane-artifact-cleanup-");
    Path nested = Files.createDirectories(root.resolve("one/two/three"));
    Files.writeString(nested.resolve("report.json"), "{}");

    new OctaneReportArtifactStore().deleteRecursively(root);

    assertFalse(Files.exists(root));
  }

  @Test
  public void servesOnlyValidatedHtmlSafeJsonAndPreservesDecodedText() throws Exception {
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(jenkins.createFreeStyleProject());
    OctaneReportArtifactStore store = new OctaneReportArtifactStore();
    OctaneReportArtifactMetadata metadata =
        store.publish(build, OctaneScaleTestFixture.snapshot(0, 1, 1));
    Path directory = build.getRootDir().toPath().resolve(metadata.getArtifactDirectory());
    String text = "</script><img src=x onerror=alert('x')>&";
    String json = new ObjectMapper().writeValueAsString(java.util.Map.of("name", text));
    for (String file :
        List.of(
            OctaneReportArtifactStore.INDEX_FILE,
            OctaneReportArtifactStore.RESULTS_FILE,
            "section-0.json")) {
      Files.writeString(directory.resolve(file), json);
    }
    for (byte[] response :
        List.of(
            store.readIndex(build, metadata),
            store.readResults(build, metadata),
            store.readSectionPage(build, metadata, 0, 0, 10))) {
      String body = new String(response, StandardCharsets.UTF_8);
      assertFalse(body.contains("<"));
      assertFalse(body.contains(">"));
      assertFalse(body.contains("&"));
      assertEquals(text, new ObjectMapper().readTree(body).path("name").asText());
    }
    for (String invalid : List.of("<script>alert(1)</script>", "{} {}", "[]", "null")) {
      Files.writeString(directory.resolve(OctaneReportArtifactStore.INDEX_FILE), invalid);
      assertThrows(IOException.class, () -> store.readIndex(build, metadata));
    }
    assertThrows(IOException.class, () -> store.readSectionPage(build, metadata, -1, 0, 10));
    assertThrows(
        IOException.class,
        () -> store.readSectionPage(build, metadata, metadata.getSectionCount(), 0, 10));
  }

  @Test
  public void rejectsTraversalSymlinksAndOversizedArtifacts() throws Exception {
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(jenkins.createFreeStyleProject());
    OctaneReportArtifactStore store = new OctaneReportArtifactStore();
    OctaneReportArtifactMetadata metadata =
        store.publish(build, OctaneScaleTestFixture.snapshot(0, 1, 1));
    Path root = build.getRootDir().toPath();
    Path directory = root.resolve(metadata.getArtifactDirectory());
    for (String path :
        List.of("../outside", directory.toString(), "octane-suite-gate/../outside")) {
      OctaneReportArtifactMetadata invalid =
          new OctaneReportArtifactMetadata(
              1, path, metadata.getChecksum(), Instant.now().toString(), 1, 1, false, false);
      assertThrows(IOException.class, () -> store.readIndex(build, invalid));
      store.deleteGeneration(build, invalid);
      assertTrue(Files.isDirectory(directory));
    }
    Path outside = root.resolve("outside.json");
    Files.writeString(outside, "{}");
    for (String file :
        List.of(OctaneReportArtifactStore.INDEX_FILE, OctaneReportArtifactStore.SNAPSHOT_FILE)) {
      Path artifact = directory.resolve(file);
      Files.delete(artifact);
      Files.createSymbolicLink(artifact, outside);
      assertThrows(
          IOException.class,
          () -> {
            if (file.equals(OctaneReportArtifactStore.INDEX_FILE)) {
              store.readIndex(build, metadata);
            } else {
              store.loadSnapshot(build, metadata);
            }
          });
      Files.delete(artifact);
    }
    Path index = directory.resolve(OctaneReportArtifactStore.INDEX_FILE);
    try (java.nio.channels.FileChannel channel =
        java.nio.channels.FileChannel.open(
            index, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      channel.position(OctaneReportArtifactStore.MAX_ARTIFACT_BYTES);
      channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
    }
    assertThrows(IOException.class, () -> store.readIndex(build, metadata));
    Path moved = root.resolve("moved-generation");
    Files.move(directory, moved);
    Files.createSymbolicLink(directory, moved);
    assertThrows(IOException.class, () -> store.readIndex(build, metadata));
    store.deleteGeneration(build, metadata);
    assertTrue(Files.exists(moved));
    Files.delete(directory);
    Path artifactRoot = root.resolve(OctaneReportArtifactStore.ROOT_DIRECTORY);
    Files.delete(artifactRoot);
    Files.createSymbolicLink(artifactRoot, moved);
    assertThrows(IOException.class, () -> store.readIndex(build, metadata));
    assertThrows(
        IOException.class, () -> store.publish(build, OctaneScaleTestFixture.snapshot(0, 1, 1)));
    assertEquals("{}", Files.readString(outside));
  }
}
