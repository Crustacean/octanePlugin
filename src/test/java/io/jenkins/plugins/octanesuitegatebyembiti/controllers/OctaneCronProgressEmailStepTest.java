package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertEquals;

import hudson.model.Result;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneProgressEmailScheduler;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneCronProgressEmailStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void blankCronDisablesEmailAndRunsBody() throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            "node { octaneCronProgressEmail(cron: '', to: 'qa@example.com') { } }", true));

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

    assertEquals(0, OctaneProgressEmailScheduler.get().activeScheduleCount());
  }
}
