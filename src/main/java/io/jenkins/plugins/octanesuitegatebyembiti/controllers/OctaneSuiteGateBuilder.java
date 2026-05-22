package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneSuiteGateConfiguration;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.services.GateFailedException;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneGateRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class OctaneSuiteGateBuilder extends Builder implements SimpleBuildStep {
  private final OctaneSuiteGateStep delegate;

  @DataBoundConstructor
  public OctaneSuiteGateBuilder(String serverId, String suiteRunId) {
    delegate = new OctaneSuiteGateStep(serverId, suiteRunId);
  }

  public String getServerId() {
    return delegate.getServerId();
  }

  public String getSuiteRunId() {
    return delegate.getSuiteRunId();
  }

  public String getSharedSpaceId() {
    return delegate.getSharedSpaceId();
  }

  @DataBoundSetter
  public void setSharedSpaceId(String sharedSpaceId) {
    delegate.setSharedSpaceId(sharedSpaceId);
  }

  public String getWorkspaceId() {
    return delegate.getWorkspaceId();
  }

  @DataBoundSetter
  public void setWorkspaceId(String workspaceId) {
    delegate.setWorkspaceId(workspaceId);
  }

  public String getCriteria() {
    return delegate.getCriteria();
  }

  @DataBoundSetter
  public void setCriteria(String criteria) {
    delegate.setCriteria(criteria);
  }

  public List<OctaneGateScope> getScopes() {
    return Collections.unmodifiableList(new ArrayList<>(delegate.getScopes()));
  }

  @DataBoundSetter
  public void setScopes(List<OctaneGateScope> scopes) {
    delegate.setScopes(scopes);
  }

  public int getPollIntervalSeconds() {
    return delegate.getPollIntervalSeconds();
  }

  @DataBoundSetter
  public void setPollIntervalSeconds(int pollIntervalSeconds) {
    delegate.setPollIntervalSeconds(pollIntervalSeconds);
  }

  public int getTimeoutMinutes() {
    return delegate.getTimeoutMinutes();
  }

  @DataBoundSetter
  public void setTimeoutMinutes(int timeoutMinutes) {
    delegate.setTimeoutMinutes(timeoutMinutes);
  }

  public boolean isMarkUnstable() {
    return delegate.isMarkUnstable();
  }

  @DataBoundSetter
  public void setMarkUnstable(boolean markUnstable) {
    delegate.setMarkUnstable(markUnstable);
  }

  public boolean isRiskHeatMap() {
    return delegate.isRiskHeatMap();
  }

  @DataBoundSetter
  public void setRiskHeatMap(boolean riskHeatMap) {
    delegate.setRiskHeatMap(riskHeatMap);
  }

  public String getRiskHeatMapDefectQuery() {
    return delegate.getRiskHeatMapDefectQuery();
  }

  @DataBoundSetter
  public void setRiskHeatMapDefectQuery(String riskHeatMapDefectQuery) {
    delegate.setRiskHeatMapDefectQuery(riskHeatMapDefectQuery);
  }

  public int getRiskHeatMapMaxDefects() {
    return delegate.getRiskHeatMapMaxDefects();
  }

  @DataBoundSetter
  public void setRiskHeatMapMaxDefects(int riskHeatMapMaxDefects) {
    delegate.setRiskHeatMapMaxDefects(riskHeatMapMaxDefects);
  }

  public String getPassedStatuses() {
    return delegate.getPassedStatuses();
  }

  @DataBoundSetter
  public void setPassedStatuses(String passedStatuses) {
    delegate.setPassedStatuses(passedStatuses);
  }

  public String getFailedStatuses() {
    return delegate.getFailedStatuses();
  }

  @DataBoundSetter
  public void setFailedStatuses(String failedStatuses) {
    delegate.setFailedStatuses(failedStatuses);
  }

  public String getNeutralStatuses() {
    return delegate.getNeutralStatuses();
  }

  @DataBoundSetter
  public void setNeutralStatuses(String neutralStatuses) {
    delegate.setNeutralStatuses(neutralStatuses);
  }

  public String getRunningStatuses() {
    return delegate.getRunningStatuses();
  }

  @DataBoundSetter
  public void setRunningStatuses(String runningStatuses) {
    delegate.setRunningStatuses(runningStatuses);
  }

  @Override
  public void perform(
      Run<?, ?> run,
      FilePath workspace,
      EnvVars environment,
      Launcher launcher,
      TaskListener listener)
      throws InterruptedException, IOException {
    GateRequest request = delegate.toRequest();
    OctaneGateReportAction reportAction = OctaneGateReportAction.attachTo(run, request);
    new OctaneGateLogListener().logReportLink(listener, reportAction.getReportUrl());
    try {
      new OctaneGateRunner().run(request, listener, reportAction);
    } catch (GateFailedException e) {
      if (delegate.isMarkUnstable()) {
        run.setResult(Result.UNSTABLE);
        listener.getLogger().println(e.getMessage());
      } else {
        throw e;
      }
    } catch (IOException | InterruptedException e) {
      reportAction.onError(e.getMessage(), request);
      throw e;
    } catch (RuntimeException e) {
      reportAction.onError(e.getMessage(), request);
      throw e;
    }
  }

  @Extension
  @Symbol("octaneSuiteGateBuilder")
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
    @Override
    @SuppressWarnings("rawtypes")
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "ALM Octane Suite Gate";
    }

    public ListBoxModel doFillServerIdItems() {
      OctaneSuiteGateConfiguration configuration = OctaneSuiteGateConfiguration.get();
      return configuration == null ? new ListBoxModel() : configuration.doFillServerIdItems();
    }

    public FormValidation doCheckServerId(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckServerId(value);
    }

    public FormValidation doCheckSuiteRunId(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckSuiteRunId(value);
    }

    public FormValidation doCheckSharedSpaceId(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckSharedSpaceId(value);
    }

    public FormValidation doCheckWorkspaceId(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckWorkspaceId(value);
    }

    public FormValidation doCheckCriteria(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckCriteria(value);
    }

    public FormValidation doCheckPollIntervalSeconds(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckPollIntervalSeconds(value);
    }

    public FormValidation doCheckTimeoutMinutes(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckTimeoutMinutes(value);
    }

    public FormValidation doCheckRiskHeatMapMaxDefects(@QueryParameter String value) {
      return new OctaneSuiteGateStep.DescriptorImpl().doCheckRiskHeatMapMaxDefects(value);
    }
  }
}
