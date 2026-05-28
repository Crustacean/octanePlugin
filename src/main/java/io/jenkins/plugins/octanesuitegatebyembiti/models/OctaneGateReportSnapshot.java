package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneRiskHeatMapRenderer;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OctaneGateReportSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final DateTimeFormatter EAT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Africa/Nairobi"));

  private final OctaneGateReportState state;
  private final String message;
  private final String criteria;
  private final String suiteRunId;
  private final int refreshSeconds;
  private final int timeoutSeconds;
  private final String startedAt;
  private final String updatedAt;
  private final List<OctaneGateReportSection> sections;
  private final OctaneRiskHeatMap riskHeatMap;

  private OctaneGateReportSnapshot(
      OctaneGateReportState state,
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt,
      String updatedAt,
      List<OctaneGateReportSection> sections,
      OctaneRiskHeatMap riskHeatMap) {
    this.state = state;
    this.message = message;
    this.criteria = criteria;
    this.suiteRunId = suiteRunId;
    this.refreshSeconds = Math.max(1, refreshSeconds);
    this.timeoutSeconds = Math.max(1, timeoutSeconds);
    this.startedAt = startedAt;
    this.updatedAt = updatedAt;
    this.sections = List.copyOf(sections);
    this.riskHeatMap = riskHeatMap == null ? OctaneRiskHeatMap.disabled() : riskHeatMap;
  }

  private static int toSeconds(int minutes) {
    return Math.max(1, minutes) * 60;
  }

  public static OctaneGateReportSnapshot empty() {
    String now = Instant.now().toString();
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.WAITING,
        "No Octane gate data yet.",
        "",
        "",
        30,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        now,
        now,
        List.of(),
        OctaneRiskHeatMap.disabled());
  }

  public static OctaneGateReportSnapshot waiting(GateRequest request, int refreshSeconds) {
    return waiting(request, refreshSeconds, Instant.now().toString());
  }

  public static OctaneGateReportSnapshot waiting(
      GateRequest request, int refreshSeconds, String startedAt) {
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.WAITING,
        "Waiting for ALM Octane polling to start.",
        request.getCriteria(),
        request.getSuiteRunId(),
        refreshSeconds,
        toSeconds(request.getTimeoutMinutes()),
        startedAt,
        Instant.now().toString(),
        List.of(),
        request.isRiskHeatMap() ? OctaneRiskHeatMap.waiting() : OctaneRiskHeatMap.disabled());
  }

  public static OctaneGateReportSnapshot fromResult(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier,
      int refreshSeconds) {
    return fromResult(
        state,
        message,
        result,
        classifier,
        refreshSeconds,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        result.getPolledAt().toString());
  }

  public static OctaneGateReportSnapshot fromResult(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt) {
    List<OctaneGateReportSection> sections = new ArrayList<>();
    sections.add(OctaneGateReportSection.regressions(result, classifier));
    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      sections.add(OctaneGateReportSection.scoped(scopeResult, classifier));
    }
    return new OctaneGateReportSnapshot(
        state,
        message,
        result.getCriteria(),
        result.getSuiteRunId(),
        refreshSeconds,
        timeoutSeconds,
        startedAt,
        result.getPolledAt().toString(),
        sections,
        result.getRiskHeatMap());
  }

  public static OctaneGateReportSnapshot error(
      String message, String criteria, String suiteRunId, int refreshSeconds) {
    return error(
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        Instant.now().toString(),
        false);
  }

  public static OctaneGateReportSnapshot error(
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt) {
    return error(message, criteria, suiteRunId, refreshSeconds, timeoutSeconds, startedAt, false);
  }

  public static OctaneGateReportSnapshot error(
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt,
      boolean riskHeatMapEnabled) {
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.ERROR,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        startedAt,
        Instant.now().toString(),
        List.of(),
        riskHeatMapEnabled
            ? OctaneRiskHeatMap.unavailable("Risk heat map is unavailable because polling stopped.")
            : OctaneRiskHeatMap.disabled());
  }

  public OctaneGateReportState getState() {
    return state;
  }

  public String getStateLabel() {
    return state.getLabel();
  }

  public String getMessage() {
    return message;
  }

  public String getCriteria() {
    return criteria;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public List<String> getSuiteRunIds() {
    return Util.splitIdList(suiteRunId);
  }

  public int getRefreshSeconds() {
    return refreshSeconds;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public String getStartedAt() {
    return startedAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedAtText() {
    try {
      return EAT_TIME_FORMATTER.format(Instant.parse(updatedAt));
    } catch (RuntimeException e) {
      return updatedAt;
    }
  }

  public List<OctaneGateReportSection> getSections() {
    return sections;
  }

  public OctaneRiskHeatMap getRiskHeatMap() {
    return riskHeatMap;
  }

  public boolean isRiskHeatMapEnabled() {
    return riskHeatMap.isEnabled();
  }

  public String getRiskHeatMapHtml() {
    return new OctaneRiskHeatMapRenderer().render(riskHeatMap, isBuilding(), getUpdatedAtText());
  }

  public List<OctaneGateReportSection> getReportSections() {
    return sections.stream().filter(section -> !section.isNoRuns()).toList();
  }

  public boolean isBuilding() {
    return state.isBuilding();
  }

  public String getTestingTimeTitle() {
    return isBuilding() ? "Testing Time Remaining" : "Testing Time";
  }

  public long getTestingTimeSpentMinutes() {
    if (isBuilding()) {
      return 0;
    }
    try {
      Instant started = Instant.parse(startedAt);
      Instant updated = Instant.parse(updatedAt);
      long timeoutMillis = timeoutSeconds * 1000L;
      long elapsedMillis = Duration.between(started, updated).toMillis();
      long clampedMillis = Math.max(0L, Math.min(timeoutMillis, elapsedMillis));
      return Math.round(clampedMillis / 60000.0);
    } catch (RuntimeException e) {
      return 0;
    }
  }

  public String getTestingTimeSpentUnit() {
    return getTestingTimeSpentMinutes() == 1 ? "minute" : "minutes";
  }

  public boolean hasSections() {
    return !sections.isEmpty();
  }

  public boolean hasReportSections() {
    return !getReportSections().isEmpty();
  }

  public double getExecutionProgress() {
    ProjectProgressCounts counts = projectProgressCounts();
    if (counts.total == 0) {
      return 0.0;
    }
    return counts.executed * 100.0 / counts.total;
  }

  public String getExecutionProgressText() {
    return String.format(Locale.ROOT, "%.0f%%", getExecutionProgress());
  }

  public int getPassRateTotal() {
    return projectProgressCounts().total;
  }

  public int getPassRatePassed() {
    return projectProgressCounts().passed;
  }

  public double getPassRateProgress() {
    int total = getPassRateTotal();
    if (total == 0) {
      return 0.0;
    }
    return getPassRatePassed() * 100.0 / total;
  }

  public String getPassRateProgressText() {
    return String.format(Locale.ROOT, "%.0f%%", getPassRateProgress());
  }

  public String getPassRateLabel() {
    return "All Testcase Pass Rate (" + getPassRatePassed() + " / " + getPassRateTotal() + ")";
  }

  private static boolean isProjectProgressSection(OctaneGateReportSection section) {
    String source = section.getSource();
    return isRegressionSection(section) || "critical".equalsIgnoreCase(source);
  }

  private ProjectProgressCounts projectProgressCounts() {
    Set<String> criticalSuiteRunIds = criticalSuiteRunIds();
    ProjectProgressCounts counts = new ProjectProgressCounts();
    for (OctaneGateReportSection section : sections) {
      if (!isProjectProgressSection(section)) {
        continue;
      }
      if (section.getSuiteRuns().isEmpty()) {
        counts.add(section.getMetrics());
        continue;
      }
      for (OctaneGateSuiteRunChart suiteRun : section.getSuiteRuns()) {
        if (isRegressionSection(section)
            && suiteRun.getSuiteRunIds().stream().anyMatch(criticalSuiteRunIds::contains)) {
          continue;
        }
        counts.add(suiteRun);
      }
    }
    return counts;
  }

  private Set<String> criticalSuiteRunIds() {
    Set<String> criticalSuiteRunIds = new LinkedHashSet<>();
    for (OctaneGateReportSection section : sections) {
      if ("critical".equalsIgnoreCase(section.getSource())) {
        criticalSuiteRunIds.addAll(section.getSuiteRunIds());
      }
    }
    return criticalSuiteRunIds;
  }

  private static boolean isRegressionSection(OctaneGateReportSection section) {
    String source = section.getSource();
    return "regressions".equalsIgnoreCase(source) || "global".equalsIgnoreCase(source);
  }

  private static class ProjectProgressCounts {
    private int total;
    private int executed;
    private int passed;

    private void add(GateMetrics metrics) {
      total += metrics.getTotal();
      executed += metrics.getExecuted();
      passed += metrics.getPassed();
    }

    private void add(OctaneGateSuiteRunChart suiteRun) {
      total += suiteRun.getTotal();
      for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
        if (status.getBucket() != OctaneGateStatusBucket.RUNNING) {
          executed += status.getCount();
        }
        if (status.getBucket() == OctaneGateStatusBucket.PASSED) {
          passed += status.getCount();
        }
      }
    }
  }
}
