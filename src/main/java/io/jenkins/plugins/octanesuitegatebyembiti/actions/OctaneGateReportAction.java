package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import hudson.model.Run;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportZoneHtmlRenderer;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerResponse2;

public class OctaneGateReportAction implements RunAction2, OctaneGateReportPublisher, Serializable {
  private static final long serialVersionUID = 1L;

  public static final String URL_NAME = "octaneSuiteGateReport";

  private transient Run<?, ?> run;
  private OctaneGateReportSnapshot snapshot = OctaneGateReportSnapshot.empty();
  private int refreshSeconds = GateRequest.DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutSeconds = GateRequest.DEFAULT_TIMEOUT_MINUTES * 60;
  private String startedAt = Instant.now().toString();

  public static OctaneGateReportAction attachTo(Run<?, ?> run, GateRequest request) {
    OctaneGateReportAction action = run.getAction(OctaneGateReportAction.class);
    if (action == null) {
      action = new OctaneGateReportAction();
    }
    action.run = run;
    action.configureTimers(request);
    action.snapshot =
        OctaneGateReportSnapshot.waiting(request, action.refreshSeconds, action.startedAt);
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
    snapshot = OctaneGateReportSnapshot.waiting(request, refreshSeconds, startedAt);
    saveRun();
  }

  @Override
  public synchronized void onPoll(GateResult result, StatusClassifier classifier) {
    snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING,
            "Polling ALM Octane suite runs.",
            result,
            classifier,
            refreshSeconds,
            timeoutSeconds,
            startedAt);
    saveRun();
  }

  @Override
  public synchronized void onFinal(
      OctaneGateReportState state, String message, GateResult result, StatusClassifier classifier) {
    snapshot =
        OctaneGateReportSnapshot.fromResult(
            state, message, result, classifier, refreshSeconds, timeoutSeconds, startedAt);
    saveRun();
  }

  @Override
  public synchronized void onError(String message, GateRequest request) {
    snapshot =
        OctaneGateReportSnapshot.error(
            defaultMessage(message),
            request.getCriteria(),
            request.getSuiteRunId(),
            refreshSeconds,
            timeoutSeconds,
            startedAt,
            request.isRiskHeatMap());
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
    payload.put("passRateProgress", safeSnapshot.getPassRateProgress());
    payload.put("passRateProgressText", safeSnapshot.getPassRateProgressText());
    payload.put("passRateLabel", safeSnapshot.getPassRateLabel());
    payload.put("refreshSeconds", safeSnapshot.getRefreshSeconds());
    payload.put("riskHeatMapEnabled", safeSnapshot.isRiskHeatMapEnabled());
    payload.put("riskHeatMapHtml", safeSnapshot.getRiskHeatMapHtml());
    payload.put("riskHeatMap", safeSnapshot.getRiskHeatMap().toMap());
    payload.put("reportZoneHtml", new OctaneReportZoneHtmlRenderer().renderZone(safeSnapshot));

    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().print(payload.toString());
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
    startedAt = Instant.now().toString();
  }

  private String defaultMessage(String message) {
    if (message == null || message.isBlank()) {
      return "ALM Octane suite gate stopped before a result was available.";
    }
    return message;
  }
}
