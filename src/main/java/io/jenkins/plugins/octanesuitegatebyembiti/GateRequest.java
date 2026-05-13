package io.jenkins.plugins.octanesuitegatebyembiti;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GateRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  static final int DEFAULT_POLL_INTERVAL_SECONDS = 30;
  static final int DEFAULT_TIMEOUT_MINUTES = 120;
  static final String DEFAULT_CRITERIA = "100% execution AND 100% pass";

  private final String serverId;
  private final String suiteRunId;
  private String sharedSpaceId = "";
  private String workspaceId = "";
  private String criteria = DEFAULT_CRITERIA;
  private List<OctaneGateScope> scopes = new ArrayList<>();
  private int pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
  private boolean markUnstable;
  private String passedStatuses = StatusClassifier.DEFAULT_PASSED_STATUSES;
  private String failedStatuses = StatusClassifier.DEFAULT_FAILED_STATUSES;
  private String neutralStatuses = StatusClassifier.DEFAULT_NEUTRAL_STATUSES;
  private String runningStatuses = StatusClassifier.DEFAULT_RUNNING_STATUSES;

  public GateRequest(String serverId, String suiteRunId) {
    this.serverId = Util.trimToEmpty(serverId);
    this.suiteRunId = Util.trimToEmpty(suiteRunId);
  }

  public String getServerId() {
    return serverId;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public List<String> getSuiteRunIds() {
    return Util.splitIdList(suiteRunId);
  }

  public String getSharedSpaceId() {
    return sharedSpaceId;
  }

  public void setSharedSpaceId(String sharedSpaceId) {
    this.sharedSpaceId = Util.trimToEmpty(sharedSpaceId);
  }

  public String getWorkspaceId() {
    return workspaceId;
  }

  public void setWorkspaceId(String workspaceId) {
    this.workspaceId = Util.trimToEmpty(workspaceId);
  }

  public String getCriteria() {
    return criteria;
  }

  public void setCriteria(String criteria) {
    String trimmed = Util.trimToEmpty(criteria);
    this.criteria = trimmed.isEmpty() ? DEFAULT_CRITERIA : trimmed;
  }

  public List<OctaneGateScope> getScopes() {
    return Collections.unmodifiableList(scopes);
  }

  public void setScopes(List<OctaneGateScope> scopes) {
    this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
  }

  public int getPollIntervalSeconds() {
    return pollIntervalSeconds;
  }

  public void setPollIntervalSeconds(int pollIntervalSeconds) {
    this.pollIntervalSeconds = Math.max(1, pollIntervalSeconds);
  }

  public int getTimeoutMinutes() {
    return timeoutMinutes;
  }

  public void setTimeoutMinutes(int timeoutMinutes) {
    this.timeoutMinutes = Math.max(1, timeoutMinutes);
  }

  public boolean isMarkUnstable() {
    return markUnstable;
  }

  public void setMarkUnstable(boolean markUnstable) {
    this.markUnstable = markUnstable;
  }

  public String getPassedStatuses() {
    return passedStatuses;
  }

  public void setPassedStatuses(String passedStatuses) {
    this.passedStatuses = defaultIfBlank(passedStatuses, StatusClassifier.DEFAULT_PASSED_STATUSES);
  }

  public String getFailedStatuses() {
    return failedStatuses;
  }

  public void setFailedStatuses(String failedStatuses) {
    this.failedStatuses = defaultIfBlank(failedStatuses, StatusClassifier.DEFAULT_FAILED_STATUSES);
  }

  public String getNeutralStatuses() {
    return neutralStatuses;
  }

  public void setNeutralStatuses(String neutralStatuses) {
    this.neutralStatuses =
        defaultIfBlank(neutralStatuses, StatusClassifier.DEFAULT_NEUTRAL_STATUSES);
  }

  public String getRunningStatuses() {
    return runningStatuses;
  }

  public void setRunningStatuses(String runningStatuses) {
    this.runningStatuses =
        defaultIfBlank(runningStatuses, StatusClassifier.DEFAULT_RUNNING_STATUSES);
  }

  public StatusClassifier createStatusClassifier() {
    return new StatusClassifier(passedStatuses, failedStatuses, neutralStatuses, runningStatuses);
  }

  private String defaultIfBlank(String value, String defaultValue) {
    String trimmed = Util.trimToEmpty(value);
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
