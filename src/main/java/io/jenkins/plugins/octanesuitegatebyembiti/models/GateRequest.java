package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GateRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final int DEFAULT_POLL_INTERVAL_SECONDS = 30;
  public static final int DEFAULT_TIMEOUT_MINUTES = 120;
  public static final int DEFAULT_TIMEOUT_MINUTES_EXTENDED = 0;
  public static final int DEFAULT_RISK_HEAT_MAP_MAX_DEFECTS = 1000;
  public static final String DEFAULT_CRITERIA = "100% execution AND 100% pass";

  private final String serverId;
  private final String suiteRunId;
  private String sharedSpaceId = "";
  private String workspaceId = "";
  private String criteria = DEFAULT_CRITERIA;
  private List<OctaneGateScope> scopes = new ArrayList<>();
  private int pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
  private int timeoutMinutesExtended = DEFAULT_TIMEOUT_MINUTES_EXTENDED;
  private boolean markUnstable;
  private boolean riskHeatMap;
  private String riskHeatMapDefectQuery = "";
  private int riskHeatMapMaxDefects = DEFAULT_RISK_HEAT_MAP_MAX_DEFECTS;
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

  public int getTimeoutMinutesExtended() {
    return timeoutMinutesExtended;
  }

  public void setTimeoutMinutesExtended(int timeoutMinutesExtended) {
    this.timeoutMinutesExtended = Math.max(0, timeoutMinutesExtended);
  }

  public boolean isMarkUnstable() {
    return markUnstable;
  }

  public void setMarkUnstable(boolean markUnstable) {
    this.markUnstable = markUnstable;
  }

  public boolean isRiskHeatMap() {
    return riskHeatMap;
  }

  public void setRiskHeatMap(boolean riskHeatMap) {
    this.riskHeatMap = riskHeatMap;
  }

  public String getRiskHeatMapDefectQuery() {
    return riskHeatMapDefectQuery;
  }

  public void setRiskHeatMapDefectQuery(String riskHeatMapDefectQuery) {
    this.riskHeatMapDefectQuery = Util.trimToEmpty(riskHeatMapDefectQuery);
  }

  public int getRiskHeatMapMaxDefects() {
    return riskHeatMapMaxDefects;
  }

  public void setRiskHeatMapMaxDefects(int riskHeatMapMaxDefects) {
    this.riskHeatMapMaxDefects = Math.max(1, riskHeatMapMaxDefects);
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
