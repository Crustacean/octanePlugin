package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Result;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneProgressEmailScheduler;
import java.time.Duration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneCronProgressEmailStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void readsProgressEmailStalenessThresholdFromEnvironment() throws Exception {
    assertEquals(
        Duration.ofMinutes(1L), OctaneCronProgressEmailStep.stalenessThreshold(new EnvVars()));
    EnvVars configured = new EnvVars();
    configured.put(OctaneCronProgressEmailStep.STALENESS_THRESHOLD_ENV, "7");
    assertEquals(
        Duration.ofMinutes(7L), OctaneCronProgressEmailStep.stalenessThreshold(configured));

    configured.put(OctaneCronProgressEmailStep.STALENESS_THRESHOLD_ENV, "-1");
    try {
      OctaneCronProgressEmailStep.stalenessThreshold(configured);
      fail("Expected a negative staleness threshold to be rejected.");
    } catch (AbortException expected) {
      assertTrue(expected.getMessage().contains("whole number of zero or greater"));
    }
  }

  @Test
  public void normalizesProgressEmailIntervalTimeoutFlag() throws Exception {
    EnvVars configured = new EnvVars();
    assertFalse(OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured));

    configured.put(OctaneCronProgressEmailStep.INTERVAL_TIMEOUT_ENV, " TRUE ");
    assertTrue(OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured));
    configured.put(OctaneCronProgressEmailStep.INTERVAL_TIMEOUT_ENV, "1");
    assertTrue(OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured));
    configured.put(OctaneCronProgressEmailStep.INTERVAL_TIMEOUT_ENV, " False ");
    assertFalse(OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured));
    configured.put(OctaneCronProgressEmailStep.INTERVAL_TIMEOUT_ENV, "0");
    assertFalse(OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured));

    configured.put(OctaneCronProgressEmailStep.INTERVAL_TIMEOUT_ENV, "sometimes");
    try {
      OctaneCronProgressEmailStep.intervalTimeoutEnabled(configured);
      fail("Expected an unsupported interval timeout value to be rejected.");
    } catch (AbortException expected) {
      assertTrue(expected.getMessage().contains("true, false, 1, or 0"));
    }
  }

  @Test
  public void firstEmailAlwaysSendsAndSubsequentTicksHonorTimeoutFlag() {
    assertTrue(OctaneCronProgressEmailStep.shouldSendProgressEmail(null, 0.0, true));
    assertFalse(OctaneCronProgressEmailStep.shouldSendProgressEmail(40.0, 40.0, true));
    assertTrue(OctaneCronProgressEmailStep.shouldSendProgressEmail(40.0, 41.0, true));
    assertTrue(OctaneCronProgressEmailStep.shouldSendProgressEmail(40.0, 40.0, false));
  }

  @Test
  public void blankCronDisablesEmailAndRunsBody() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { withEnv(['PROGRESS_EMAIL_INTERVAL_TIMEOUT=invalid']) { "
                + "octaneCronProgressEmail(cron: '', to: 'qa@example.com') { } } }",
            true));

    WorkflowRun run = jenkins.buildAndAssertSuccess(job);

    jenkins.assertLogContains("PROGRESS_EMAIL_INTERVAL_CRONJOB is blank", run);
    assertEquals(0, OctaneProgressEmailScheduler.get().activeScheduleCount());
  }

  @Test
  public void invalidCronFailsBeforeRunningBody() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneCronProgressEmail(cron: 'not cron', to: 'qa@example.com') { } }", true));

    WorkflowRun run = job.scheduleBuild2(0).get();

    jenkins.assertBuildStatus(Result.FAILURE, run);
    jenkins.assertLogContains("Unable to schedule Octane progress emails", run);
    assertEquals(0, OctaneProgressEmailScheduler.get().activeScheduleCount());
  }

  @Test
  public void completedBodyImmediatelyCancelsFutureCronTrigger() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneCronProgressEmail(cron: '0 0 1 1 *', to: 'qa@example.com') { } }", true));

    WorkflowRun run = jenkins.buildAndAssertSuccess(job);

    jenkins.assertLogContains("Cron job time: 0 0 1 1 * will run \"According to cron fields:", run);
    jenkins.assertLogContains("next at ", run);
    assertEquals(0, OctaneProgressEmailScheduler.get().activeScheduleCount());
  }
}
