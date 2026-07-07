package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import hudson.model.Run;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectTrend;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportZoneHtmlRenderer;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class OctaneGateReportAction implements RunAction2, OctaneGateReportPublisher, Serializable {
  private static final long serialVersionUID = 1L;

  public static final String URL_NAME = "octaneSuiteGateReport";

  private transient Run<?, ?> run;
  private OctaneGateReportSnapshot snapshot = OctaneGateReportSnapshot.empty();
  private int refreshSeconds = GateRequest.DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutSeconds = GateRequest.DEFAULT_TIMEOUT_MINUTES * 60;
  private int timeoutExtendedSeconds = GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED * 60;
  private String startedAt = Instant.now().toString();
  private volatile boolean manualExitRequested;
  private transient Object manualExitLock = new Object();

  public static OctaneGateReportAction attachTo(Run<?, ?> run, GateRequest request) {
    OctaneGateReportAction action = run.getAction(OctaneGateReportAction.class);
    if (action == null) {
      action = new OctaneGateReportAction();
    }
    action.run = run;
    action.configureTimers(request);
    action.snapshot =
        action.withPreviousCycleMetrics(
            OctaneGateReportSnapshot.waiting(request, action.refreshSeconds, action.startedAt));
    run.addOrReplaceAction(action);
    action.saveRun();
    return action;
  }

  @Override
  public String getIconFileName() {
    return "graph.gif";
  }

  @Override
  public String getDisplayName() {
    return "Octane Gate Report";
  }

  @Override
  public String getUrlName() {
    return URL_NAME;
  }

  @Override
  public void onAttached(Run<?, ?> run) {
    this.run = run;
  }

  @Override
  public void onLoad(Run<?, ?> run) {
    this.run = run;
  }

  @Override
  public synchronized void onWaiting(GateRequest request, List<String> suiteRunIds) {
    configureTimers(request);
    snapshot =
        withPreviousCycleMetrics(
            OctaneGateReportSnapshot.waiting(request, refreshSeconds, startedAt));
    saveRun();
  }

  @Override
  public synchronized void onPoll(GateResult result, StatusClassifier classifier) {
    snapshot =
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                OctaneGateReportState.POLLING,
                "Polling ALM Octane suite runs.",
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt));
    saveRun();
  }

  @Override
  public synchronized void onExtendedTime(GateResult result, StatusClassifier classifier) {
    snapshot =
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                OctaneGateReportState.EXTENDED_TIME,
                "Extended Octane polling time is active.",
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt));
    saveRun();
  }

  @Override
  public synchronized void onFinal(
      OctaneGateReportState state, String message, GateResult result, StatusClassifier classifier) {
    snapshot =
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                state,
                message,
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt));
    saveRun();
  }

  @Override
  public synchronized void onError(String message, GateRequest request) {
    OctaneGateReportSnapshot errorSnapshot =
        OctaneGateReportSnapshot.error(
            defaultMessage(message),
            request.getCriteria(),
            request.getSuiteRunId(),
            refreshSeconds,
            timeoutSeconds,
            timeoutExtendedSeconds,
            startedAt,
            request.isRiskHeatMap());
    if (snapshot != null) {
      errorSnapshot = errorSnapshot.withDefectTrend(snapshot.getDefectTrend());
    }
    snapshot = withPreviousCycleMetrics(errorSnapshot);
    saveRun();
  }

  public synchronized OctaneGateReportSnapshot getSnapshot() {
    return snapshot;
  }

  public synchronized int getRefreshSeconds() {
    return snapshot.getRefreshSeconds();
  }

  public synchronized boolean isAutoRefresh() {
    return snapshot.isBuilding();
  }

  public String getReportUrl() {
    if (run == null) {
      return URL_NAME + "/";
    }
    String rootUrl = Jenkins.get().getRootUrl();
    String buildPath = run.getUrl() + URL_NAME + "/";
    return rootUrl == null ? buildPath : rootUrl + buildPath;
  }

  public synchronized void doSnapshot(StaplerResponse2 response) throws IOException {
    OctaneGateReportSnapshot safeSnapshot =
        snapshot == null ? OctaneGateReportSnapshot.empty() : snapshot;
    JSONObject payload = new JSONObject();
    payload.put("updatedAt", safeSnapshot.getUpdatedAt());
    payload.put("updatedAtText", safeSnapshot.getUpdatedAtText());
    payload.put("building", safeSnapshot.isBuilding());
    payload.put("stateLabel", safeSnapshot.getStateLabel());
    payload.put("message", safeSnapshot.getMessage());
    payload.put("executionProgress", safeSnapshot.getExecutionProgress());
    payload.put("executionProgressText", safeSnapshot.getExecutionProgressText());
    payload.put(
        "executionStatusDistributionHtml", safeSnapshot.getExecutionStatusDistributionHtml());
    payload.put("passRateProgress", safeSnapshot.getPassRateProgress());
    payload.put("passRateProgressText", safeSnapshot.getPassRateProgressText());
    payload.put("passRateLabel", safeSnapshot.getPassRateLabel());
    payload.put("testMetricsHtml", safeSnapshot.getTestMetricsHtml());
    payload.put("testMetrics", safeSnapshot.getTestMetrics().toMap());
    payload.put("defectTrend", safeSnapshot.getDefectTrend().toMap());
    payload.put("refreshSeconds", safeSnapshot.getRefreshSeconds());
    payload.put("timeoutSeconds", safeSnapshot.getTimeoutSeconds());
    payload.put("timeoutExtendedSeconds", safeSnapshot.getTimeoutExtendedSeconds());
    payload.put("extendedTime", safeSnapshot.isExtendedTime());
    payload.put("manualExitRequested", isManualExitRequested());
    payload.put("riskHeatMapEnabled", safeSnapshot.isRiskHeatMapEnabled());
    payload.put("riskHeatMapHtml", safeSnapshot.getRiskHeatMapHtml());
    payload.put("riskHeatMap", safeSnapshot.getRiskHeatMap().toMap());
    payload.put("reportZoneHtml", new OctaneReportZoneHtmlRenderer().renderZone(safeSnapshot));

    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().print(payload.toString());
  }

  @RequirePOST
  public HttpResponse doExitOctaneAndContinue() {
    if (run != null) {
      run.getACL().checkPermission(Run.UPDATE);
    }
    synchronized (manualExitLock()) {
      if (snapshot != null && snapshot.isExtendedTime()) {
        manualExitRequested = true;
        manualExitLock().notifyAll();
      }
    }
    saveRun();
    return HttpResponses.redirectToDot();
  }

  @Override
  public synchronized boolean isManualExitRequested() {
    return manualExitRequested;
  }

  @Override
  public boolean awaitNextPollOrManualExit(Duration duration) throws InterruptedException {
    long timeoutMillis = Math.max(0L, duration.toMillis());
    long deadline = System.currentTimeMillis() + timeoutMillis;
    synchronized (manualExitLock()) {
      while (!manualExitRequested && timeoutMillis > 0L) {
        manualExitLock().wait(timeoutMillis);
        timeoutMillis = deadline - System.currentTimeMillis();
      }
      return manualExitRequested;
    }
  }

  private void saveRun() {
    if (run == null) {
      return;
    }
    try {
      run.save();
    } catch (IOException ignored) {
      // Jenkins can still render the in-memory report for the current build.
    }
  }

  private void configureTimers(GateRequest request) {
    refreshSeconds = request.getPollIntervalSeconds();
    timeoutSeconds = Math.max(1, request.getTimeoutMinutes()) * 60;
    timeoutExtendedSeconds = Math.max(0, request.getTimeoutMinutesExtended()) * 60;
    startedAt = Instant.now().toString();
    manualExitRequested = false;
  }

  private Object manualExitLock() {
    if (manualExitLock == null) {
      manualExitLock = new Object();
    }
    return manualExitLock;
  }

  private OctaneGateReportSnapshot withPreviousCycleMetrics(OctaneGateReportSnapshot current) {
    return current.withCalculatedTestMetrics(previousCompletedSnapshot());
  }

  private OctaneGateReportSnapshot withLiveReportData(OctaneGateReportSnapshot current) {
    OctaneDefectTrend trend = current.getDefectTrend();
    if (snapshot != null) {
      trend =
          snapshot
              .getDefectTrend()
              .append(
                  current.getUpdatedAt(), current.getRiskHeatMap(), current.getExecutedTestCount());
    }
    return withPreviousCycleMetrics(current.withDefectTrend(trend));
  }

  private OctaneGateReportSnapshot previousCompletedSnapshot() {
    if (run == null) {
      return null;
    }
    Run<?, ?> previous = run.getPreviousCompletedBuild();
    while (previous != null) {
      OctaneGateReportAction action = previous.getAction(OctaneGateReportAction.class);
      if (action != null && action.getSnapshot() != null && !action.getSnapshot().isBuilding()) {
        return action.getSnapshot();
      }
      previous = previous.getPreviousCompletedBuild();
    }
    return null;
  }

  private String defaultMessage(String message) {
    if (message == null || message.isBlank()) {
      return "ALM Octane suite gate stopped before a result was available.";
    }
    return message;
  }
}
