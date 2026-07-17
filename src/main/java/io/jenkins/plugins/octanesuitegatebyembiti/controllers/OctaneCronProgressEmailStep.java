package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneEmailFailureMode;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneProgressEmailScheduler;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class OctaneCronProgressEmailStep extends Step {
  private final String cron;
  private final String to;
  private String cc = "";
  private String bcc = "";
  private String subject = "";
  private String body = "";
  private String projectName = "";
  private String domainName = "";
  private String from = "";
  private String replyTo = "";
  private String onFailure = OctaneEmailFailureMode.WARN.name();
  private String browserPath = "";
  private String theme = OctaneReportTheme.LIGHT.name();
  private int viewportWidth = OctaneEmailReportStep.DEFAULT_VIEWPORT_WIDTH;
  private boolean archiveScreenshot;
  private boolean printDefectGroups;

  @DataBoundConstructor
  public OctaneCronProgressEmailStep(String cron, String to) {
    this.cron = Util.trimToEmpty(cron);
    this.to = Util.trimToEmpty(to);
  }

  public String getCron() {
    return cron;
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

  public boolean isPrintDefectGroups() {
    return printDefectGroups;
  }

  @DataBoundSetter
  public void setPrintDefectGroups(boolean printDefectGroups) {
    this.printDefectGroups = printDefectGroups;
  }

  @Override
  public StepExecution start(StepContext context) {
    return new Execution(cron, emailRequest(), context);
  }

  private OctaneEmailReportStep.EmailRequest emailRequest() {
    OctaneEmailReportStep email = new OctaneEmailReportStep(to);
    email.setCc(cc);
    email.setBcc(bcc);
    email.setSubject(subject);
    email.setBody(body);
    email.setProjectName(projectName);
    email.setDomainName(domainName);
    email.setFrom(from);
    email.setReplyTo(replyTo);
    email.setOnFailure(onFailure);
    email.setBrowserPath(browserPath);
    email.setTheme(theme);
    email.setViewportWidth(viewportWidth);
    email.setArchiveScreenshot(archiveScreenshot);
    email.setPrintDefectGroups(printDefectGroups);
    return email.toRequest();
  }

  private static final class Execution extends StepExecution
      implements OctaneProgressEmailScheduler.Delivery {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter AUDIT_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String cron;
    private final OctaneEmailReportStep.EmailRequest emailRequest;
    private final String registrationId = UUID.randomUUID().toString();
    private transient volatile OctaneProgressEmailScheduler.Registration registration;
    private transient volatile BodyExecution bodyExecution;
    private volatile boolean completed;

    private Execution(
        String cron, OctaneEmailReportStep.EmailRequest emailRequest, StepContext context) {
      super(context);
      this.cron = cron;
      this.emailRequest = emailRequest;
    }

    @Override
    public boolean start() throws Exception {
      registerIfEnabled();
      try {
        bodyExecution =
            getContext()
                .newBodyInvoker()
                .withDisplayName("Octane gate with cron progress email")
                .withCallback(new Callback(this))
                .start();
      } catch (RuntimeException | Error failure) {
        cancelRegistration();
        throw failure;
      }
      return false;
    }

    @Override
    public void onResume() {
      if (completed || cron.isBlank()) {
        return;
      }
      try {
        register();
      } catch (Exception e) {
        completed = true;
        getContext().onFailure(e);
      }
    }

    @Override
    public void stop(Throwable cause) {
      completed = true;
      cancelRegistration();
      BodyExecution body = bodyExecution;
      if (body != null) {
        body.cancel(cause);
      }
    }

    @Override
    public void send(OctaneProgressEmailScheduler.Occurrence occurrence) throws Exception {
      if (completed) {
        return;
      }
      TaskListener listener = getContext().get(TaskListener.class);
      listener
          .getLogger()
          .println(
              "Cron job time: "
                  + occurrence.expression()
                  + " will run \""
                  + occurrence.description()
                  + "\"");
      listener
          .getLogger()
          .println("next at " + AUDIT_TIME_FORMATTER.format(occurrence.scheduledAt()));
      OctaneEmailReportStep.executeRequest(emailRequest, getContext());
    }

    @Override
    public void skipped(OctaneProgressEmailScheduler.Occurrence occurrence, Duration lateness) {
      log(
          "WARNING: Skipped delayed Octane progress email for cron "
              + occurrence.expression()
              + " because scheduler capacity delayed it by "
              + lateness.toSeconds()
              + " seconds.");
    }

    @Override
    public void failed(OctaneProgressEmailScheduler.Occurrence occurrence, Throwable failure) {
      log(
          "WARNING: Scheduled Octane progress email for cron "
              + occurrence.expression()
              + " failed: "
              + defaultMessage(failure));
    }

    private void registerIfEnabled() throws Exception {
      if (cron.isBlank()) {
        TaskListener listener = getContext().get(TaskListener.class);
        listener
            .getLogger()
            .println(
                "Periodic Octane progress emails are disabled because "
                    + "PROGRESS_EMAIL_INTERVAL_CRONJOB is blank.");
        return;
      }
      register();
    }

    private void register() throws Exception {
      Run<?, ?> run = getContext().get(Run.class);
      try {
        registration =
            OctaneProgressEmailScheduler.get()
                .schedule(registrationId, run.getExternalizableId(), cron, this);
      } catch (IllegalArgumentException | RejectedExecutionException e) {
        AbortException failure =
            new AbortException("Unable to schedule Octane progress emails: " + e.getMessage());
        failure.initCause(e);
        throw failure;
      }
    }

    private void completeSuccess(Object result) {
      completed = true;
      cancelRegistration();
      getContext().onSuccess(result);
    }

    private void completeFailure(Throwable failure) {
      completed = true;
      cancelRegistration();
      getContext().onFailure(failure);
    }

    private void cancelRegistration() {
      OctaneProgressEmailScheduler.Registration current = registration;
      registration = null;
      if (current != null) {
        current.cancel();
      }
    }

    private void log(String message) {
      try {
        TaskListener listener = getContext().get(TaskListener.class);
        if (listener != null) {
          listener.getLogger().println(message);
        }
      } catch (Exception ignored) {
        // The Pipeline context may already be gone after a hard controller shutdown.
      }
    }

    private String defaultMessage(Throwable failure) {
      String message = failure.getMessage();
      return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
  }

  private static final class Callback extends BodyExecutionCallback implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Execution execution;

    private Callback(Execution execution) {
      this.execution = execution;
    }

    @Override
    public void onSuccess(StepContext context, Object result) {
      execution.completeSuccess(result);
    }

    @Override
    public void onFailure(StepContext context, Throwable failure) {
      execution.completeFailure(failure);
    }
  }

  @Extension
  @Symbol("octaneCronProgressEmail")
  public static final class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return "octaneCronProgressEmail";
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "Schedule cron-based Octane progress emails";
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Set.of(Run.class, FilePath.class, Launcher.class, TaskListener.class, EnvVars.class);
    }
  }
}
