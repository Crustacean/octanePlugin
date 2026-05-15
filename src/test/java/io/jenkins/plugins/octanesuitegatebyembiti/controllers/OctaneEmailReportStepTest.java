package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneEmailReportStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void composesToCcAndBccRecipientsForEmailExtension() {
    assertEquals(
        "qa@example.com,dev@example.com,cc:lead@example.com,bcc:audit@example.com",
        OctaneEmailReportStep.composeRecipients(
            "qa@example.com dev@example.com", "lead@example.com", "audit@example.com"));
  }

  @Test
  public void appendsReportUrlToBody() {
    assertEquals(
        "Attached is the Octane report-zone screenshot.\n\nOctane Gate Report: job/1/report/",
        OctaneEmailReportStep.appendReportUrl("", "job/1/report/"));
  }

  @Test
  public void missingReportActionCanWarnAndContinue() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneEmailReport(to: 'qa@example.com', onFailure: 'WARN') }", true));

    WorkflowRun run = jenkins.buildAndAssertSuccess(job);

    jenkins.assertLogContains("WARNING: Octane email report failed", run);
  }

  @Test
  public void missingReportActionCanMarkBuildUnstable() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneEmailReport(to: 'qa@example.com', onFailure: 'UNSTABLE') }", true));

    WorkflowRun run = job.scheduleBuild2(0).get();

    jenkins.assertBuildStatus(Result.UNSTABLE, run);
    jenkins.assertLogContains("Marking build UNSTABLE and continuing.", run);
  }

  @Test
  public void missingReportActionCanFailBuild() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneEmailReport(to: 'qa@example.com', onFailure: 'FAILURE') }", true));

    WorkflowRun run = job.scheduleBuild2(0).get();

    jenkins.assertBuildStatus(Result.FAILURE, run);
    jenkins.assertLogContains("Octane email report failed", run);
  }

  @Test
  public void invalidFailureModeFailsClearly() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneEmailReport(to: 'qa@example.com', onFailure: 'BROKEN') }", true));

    WorkflowRun run = job.scheduleBuild2(0).get();

    jenkins.assertBuildStatus(Result.FAILURE, run);
    String log = JenkinsRule.getLog(run);
    assertTrue(log.contains("onFailure must be one of UNSTABLE, FAILURE, or WARN."));
  }
}
