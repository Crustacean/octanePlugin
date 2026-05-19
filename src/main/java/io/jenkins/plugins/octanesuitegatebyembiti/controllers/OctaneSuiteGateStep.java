package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneSuiteGateConfiguration;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.services.CriteriaException;
import io.jenkins.plugins.octanesuitegatebyembiti.services.CriteriaExpression;
import io.jenkins.plugins.octanesuitegatebyembiti.services.GateFailedException;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneGateRunner;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import jenkins.util.Timer;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class OctaneSuiteGateStep extends Step {
  private final String serverId;
  private final String suiteRunId;
  private String sharedSpaceId = "";
  private String workspaceId = "";
  private String criteria = GateRequest.DEFAULT_CRITERIA;
  private List<OctaneGateScope> scopes = new ArrayList<>();
  private int pollIntervalSeconds = GateRequest.DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutMinutes = GateRequest.DEFAULT_TIMEOUT_MINUTES;
  private boolean markUnstable;
  private boolean riskHeatMap;
  private String riskHeatMapDefectQuery = "";
  private int riskHeatMapMaxDefects = GateRequest.DEFAULT_RISK_HEAT_MAP_MAX_DEFECTS;
  private String passedStatuses = StatusClassifier.DEFAULT_PASSED_STATUSES;
  private String failedStatuses = StatusClassifier.DEFAULT_FAILED_STATUSES;
  private String neutralStatuses = StatusClassifier.DEFAULT_NEUTRAL_STATUSES;
  private String runningStatuses = StatusClassifier.DEFAULT_RUNNING_STATUSES;

  @DataBoundConstructor
  public OctaneSuiteGateStep(String serverId, String suiteRunId) {
    this.serverId = Util.trimToEmpty(serverId);
    this.suiteRunId = Util.trimToEmpty(suiteRunId);
  }

  public String getServerId() {
    return serverId;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public String getSharedSpaceId() {
    return sharedSpaceId;
  }

  @DataBoundSetter
  public void setSharedSpaceId(String sharedSpaceId) {
    this.sharedSpaceId = Util.trimToEmpty(sharedSpaceId);
  }

  public String getWorkspaceId() {
    return workspaceId;
  }

  @DataBoundSetter
  public void setWorkspaceId(String workspaceId) {
    this.workspaceId = Util.trimToEmpty(workspaceId);
  }

  public String getCriteria() {
    return criteria;
  }

  @DataBoundSetter
  public void setCriteria(String criteria) {
    String trimmed = Util.trimToEmpty(criteria);
    this.criteria = trimmed.isEmpty() ? GateRequest.DEFAULT_CRITERIA : trimmed;
  }

  public List<OctaneGateScope> getScopes() {
    return Collections.unmodifiableList(scopes);
  }

  @DataBoundSetter
  public void setScopes(List<OctaneGateScope> scopes) {
    this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
  }

  public int getPollIntervalSeconds() {
    return pollIntervalSeconds;
  }

  @DataBoundSetter
  public void setPollIntervalSeconds(int pollIntervalSeconds) {
    this.pollIntervalSeconds = Math.max(1, pollIntervalSeconds);
  }

  public int getTimeoutMinutes() {
    return timeoutMinutes;
  }

  @DataBoundSetter
  public void setTimeoutMinutes(int timeoutMinutes) {
    this.timeoutMinutes = Math.max(1, timeoutMinutes);
  }

  public boolean isMarkUnstable() {
    return markUnstable;
  }

  @DataBoundSetter
  public void setMarkUnstable(boolean markUnstable) {
    this.markUnstable = markUnstable;
  }

  public boolean isRiskHeatMap() {
    return riskHeatMap;
  }

  @DataBoundSetter
  public void setRiskHeatMap(boolean riskHeatMap) {
    this.riskHeatMap = riskHeatMap;
  }

  public String getRiskHeatMapDefectQuery() {
    return riskHeatMapDefectQuery;
  }

  @DataBoundSetter
  public void setRiskHeatMapDefectQuery(String riskHeatMapDefectQuery) {
    this.riskHeatMapDefectQuery = Util.trimToEmpty(riskHeatMapDefectQuery);
  }

  public int getRiskHeatMapMaxDefects() {
    return riskHeatMapMaxDefects;
  }

  @DataBoundSetter
  public void setRiskHeatMapMaxDefects(int riskHeatMapMaxDefects) {
    this.riskHeatMapMaxDefects = Math.max(1, riskHeatMapMaxDefects);
  }

  public String getPassedStatuses() {
    return passedStatuses;
  }

  @DataBoundSetter
  public void setPassedStatuses(String passedStatuses) {
    this.passedStatuses = defaultIfBlank(passedStatuses, StatusClassifier.DEFAULT_PASSED_STATUSES);
  }

  public String getFailedStatuses() {
    return failedStatuses;
  }

  @DataBoundSetter
  public void setFailedStatuses(String failedStatuses) {
    this.failedStatuses = defaultIfBlank(failedStatuses, StatusClassifier.DEFAULT_FAILED_STATUSES);
  }

  public String getNeutralStatuses() {
    return neutralStatuses;
  }

  @DataBoundSetter
  public void setNeutralStatuses(String neutralStatuses) {
    this.neutralStatuses =
        defaultIfBlank(neutralStatuses, StatusClassifier.DEFAULT_NEUTRAL_STATUSES);
  }

  public String getRunningStatuses() {
    return runningStatuses;
  }

  @DataBoundSetter
  public void setRunningStatuses(String runningStatuses) {
    this.runningStatuses =
        defaultIfBlank(runningStatuses, StatusClassifier.DEFAULT_RUNNING_STATUSES);
  }

  @Override
  public StepExecution start(StepContext context) {
    return new Execution(toRequest(), context);
  }

  GateRequest toRequest() {
    GateRequest request = new GateRequest(serverId, suiteRunId);
    request.setSharedSpaceId(sharedSpaceId);
    request.setWorkspaceId(workspaceId);
    request.setCriteria(criteria);
    request.setScopes(scopes);
    request.setPollIntervalSeconds(pollIntervalSeconds);
    request.setTimeoutMinutes(timeoutMinutes);
    request.setMarkUnstable(markUnstable);
    request.setRiskHeatMap(riskHeatMap);
    request.setRiskHeatMapDefectQuery(riskHeatMapDefectQuery);
    request.setRiskHeatMapMaxDefects(riskHeatMapMaxDefects);
    request.setPassedStatuses(passedStatuses);
    request.setFailedStatuses(failedStatuses);
    request.setNeutralStatuses(neutralStatuses);
    request.setRunningStatuses(runningStatuses);
    return request;
  }

  private String defaultIfBlank(String value, String defaultValue) {
    String trimmed = Util.trimToEmpty(value);
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  private static class Execution extends StepExecution {
    private static final long serialVersionUID = 1L;

    private final GateRequest request;
    private transient Future<?> future;

    Execution(GateRequest request, StepContext context) {
      super(context);
      this.request = request;
    }

    @Override
    public boolean start() {
      schedule();
      return false;
    }

    @Override
    public void onResume() {
      schedule();
    }

    @Override
    public void stop(@NonNull Throwable cause) throws Exception {
      if (future != null) {
        future.cancel(true);
      }
      getContext().onFailure(cause);
    }

    private void schedule() {
      future = Timer.get().submit(this::run);
    }

    private void run() {
      OctaneGateReportAction reportAction = null;
      try {
        StepContext context = getContext();
        Run<?, ?> run = context.get(Run.class);
        TaskListener listener = context.get(TaskListener.class);
        reportAction = OctaneGateReportAction.attachTo(run, request);
        new OctaneGateLogListener().logReportLink(listener, reportAction.getReportUrl());
        GateResult result = new OctaneGateRunner().run(request, listener, reportAction);
        context.onSuccess(result.toPipelineMap());
      } catch (GateFailedException e) {
        handleGateFailure(e);
      } catch (Throwable t) {
        if (reportAction != null) {
          reportAction.onError(t.getMessage(), request);
        }
        getContext().onFailure(t);
      }
    }

    private void handleGateFailure(GateFailedException exception) {
      try {
        if (request.isMarkUnstable()) {
          Run<?, ?> run = getContext().get(Run.class);
          TaskListener listener = getContext().get(TaskListener.class);
          run.setResult(Result.UNSTABLE);
          listener.getLogger().println(exception.getMessage());
          getContext().onSuccess(exception.getResult().toPipelineMap());
        } else {
          getContext().onFailure(exception);
        }
      } catch (IOException | InterruptedException e) {
        getContext().onFailure(e);
      }
    }
  }

  @Extension
  @Symbol("octaneSuiteGate")
  public static class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return "octaneSuiteGate";
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "ALM Octane Suite Gate";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Set.of(Run.class, TaskListener.class);
    }

    public ListBoxModel doFillServerIdItems() {
      OctaneSuiteGateConfiguration configuration = OctaneSuiteGateConfiguration.get();
      return configuration == null ? new ListBoxModel() : configuration.doFillServerIdItems();
    }

    public FormValidation doCheckServerId(@QueryParameter String value) {
      if (Util.isBlank(value)) {
        return FormValidation.error("Server ID is required.");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckSuiteRunId(@QueryParameter String value) {
      if (Util.splitIdList(value).isEmpty()) {
        return FormValidation.error("At least one suite run ID is required.");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckSharedSpaceId(@QueryParameter String value) {
      return checkRequiredNumber("Shared space ID", value);
    }

    public FormValidation doCheckWorkspaceId(@QueryParameter String value) {
      return checkRequiredNumber("Workspace ID", value);
    }

    public FormValidation doCheckCriteria(@QueryParameter String value) {
      try {
        CriteriaExpression.parse(Util.isBlank(value) ? GateRequest.DEFAULT_CRITERIA : value);
        return FormValidation.ok();
      } catch (CriteriaException e) {
        return FormValidation.error(e.getMessage());
      }
    }

    public FormValidation doCheckPollIntervalSeconds(@QueryParameter String value) {
      return checkPositiveInteger("Poll interval", value);
    }

    public FormValidation doCheckTimeoutMinutes(@QueryParameter String value) {
      return checkPositiveInteger("Timeout", value);
    }

    public FormValidation doCheckRiskHeatMapMaxDefects(@QueryParameter String value) {
      return checkPositiveInteger("Risk heat map max defects", value);
    }

    private FormValidation checkRequiredNumber(String label, String value) {
      if (Util.isBlank(value)) {
        return FormValidation.error(label + " is required.");
      }
      try {
        Long.parseLong(value);
        return FormValidation.ok();
      } catch (NumberFormatException e) {
        return FormValidation.error(label + " must be numeric.");
      }
    }

    private FormValidation checkPositiveInteger(String label, String value) {
      try {
        if (Integer.parseInt(value) <= 0) {
          return FormValidation.error(label + " must be greater than zero.");
        }
        return FormValidation.ok();
      } catch (NumberFormatException e) {
        return FormValidation.error(label + " must be a number.");
      }
    }
  }
}
