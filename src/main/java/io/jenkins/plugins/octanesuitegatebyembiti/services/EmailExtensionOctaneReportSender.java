package io.jenkins.plugins.octanesuitegatebyembiti.services;

import hudson.plugins.emailext.EmailExtStep;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public class EmailExtensionOctaneReportSender implements OctaneEmailReportSender {
  @Override
  public void send(
      StepContext context,
      String recipients,
      String subject,
      String body,
      String attachmentsPattern)
      throws Exception {
    EmailExtStep emailStep = new EmailExtStep(subject, body);
    emailStep.setTo(recipients);
    emailStep.setAttachmentsPattern(attachmentsPattern);
    emailStep.setMimeType("text/plain");
    StepExecution execution = emailStep.start(context);
    invokeRun(execution);
  }

  private void invokeRun(StepExecution execution) throws Exception {
    Method runMethod = findRunMethod(execution.getClass());
    try {
      runMethod.setAccessible(true);
      runMethod.invoke(execution);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }

  private Method findRunMethod(Class<?> type) throws NoSuchMethodException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredMethod("run");
      } catch (NoSuchMethodException ignored) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchMethodException("Unable to invoke Email Extension step execution.");
  }
}
