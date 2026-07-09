package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.ArtifactArchiver;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneEmailFailureMode;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import io.jenkins.plugins.octanesuitegatebyembiti.services.EmailExtensionOctaneReportSender;
import io.jenkins.plugins.octanesuitegatebyembiti.services.HeadlessBrowserReportScreenshotService;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneEmailBodyRenderer;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneEmailReportSender;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportScreenshot;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportScreenshotService;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

@SuppressFBWarnings(
    value = "MS_SHOULD_BE_FINAL",
    justification = "Tests replace these collaborators without invoking real browsers or SMTP.")
public class OctaneEmailReportStep extends Step {
  static final int DEFAULT_VIEWPORT_WIDTH = 1400;

  private static OctaneReportScreenshotService screenshotService =
      new HeadlessBrowserReportScreenshotService();
  private static OctaneEmailReportSender emailSender = new EmailExtensionOctaneReportSender();

  private final String to;
  private String cc = "";
  private String bcc = "";
  private String subject = "";
  private String body = "";
  private String projectName = "";
  private String domainName = "";
  private String from = "";
  private String replyTo = "";
  private String onFailure = OctaneEmailFailureMode.UNSTABLE.name();
  private String browserPath = "";
  private String theme = OctaneReportTheme.LIGHT.name();
  private int viewportWidth = DEFAULT_VIEWPORT_WIDTH;
  private boolean archiveScreenshot = true;

  @DataBoundConstructor
  public OctaneEmailReportStep(String to) {
    this.to = Util.trimToEmpty(to);
  }

  public String getTo() {
    return to;
  }

  public String getCc() {
    return cc;
  }

  @DataBoundSetter
  public void setCc(String cc) {
    this.cc = Util.trimToEmpty(cc);
  }

  public String getBcc() {
    return bcc;
  }

  @DataBoundSetter
  public void setBcc(String bcc) {
    this.bcc = Util.trimToEmpty(bcc);
  }

  public String getSubject() {
    return subject;
  }

  @DataBoundSetter
  public void setSubject(String subject) {
    this.subject = Util.trimToEmpty(subject);
  }

  public String getBody() {
    return body;
  }

  @DataBoundSetter
  public void setBody(String body) {
    this.body = Util.trimToEmpty(body);
  }

  public String getProjectName() {
    return projectName;
  }

  @DataBoundSetter
  public void setProjectName(String projectName) {
    this.projectName = Util.trimToEmpty(projectName);
  }

  public String getDomainName() {
    return domainName;
  }

  @DataBoundSetter
  public void setDomainName(String domainName) {
    this.domainName = Util.trimToEmpty(domainName);
  }

  public String getFrom() {
    return from;
  }

  @DataBoundSetter
  public void setFrom(String from) {
    this.from = Util.trimToEmpty(from);
  }

  public String getReplyTo() {
    return replyTo;
  }

  @DataBoundSetter
  public void setReplyTo(String replyTo) {
    this.replyTo = Util.trimToEmpty(replyTo);
  }

  public String getOnFailure() {
    return onFailure;
  }

  @DataBoundSetter
  public void setOnFailure(String onFailure) {
    this.onFailure = OctaneEmailFailureMode.normalize(onFailure);
  }

  public String getBrowserPath() {
    return browserPath;
  }

  @DataBoundSetter
  public void setBrowserPath(String browserPath) {
    this.browserPath = Util.trimToEmpty(browserPath);
  }

  public String getTheme() {
    return theme;
  }

  @DataBoundSetter
  public void setTheme(String theme) {
    this.theme = OctaneReportTheme.normalize(theme);
  }

  public int getViewportWidth() {
    return viewportWidth;
  }

  @DataBoundSetter
  public void setViewportWidth(int viewportWidth) {
    this.viewportWidth = Math.max(320, viewportWidth);
  }

  public boolean isArchiveScreenshot() {
    return archiveScreenshot;
  }

  @DataBoundSetter
  public void setArchiveScreenshot(boolean archiveScreenshot) {
    this.archiveScreenshot = archiveScreenshot;
  }

  @Override
  public StepExecution start(StepContext context) {
    return new Execution(toRequest(), context);
  }

  static void setServicesForTesting(
      OctaneReportScreenshotService testScreenshotService,
      OctaneEmailReportSender testEmailSender) {
    screenshotService = testScreenshotService;
    emailSender = testEmailSender;
  }

  static void resetServicesForTesting() {
    screenshotService = new HeadlessBrowserReportScreenshotService();
    emailSender = new EmailExtensionOctaneReportSender();
  }

  static String composeRecipients(String to, String cc, String bcc) {
    List<String> recipients = new ArrayList<>();
    addRecipients(recipients, "", to);
    addRecipients(recipients, "cc", cc);
    addRecipients(recipients, "bcc", bcc);
    return String.join(",", recipients);
  }

  private static void addRecipients(List<String> recipients, String prefix, String value) {
    String trimmed = Util.trimToEmpty(value);
    if (trimmed.isEmpty()) {
      return;
    }
    for (String recipient : trimmed.split("[,;\\s]+")) {
      String cleanRecipient = Util.trimToEmpty(recipient);
      if (cleanRecipient.isEmpty()) {
        continue;
      }
      recipients.add(prefix.isEmpty() ? cleanRecipient : prefix + ":" + cleanRecipient);
    }
  }

  private EmailRequest toRequest() {
    return new EmailRequest(
        to,
        cc,
        bcc,
        subject,
        body,
        projectName,
        domainName,
        from,
        replyTo,
        onFailure,
        browserPath,
        theme,
        viewportWidth,
        archiveScreenshot);
  }

  private static class EmailRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String to;
    private final String cc;
    private final String bcc;
    private final String subject;
    private final String body;
    private final String projectName;
    private final String domainName;
    private final String from;
    private final String replyTo;
    private final String onFailure;
    private final String browserPath;
    private final String theme;
    private final int viewportWidth;
    private final boolean archiveScreenshot;

    EmailRequest(
        String to,
        String cc,
        String bcc,
        String subject,
        String body,
        String projectName,
        String domainName,
        String from,
        String replyTo,
        String onFailure,
        String browserPath,
        String theme,
        int viewportWidth,
        boolean archiveScreenshot) {
      this.to = to;
      this.cc = cc;
      this.bcc = bcc;
      this.subject = subject;
      this.body = body;
      this.projectName = projectName;
      this.domainName = domainName;
      this.from = from;
      this.replyTo = replyTo;
      this.onFailure = onFailure;
      this.browserPath = browserPath;
      this.theme = theme;
      this.viewportWidth = viewportWidth;
      this.archiveScreenshot = archiveScreenshot;
    }
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    private final EmailRequest request;

    Execution(EmailRequest request, StepContext context) {
      super(context);
      this.request = request;
    }

    @Override
    protected Void run() throws Exception {
      OctaneEmailFailureMode failureMode = OctaneEmailFailureMode.from(request.onFailure);
      Run<?, ?> run = getContext().get(Run.class);
      TaskListener listener = getContext().get(TaskListener.class);
      try {
        sendReport(run, listener);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      } catch (Exception e) {
        handleFailure(run, listener, failureMode, e);
      }
      return null;
    }

    private void sendReport(Run<?, ?> run, TaskListener listener) throws Exception {
      FilePath workspace = getContext().get(FilePath.class);
      Launcher launcher = getContext().get(Launcher.class);
      EnvVars envVars = envVars();
      OctaneGateReportAction action = run.getAction(OctaneGateReportAction.class);
      if (action == null) {
        throw new AbortException(
            "Octane Gate Report is not available. Run octaneSuiteGate before octaneEmailReport.");
      }
      String recipients = composeRecipients(request.to, request.cc, request.bcc);
      if (recipients.isBlank()) {
        throw new AbortException("At least one recipient is required.");
      }

      OctaneReportScreenshot screenshot =
          screenshotService.capture(
              action,
              workspace,
              envVars,
              launcher,
              listener,
              request.browserPath,
              request.viewportWidth,
              request.theme);
      if (request.archiveScreenshot) {
        listener.getLogger().println("Archiving Octane report-zone screenshot.");
        archiveScreenshot(run, workspace, envVars, launcher, listener, screenshot);
        listener.getLogger().println("Octane report-zone screenshot archived successfully.");
      }

      String subject = effectiveSubject(run, request.subject);
      String body =
          new OctaneEmailBodyRenderer()
              .render(
                  request.body,
                  effectiveProjectName(run, request.projectName),
                  request.domainName,
                  action.getSnapshot(),
                  action.getReportUrl(),
                  screenshot.getScreenshotFile().getName(),
                  request.theme);
      listener.getLogger().println("Sending Octane report email through Jenkins Email Extension.");
      emailSender.send(
          getContext(),
          recipients,
          request.from,
          request.replyTo,
          subject,
          body,
          screenshot.getAttachmentPattern());
      listener
          .getLogger()
          .println(
              "Jenkins Email Extension completed the SMTP handoff for "
                  + visibleRecipients(recipients)
                  + ". Inbox placement is controlled by the receiving mail service.");
    }

    private EnvVars envVars() throws InterruptedException {
      try {
        EnvVars envVars = getContext().get(EnvVars.class);
        return envVars == null ? new EnvVars() : envVars;
      } catch (java.io.IOException e) {
        return new EnvVars();
      }
    }

    private void archiveScreenshot(
        Run<?, ?> run,
        FilePath workspace,
        EnvVars envVars,
        Launcher launcher,
        TaskListener listener,
        OctaneReportScreenshot screenshot)
        throws java.io.IOException, InterruptedException {
      ArtifactArchiver archiver = new ArtifactArchiver(screenshot.getAttachmentPattern());
      archiver.perform(run, workspace, envVars, launcher, listener);
    }

    private String effectiveSubject(Run<?, ?> run, String configuredSubject) {
      String trimmed = Util.trimToEmpty(configuredSubject);
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
      return "Octane Gate Report - " + run.getParent().getFullName() + " #" + run.getNumber();
    }

    private String effectiveProjectName(Run<?, ?> run, String configuredProjectName) {
      String trimmed = Util.trimToEmpty(configuredProjectName);
      return trimmed.isEmpty() ? run.getParent().getDisplayName() : trimmed;
    }

    private String visibleRecipients(String recipients) {
      return recipients.replaceAll("bcc:[^,]+", "bcc:***");
    }

    private void handleFailure(
        Run<?, ?> run,
        TaskListener listener,
        OctaneEmailFailureMode failureMode,
        Exception exception)
        throws AbortException {
      String message = "Octane email report failed: " + defaultMessage(exception);
      if (failureMode == OctaneEmailFailureMode.WARN) {
        listener.getLogger().println("WARNING: " + message);
        return;
      }
      if (failureMode == OctaneEmailFailureMode.UNSTABLE) {
        run.setResult(Result.UNSTABLE);
        listener.getLogger().println(message + " Marking build UNSTABLE and continuing.");
        return;
      }
      AbortException abortException = new AbortException(message);
      abortException.initCause(exception);
      throw abortException;
    }

    private String defaultMessage(Exception exception) {
      String message = exception.getMessage();
      if (message == null || message.isBlank()) {
        return exception.getClass().getSimpleName();
      }
      return message;
    }
  }

  @Extension
  @Symbol("octaneEmailReport")
  public static class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return "octaneEmailReport";
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "Email Octane Gate Report";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Set.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
    }

    public ListBoxModel doFillOnFailureItems() {
      ListBoxModel model = new ListBoxModel();
      model.add("Mark build unstable and continue", OctaneEmailFailureMode.UNSTABLE.name());
      model.add("Fail the build", OctaneEmailFailureMode.FAILURE.name());
      model.add("Warn and continue", OctaneEmailFailureMode.WARN.name());
      return model;
    }

    public ListBoxModel doFillThemeItems() {
      ListBoxModel model = new ListBoxModel();
      model.add("Light", OctaneReportTheme.LIGHT.name());
      model.add("Dark", OctaneReportTheme.DARK.name());
      model.add("Agent system preference", OctaneReportTheme.SYSTEM.name());
      return model;
    }

    public FormValidation doCheckTo(@QueryParameter String value) {
      return checkOptionalRecipients("To", value);
    }

    public FormValidation doCheckCc(@QueryParameter String value) {
      return checkOptionalRecipients("Cc", value);
    }

    public FormValidation doCheckBcc(@QueryParameter String value) {
      return checkOptionalRecipients("Bcc", value);
    }

    public FormValidation doCheckFrom(@QueryParameter String value) {
      return checkOptionalRecipients("From", value);
    }

    public FormValidation doCheckReplyTo(@QueryParameter String value) {
      return checkOptionalRecipients("Reply-To", value);
    }

    public FormValidation doCheckOnFailure(@QueryParameter String value) {
      try {
        OctaneEmailFailureMode.from(value);
        return FormValidation.ok();
      } catch (IllegalArgumentException e) {
        return FormValidation.error(e.getMessage());
      }
    }

    public FormValidation doCheckTheme(@QueryParameter String value) {
      try {
        OctaneReportTheme.from(value);
        return FormValidation.ok();
      } catch (IllegalArgumentException e) {
        return FormValidation.error(e.getMessage());
      }
    }

    public FormValidation doCheckViewportWidth(@QueryParameter String value) {
      try {
        if (Integer.parseInt(value) < 320) {
          return FormValidation.error("Viewport width must be at least 320.");
        }
        return FormValidation.ok();
      } catch (NumberFormatException e) {
        return FormValidation.error("Viewport width must be a number.");
      }
    }

    private FormValidation checkOptionalRecipients(String label, String value) {
      if (value != null && value.contains("\n")) {
        return FormValidation.error(
            label + " recipients must be comma, semicolon, or space separated.");
      }
      return FormValidation.ok();
    }
  }
}
