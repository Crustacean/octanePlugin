package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
    String relativeDirectory = OctaneReportArtifactStore.ROOT_DIRECTORY + "/malicious";
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
            1, relativeDirectory, "malicious", Instant.now().toString(), 1L, 0, false, false);

    IOException failure =
        assertThrows(
            IOException.class, () -> new OctaneReportArtifactStore().loadSnapshot(build, metadata));

    assertTrue(failure.getMessage().contains("filter status: REJECTED"));
  }
}
