package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportArtifactMetadata;
import java.nio.file.Files;
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
    assertEquals(500, reloaded.getReportSections().get(0).getSuiteRuns().size());
    assertTrue(
        Files.isRegularFile(
            build
                .getRootDir()
                .toPath()
                .resolve(metadata.getArtifactDirectory())
                .resolve(OctaneReportArtifactStore.RESULTS_FILE)));
  }
}
