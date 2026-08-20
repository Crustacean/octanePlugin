package io.jenkins.plugins.octanesuitegatebyembiti.services;

import org.jenkinsci.plugins.workflow.steps.StepContext;

public interface OctaneEmailReportSender {
  void send(
      StepContext context,
      String recipients,
      String from,
      String replyTo,
      String subject,
      String body,
      String attachmentsPattern,
      boolean important)
      throws Exception;
}
