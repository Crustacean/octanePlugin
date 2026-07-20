package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportScreenshot;
import java.util.concurrent.atomic.AtomicReference;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class OctaneEmailReportStepTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @After
  public void resetServices() {
    OctaneEmailReportStep.resetServicesForTesting();
  }

  @Test
  public void composesToCcAndBccRecipientsForEmailExtension() {
    assertEquals(
        "qa@example.com,dev@example.com,cc:lead@example.com,bcc:audit@example.com",
        OctaneEmailReportStep.composeRecipients(
            "qa@example.com dev@example.com", "lead@example.com", "audit@example.com"));
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

  @Test
  public void ongoingIntervalCalculationSendsRenderedReport() throws Exception {
    AtomicReference<String> sentRecipients = new AtomicReference<>();
    AtomicReference<String> sentSubject = new AtomicReference<>();
    AtomicReference<String> sentBody = new AtomicReference<>();
    AtomicReference<String> sentAttachment = new AtomicReference<>();

    OctaneEmailReportStep.setServicesForTesting(
        (action, workspace, envVars, launcher, listener, browserPath, viewportWidth, theme) -> {
          FilePath reportDirectory = workspace.child("interval-email-test");
          reportDirectory.mkdirs();
          FilePath htmlFile = reportDirectory.child("report.html");
          FilePath screenshotFile = reportDirectory.child("report.png");
          htmlFile.write("<html><body>Report</body></html>", "UTF-8");
          screenshotFile.write("screenshot", "UTF-8");
          return new OctaneReportScreenshot(
              htmlFile, screenshotFile, "interval-email-test/report.png");
        },
        (context, recipients, from, replyTo, subject, body, attachmentsPattern) -> {
          sentRecipients.set(recipients);
          sentSubject.set(subject);
          sentBody.set(body);
          sentAttachment.set(attachmentsPattern);
        });

    WorkflowJob job = jenkins.createProject(WorkflowJob.class, "interval-email-success");
    job.setDefinition(
        new CpsFlowDefinition(
            """
            node {
              long gateDeadlineMillis = System.currentTimeMillis() + 120000L
              long nowMillis = System.currentTimeMillis()
              long remainingMillis = gateDeadlineMillis - nowMillis
              long remainingSeconds = (remainingMillis + 999L) / 1000L
              long remainingMinutes = (remainingSeconds + 59L) / 60L
              octaneEmailReport(
                  to: 'qa@example.com',
                  subject: 'Interval {{REMAINING_TIME}}',
                  body: 'State {{GATE_RESULT}} with {{REMAINING_TIME}} '
                      + 'updated {{UPDATED_AT_TEXT}} '
                      + '{{EXECUTION_DETAILS}} {{REPORT_SCREENSHOT}}',
                  onFailure: 'FAILURE',
                  theme: 'DARK',
                  archiveScreenshot: false)
            }
            """,
            true));

    WorkflowRun run = jenkins.buildAndAssertSuccess(job);

    assertEquals("qa@example.com", sentRecipients.get());
    assertTrue(sentSubject.get().startsWith("Interval "));
    assertTrue(sentSubject.get().endsWith("remaining"));
    assertFalse(sentSubject.get().contains("{{REMAINING_TIME}}"));
    assertTrue(sentBody.get().contains("color:#FF9F0A;font-weight:700;\">ONGOING"));
    assertFalse(sentBody.get().contains("{{REMAINING_TIME}}"));
    assertTrue(sentBody.get().contains("src=\"cid:report.png\""));
    assertFalse(sentBody.get().contains("{{UPDATED_AT_TEXT}}"));
    assertEquals("interval-email-test/report.png", sentAttachment.get());
    jenkins.assertLogContains("Jenkins Email Extension completed the SMTP handoff", run);
  }

  @TestExtension
  public static class AttachIntervalReportAction extends RunListener<WorkflowRun> {
    @Override
    public void onStarted(WorkflowRun run, TaskListener listener) {
      if ("interval-email-success".equals(run.getParent().getName())) {
        run.addAction(new OctaneGateReportAction());
      }
    }
  }
}
