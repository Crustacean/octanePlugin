package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Jenkinsfile4TriggerTest {
  private static final Path JENKINSFILE = Path.of("examples", "Jenkinsfile4");

  @Test
  public void triggersAndWaitsForTheConfiguredOctaneTestJob() throws IOException {
    String source = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);

    assertTrue(source.contains("agent none"));
    assertTrue(source.contains("name: 'OCTANE_TEST_JOB'"));
    assertTrue(source.contains("defaultValue: 'dashboardVariables'"));
    assertTrue(source.contains("name: 'OCTANE_TEST_BRANCH'"));
    assertTrue(source.contains("defaultValue: 'main'"));
    assertTrue(
        source.contains(
            "downstreamBranch ? \"${downstreamJob}/${downstreamBranch}\" : downstreamJob"));
    assertTrue(source.contains("stage('Trigger and poll Octane Tests results')"));
    assertTrue(source.contains("job: downstreamTarget"));
    assertTrue(source.contains("propagate: false"));
    assertTrue(source.contains("wait: true"));
    assertTrue(source.contains("stage('Deploy to UAT')"));
    assertTrue(source.contains("env.OCTANE_TEST_RESULT == 'SUCCESS'"));
    assertTrue(source.contains("Octane results PASS. Deploying."));
    assertTrue(source.contains("Octane results FAIL. Aborting deployment."));
  }
}
