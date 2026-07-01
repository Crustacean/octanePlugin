package io.jenkins.plugins.octanesuitegatebyembiti.services;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.AbortException;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.emailext.EmailExtStep;
import hudson.plugins.emailext.ExtendedEmailPublisher;
import hudson.plugins.emailext.ExtendedEmailPublisherDescriptor;
import hudson.util.StreamTaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public class EmailExtensionOctaneReportSender implements OctaneEmailReportSender {
  private static final List<String> FAILURE_MARKERS =
      List.of(
          "Failed after second try sending email",
          "Could not create session",
          "Could not send email. Throttling limit exceeded.",
          "Not sent to the following valid addresses:",
          "Could not be sent to the following addresses:",
          "An attempt to send an e-mail to empty list of recipients, ignored.",
          "Email sending was cancelled by user script.",
          "Could not send email as a part of the post-build publishers.");

  @Override
  public void send(
      StepContext context,
      String recipients,
      String from,
      String replyTo,
      String subject,
      String body,
      String attachmentsPattern)
      throws Exception {
    ExtendedEmailPublisherDescriptor descriptor = ExtendedEmailPublisher.descriptor();
    String effectiveFrom =
        resolveSender(from, descriptor.getSmtpUsername(), descriptor.getAdminAddress());
    EmailExtStep emailStep = new EmailExtStep(subject, body);
    emailStep.setTo(recipients);
    emailStep.setFrom(effectiveFrom);
    if (!Util.trimToEmpty(replyTo).isEmpty()) {
      emailStep.setReplyTo(replyTo);
    }
    emailStep.setAttachmentsPattern(attachmentsPattern);
    emailStep.setMimeType("text/html");
    EmailOutputCapture outputCapture = EmailOutputCapture.create(context);
    logConfiguration(outputCapture.getContext(), descriptor, effectiveFrom);
    StepExecution execution = emailStep.start(outputCapture.getContext());
    invokeRun(execution);
    verifySendOutput(outputCapture.getOutput());
  }

  private void logConfiguration(
      StepContext context, ExtendedEmailPublisherDescriptor descriptor, String effectiveFrom)
      throws IOException, InterruptedException {
    TaskListener listener = context.get(TaskListener.class);
    String host = defaultValue(descriptor.getSmtpServer(), "localhost/default");
    String port = defaultValue(descriptor.getSmtpPort(), descriptor.getUseSsl() ? "465" : "25");
    listener
        .getLogger()
        .println(
            "Email Extension SMTP configuration: host="
                + host
                + ", port="
                + port
                + ", SSL="
                + descriptor.getUseSsl()
                + ", from="
                + effectiveFrom
                + ".");
  }

  static String resolveSender(String configuredFrom, String smtpUsername, String defaultFrom)
      throws AbortException {
    String explicitSender = Util.trimToEmpty(configuredFrom);
    if (!explicitSender.isEmpty()) {
      if (!isUsableSender(explicitSender)) {
        throw new AbortException(
            "octaneEmailReport 'from' is not a valid sender address: " + explicitSender);
      }
      return explicitSender;
    }

    String authenticatedSender = Util.trimToEmpty(smtpUsername);
    if (isUsableSender(authenticatedSender)) {
      return authenticatedSender;
    }

    String jenkinsDefaultSender = Util.trimToEmpty(defaultFrom);
    if (isUsableSender(jenkinsDefaultSender)) {
      return jenkinsDefaultSender;
    }

    throw new AbortException(
        "No valid email sender is configured. Set 'from' on octaneEmailReport or configure the "
            + "SMTP username/default From address under Extended E-mail Notification. Jenkins' "
            + "placeholder nobody@nowhere cannot be used for delivery.");
  }

  private static boolean isUsableSender(String candidate) {
    if (Util.trimToEmpty(candidate).isEmpty()) {
      return false;
    }
    try {
      InternetAddress address = new InternetAddress(candidate, true);
      address.validate();
      String mailbox = Util.trimToEmpty(address.getAddress());
      return mailbox.contains("@") && !mailbox.equalsIgnoreCase("nobody@nowhere");
    } catch (AddressException e) {
      return false;
    }
  }

  private String defaultValue(String value, String fallback) {
    String normalized = Util.trimToEmpty(value);
    return normalized.isEmpty() ? fallback : normalized;
  }

  static void verifySendOutput(String output) throws AbortException {
    for (String marker : FAILURE_MARKERS) {
      if (output.contains(marker)) {
        throw new AbortException(
            "Jenkins Email Extension did not send the message: "
                + marker
                + " Check the Extended E-mail Notification SMTP settings and Jenkins system log.");
      }
    }
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

  private static final class EmailOutputCapture {
    private final StepContext context;
    private final ByteArrayOutputStream output;
    private final Charset charset;

    private EmailOutputCapture(StepContext context, ByteArrayOutputStream output, Charset charset) {
      this.context = context;
      this.output = output;
      this.charset = charset;
    }

    static EmailOutputCapture create(StepContext context) throws IOException, InterruptedException {
      TaskListener originalListener = context.get(TaskListener.class);
      Charset charset = originalListener.getCharset();
      ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
      OutputStream teeOutput = new TeeOutputStream(originalListener.getLogger(), capturedOutput);
      TaskListener trackingListener = new StreamTaskListener(teeOutput, charset);
      return new EmailOutputCapture(
          new TaskListenerStepContext(context, trackingListener), capturedOutput, charset);
    }

    StepContext getContext() {
      return context;
    }

    String getOutput() {
      return output.toString(charset);
    }
  }

  private static final class TeeOutputStream extends OutputStream {
    private final OutputStream first;
    private final OutputStream second;

    private TeeOutputStream(OutputStream first, OutputStream second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public void write(int value) throws IOException {
      first.write(value);
      second.write(value);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
      first.write(buffer, offset, length);
      second.write(buffer, offset, length);
    }

    @Override
    public void flush() throws IOException {
      first.flush();
      second.flush();
    }
  }

  private static final class TaskListenerStepContext extends StepContext {
    private static final long serialVersionUID = 1L;

    private final StepContext delegate;
    private final TaskListener listener;

    private TaskListenerStepContext(StepContext delegate, TaskListener listener) {
      this.delegate = delegate;
      this.listener = listener;
    }

    @Override
    public <T> T get(Class<T> key) throws IOException, InterruptedException {
      if (key == TaskListener.class) {
        return key.cast(listener);
      }
      return delegate.get(key);
    }

    @Override
    public void onSuccess(Object result) {
      delegate.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable cause) {
      delegate.onFailure(cause);
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public ListenableFuture<Void> saveState() {
      return delegate.saveState();
    }

    @Override
    public void setResult(Result result) {
      delegate.setResult(result);
    }

    @Override
    public BodyInvoker newBodyInvoker() throws IllegalStateException {
      return delegate.newBodyInvoker();
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
