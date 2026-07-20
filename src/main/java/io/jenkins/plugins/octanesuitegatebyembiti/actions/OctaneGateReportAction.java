package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import hudson.AbortException;
import hudson.model.Item;
import hudson.model.Run;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectTrend;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportArtifactMetadata;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportArtifactStore;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneReportZoneHtmlRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class OctaneGateReportAction implements RunAction2, OctaneGateReportPublisher, Serializable {
  private static final long serialVersionUID = 1L;

  public static final String URL_NAME = "octaneSuiteGateReport";

  private transient Run<?, ?> run;
  // Kept under its historical field name so reports saved by older plugin versions still load.
  // New publications clear it before Run.save() and persist only artifactMetadata.
  private OctaneGateReportSnapshot snapshot;
  private transient OctaneGateReportSnapshot snapshotCache = OctaneGateReportSnapshot.empty();
  private OctaneReportArtifactMetadata artifactMetadata = OctaneReportArtifactMetadata.empty();
  private int refreshSeconds = GateRequest.DEFAULT_POLL_INTERVAL_SECONDS;
  private int timeoutSeconds = GateRequest.DEFAULT_TIMEOUT_MINUTES * 60;
  private int timeoutExtendedSeconds = GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED * 60;
  private int basePassrateFigure = GateRequest.DEFAULT_BASE_PASSRATE_FIGURE;
  private int baseExecutionFigure = GateRequest.DEFAULT_BASE_EXECUTION_FIGURE;
  private String startedAt = Instant.now().toString();
  private volatile boolean manualExitRequested;
  private transient Object manualExitLock = new Object();
  private transient Runnable manualExitCallback;
  private transient Object refreshLock = new Object();
  private transient RefreshCallback refreshCallback;

  public static OctaneGateReportAction attachTo(Run<?, ?> run, GateRequest request) {
    OctaneGateReportAction action = run.getAction(OctaneGateReportAction.class);
    if (action == null) {
      action = new OctaneGateReportAction();
    }
    action.run = run;
    action.configureTimers(request);
    run.addOrReplaceAction(action);
    action.publishSnapshot(
        action.withPreviousCycleMetrics(
            OctaneGateReportSnapshot.waiting(request, action.refreshSeconds, action.startedAt)));
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
    publishSnapshot(
        withPreviousCycleMetrics(
            OctaneGateReportSnapshot.waiting(request, refreshSeconds, startedAt)));
  }

  @Override
  public synchronized void onPoll(GateResult result, StatusClassifier classifier) {
    publishSnapshot(
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                OctaneGateReportState.POLLING,
                "Polling ALM Octane suite runs.",
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt)));
  }

  @Override
  public synchronized void onExtendedTime(GateResult result, StatusClassifier classifier) {
    publishSnapshot(
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                OctaneGateReportState.EXTENDED_TIME,
                "Extended Octane polling time is active.",
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt)));
  }

  @Override
  public synchronized void onFinal(
      OctaneGateReportState state, String message, GateResult result, StatusClassifier classifier) {
    publishSnapshot(
        withLiveReportData(
            OctaneGateReportSnapshot.fromResult(
                state,
                message,
                result,
                classifier,
                refreshSeconds,
                timeoutSeconds,
                timeoutExtendedSeconds,
                startedAt)));
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
    OctaneGateReportSnapshot current = currentSnapshot();
    if (current != null) {
      errorSnapshot = errorSnapshot.withDefectTrend(current.getDefectTrend());
    }
    publishSnapshot(withPreviousCycleMetrics(errorSnapshot));
  }

  public synchronized OctaneGateReportSnapshot getSnapshot() {
    OctaneGateReportSnapshot current = currentSnapshot();
    return current == null ? OctaneGateReportSnapshot.empty() : current;
  }

  public synchronized int getRefreshSeconds() {
    return getSnapshot().getRefreshSeconds();
  }

  public synchronized boolean isAutoRefresh() {
    return getSnapshot().isBuilding();
  }

  public synchronized String getReportDataChecksum() {
    return artifactMetadata == null ? "" : artifactMetadata.getChecksum();
  }

  public synchronized int getReportDataSchemaVersion() {
    return artifactMetadata == null ? 0 : artifactMetadata.getSchemaVersion();
  }

  public String getReportUrl() {
    if (run == null) {
      return URL_NAME + "/";
    }
    String rootUrl = Jenkins.get().getRootUrl();
    String buildPath = run.getUrl() + URL_NAME + "/";
    return rootUrl == null ? buildPath : rootUrl + buildPath;
  }

  public synchronized void doSnapshot(StaplerRequest2 request, StaplerResponse2 response)
      throws IOException {
    checkReadPermission();
    String etag = currentEtag();
    if (etagMatches(request, etag)) {
      response.setStatus(304);
      return;
    }
    OctaneGateReportSnapshot current = currentSnapshot();
    OctaneGateReportSnapshot safeSnapshot =
        current == null ? OctaneGateReportSnapshot.empty() : current;
    JSONObject payload = new JSONObject();
    payload.put("updatedAt", safeSnapshot.getUpdatedAt());
    payload.put("updatedAtText", safeSnapshot.getUpdatedAtText());
    payload.put("startedAt", safeSnapshot.getStartedAt());
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
    payload.put(
        "riskHeatMap",
        safeSnapshot.isClientRenderedReport()
            ? safeSnapshot.getRiskHeatMap().toSummaryMap()
            : safeSnapshot.getRiskHeatMap().toMap());
    payload.put("testerDetails", safeSnapshot.getTesterDetails());
    payload.put("reportZoneDeferred", safeSnapshot.isClientRenderedReport());
    payload.put("reportDataUrl", "data");
    payload.put("reportDataChecksum", getReportDataChecksum());
    payload.put(
        "reportZoneHtml",
        safeSnapshot.isClientRenderedReport()
            ? ""
            : new OctaneReportZoneHtmlRenderer().renderZone(safeSnapshot));

    setDataHeaders(response, etag, safeSnapshot.isBuilding());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().print(payload.toString());
  }

  public synchronized void doData(
      StaplerRequest2 request,
      StaplerResponse2 response,
      @QueryParameter String section,
      @QueryParameter int cursor,
      @QueryParameter int limit)
      throws IOException {
    checkReadPermission();
    OctaneReportArtifactMetadata metadata = artifactMetadata;
    if (metadata == null || !metadata.isAvailable()) {
      response.sendError(404, "Octane report data is not available for this build.");
      return;
    }
    String etag = dataEtag(section, cursor, limit);
    if (etagMatches(request, etag)) {
      response.setStatus(304);
      return;
    }
    byte[] body;
    OctaneReportArtifactStore store = new OctaneReportArtifactStore();
    if (section == null || section.isBlank()) {
      body = store.readIndex(run, metadata);
    } else {
      int sectionNumber;
      try {
        sectionNumber = Integer.parseInt(section);
      } catch (NumberFormatException e) {
        response.sendError(400, "The Octane report section must be numeric.");
        return;
      }
      if (sectionNumber < 0 || sectionNumber >= metadata.getSectionCount()) {
        response.sendError(404, "The requested Octane report section does not exist.");
        return;
      }
      body = store.readSectionPage(run, metadata, sectionNumber, cursor, limit <= 0 ? 80 : limit);
    }
    setDataHeaders(response, etag, metadata.isBuilding());
    response.setContentType("application/json;charset=UTF-8");
    response.setContentLength(body.length);
    response.getOutputStream().write(body);
  }

  public void doScaleReportScript(StaplerResponse2 response) throws IOException {
    checkReadPermission();
    try (InputStream script =
        OctaneGateReportAction.class.getResourceAsStream("/js/octane-scale-report.js")) {
      if (script == null) {
        response.sendError(404, "The Octane scale report renderer is unavailable.");
        return;
      }
      response.setContentType("text/javascript;charset=UTF-8");
      response.setHeader("Cache-Control", "private, max-age=31536000, immutable");
      response.setHeader("X-Content-Type-Options", "nosniff");
      script.transferTo(response.getOutputStream());
    }
  }

  @RequirePOST
  public HttpResponse doExitOctaneAndContinue() {
    if (run != null) {
      run.getACL().checkPermission(Run.UPDATE);
    }
    Runnable callback = null;
    synchronized (manualExitLock()) {
      OctaneGateReportSnapshot current = currentSnapshot();
      if (current != null && current.isExtendedTime()) {
        manualExitRequested = true;
        manualExitLock().notifyAll();
        callback = manualExitCallback;
      }
    }
    saveRun();
    if (callback != null) {
      callback.run();
    }
    return HttpResponses.redirectToDot();
  }

  public void setManualExitCallback(Runnable callback) {
    synchronized (manualExitLock()) {
      manualExitCallback = callback;
    }
  }

  public void clearManualExitCallback(Runnable callback) {
    synchronized (manualExitLock()) {
      if (manualExitCallback == callback) {
        manualExitCallback = null;
      }
    }
  }

  public void setRefreshCallback(RefreshCallback callback) {
    synchronized (refreshLock()) {
      refreshCallback = callback;
    }
  }

  public void clearRefreshCallback(RefreshCallback callback) {
    synchronized (refreshLock()) {
      if (refreshCallback == callback) {
        refreshCallback = null;
      }
    }
  }

  public RefreshResult refreshIfStale(Duration threshold, Instant now) throws Exception {
    Duration effectiveThreshold = nonNegative(threshold);
    Instant effectiveNow = now == null ? Instant.now() : now;
    OctaneGateReportSnapshot current = getSnapshot();
    Duration age = snapshotAge(current, effectiveNow, effectiveThreshold);
    if (!current.isBuilding()) {
      return new RefreshResult(RefreshStatus.NOT_BUILDING, age);
    }
    if (age.compareTo(effectiveThreshold) <= 0) {
      return new RefreshResult(RefreshStatus.FRESH, age);
    }

    RefreshCallback callback;
    synchronized (refreshLock()) {
      callback = refreshCallback;
    }
    if (callback == null) {
      throw new AbortException(
          "Stale Octane progress data could not be refreshed because the active gate poller "
              + "is unavailable.");
    }
    boolean started = callback.refreshAndWait();
    return new RefreshResult(started ? RefreshStatus.REFRESHED : RefreshStatus.JOINED, age);
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

  private boolean saveRun() {
    if (run == null) {
      return false;
    }
    try {
      run.save();
      return true;
    } catch (IOException ignored) {
      // Jenkins can still render the in-memory report for the current build.
      return false;
    }
  }

  private void publishSnapshot(OctaneGateReportSnapshot nextSnapshot) {
    OctaneReportArtifactMetadata previousMetadata = artifactMetadata;
    snapshotCache = nextSnapshot == null ? OctaneGateReportSnapshot.empty() : nextSnapshot;
    snapshot = null;
    if (run == null) {
      return;
    }
    try {
      OctaneReportArtifactMetadata nextMetadata =
          new OctaneReportArtifactStore().publish(run, snapshotCache);
      artifactMetadata = nextMetadata;
      boolean saved = saveRun();
      if (saved
          && previousMetadata != null
          && previousMetadata.isAvailable()
          && !previousMetadata.getChecksum().equals(nextMetadata.getChecksum())) {
        new OctaneReportArtifactStore().deleteGeneration(run, previousMetadata);
      }
      if (saved && nextMetadata.isClientRendered()) {
        snapshotCache = null;
      }
    } catch (IOException ignored) {
      // Keep the in-memory report and the last complete artifact generation.
      saveRun();
    }
  }

  private OctaneGateReportSnapshot currentSnapshot() {
    if (snapshotCache != null) {
      return snapshotCache;
    }
    if (snapshot != null) {
      return snapshot;
    }
    if (run == null || artifactMetadata == null || !artifactMetadata.isAvailable()) {
      return null;
    }
    try {
      OctaneGateReportSnapshot loaded =
          new OctaneReportArtifactStore().loadSnapshot(run, artifactMetadata);
      if (!artifactMetadata.isClientRendered()) {
        snapshotCache = loaded;
      }
      return loaded;
    } catch (IOException ignored) {
      snapshotCache = null;
    }
    return null;
  }

  private void checkReadPermission() {
    if (run != null) {
      run.getACL().checkPermission(Item.READ);
    }
  }

  private String currentEtag() {
    String checksum = getReportDataChecksum();
    if (checksum.isBlank()) {
      checksum = getSnapshot().getUpdatedAt();
    }
    return '"' + checksum + (manualExitRequested ? "-exit" : "") + '"';
  }

  private String dataEtag(String section, int cursor, int limit) {
    String suffix =
        section == null || section.isBlank()
            ? "-index"
            : "-section-" + section + "-" + Math.max(0, cursor) + "-" + Math.max(1, limit);
    return '"' + getReportDataChecksum() + suffix + '"';
  }

  private boolean etagMatches(StaplerRequest2 request, String etag) {
    String supplied = request == null ? null : request.getHeader("If-None-Match");
    return supplied != null && supplied.equals(etag);
  }

  private void setDataHeaders(StaplerResponse2 response, String etag, boolean building) {
    response.setHeader("ETag", etag);
    response.setHeader("Cache-Control", "private, no-cache" + (building ? ", no-store" : ""));
    response.setHeader("X-Content-Type-Options", "nosniff");
  }

  private void configureTimers(GateRequest request) {
    refreshSeconds = request.getPollIntervalSeconds();
    timeoutSeconds = Math.max(1, request.getTimeoutMinutes()) * 60;
    timeoutExtendedSeconds = Math.max(0, request.getTimeoutMinutesExtended()) * 60;
    basePassrateFigure = request.getBasePassrateFigure();
    baseExecutionFigure = request.getBaseExecutionFigure();
    startedAt = Instant.now().toString();
    manualExitRequested = false;
  }

  private Object manualExitLock() {
    if (manualExitLock == null) {
      manualExitLock = new Object();
    }
    return manualExitLock;
  }

  private Object refreshLock() {
    if (refreshLock == null) {
      refreshLock = new Object();
    }
    return refreshLock;
  }

  private Duration nonNegative(Duration duration) {
    if (duration == null || duration.isNegative()) {
      return Duration.ZERO;
    }
    return duration;
  }

  private Duration snapshotAge(
      OctaneGateReportSnapshot current, Instant now, Duration effectiveThreshold) {
    try {
      Duration age = Duration.between(Instant.parse(current.getUpdatedAt()), now);
      return age.isNegative() ? Duration.ZERO : age;
    } catch (RuntimeException ignored) {
      return effectiveThreshold.plusSeconds(1L);
    }
  }

  @FunctionalInterface
  public interface RefreshCallback {
    boolean refreshAndWait() throws Exception;
  }

  public enum RefreshStatus {
    FRESH,
    REFRESHED,
    JOINED,
    NOT_BUILDING
  }

  public record RefreshResult(RefreshStatus status, Duration age) {}

  private OctaneGateReportSnapshot withPreviousCycleMetrics(OctaneGateReportSnapshot current) {
    return current
        .withTesterThresholds(basePassrateFigure, baseExecutionFigure)
        .withCalculatedTestMetrics(previousCompletedSnapshot());
  }

  private OctaneGateReportSnapshot withLiveReportData(OctaneGateReportSnapshot current) {
    OctaneDefectTrend trend = current.getDefectTrend();
    OctaneGateReportSnapshot previous = currentSnapshot();
    if (previous != null) {
      trend =
          previous
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
      OctaneGateReportSnapshot previousSnapshot = action == null ? null : action.getSnapshot();
      if (previousSnapshot != null && !previousSnapshot.isBuilding()) {
        return previousSnapshot;
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
