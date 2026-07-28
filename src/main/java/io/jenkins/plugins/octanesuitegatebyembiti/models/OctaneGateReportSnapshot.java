package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneExecutionStatusDistributionRenderer;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneRiskHeatMapRenderer;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneTestMetricsRenderer;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OctaneGateReportSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;
  public static final int CLIENT_RENDER_BAR_THRESHOLD = 80;
  private static final DateTimeFormatter EAT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Africa/Nairobi"));

  private final OctaneGateReportState state;
  private final String message;
  private final String criteria;
  private final String suiteRunId;
  private final int refreshSeconds;
  private final int timeoutSeconds;
  private final int timeoutExtendedSeconds;
  private final String startedAt;
  private final String updatedAt;
  private final List<OctaneGateReportSection> sections;
  private final OctaneRiskHeatMap riskHeatMap;
  private final OctaneTestMetrics testMetrics;
  private final OctaneDefectTrend defectTrend;
  private final OctaneTestManagementAnalytics testManagement;
  private final DefectCriteriaMetrics defectMetrics;
  private final CriteriaEvaluation criteriaEvaluation;
  private final List<OctaneTesterPerformance> testerPerformances;
  private final int basePassrateFigure;
  private final int baseExecutionFigure;

  private OctaneGateReportSnapshot(
      OctaneGateReportState state,
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      int timeoutExtendedSeconds,
      String startedAt,
      String updatedAt,
      List<OctaneGateReportSection> sections,
      OctaneRiskHeatMap riskHeatMap,
      OctaneTestMetrics testMetrics,
      OctaneDefectTrend defectTrend,
      OctaneTestManagementAnalytics testManagement,
      DefectCriteriaMetrics defectMetrics,
      CriteriaEvaluation criteriaEvaluation,
      List<OctaneTesterPerformance> testerPerformances,
      int basePassrateFigure,
      int baseExecutionFigure) {
    this.state = state;
    this.message = message;
    this.criteria = criteria;
    this.suiteRunId = suiteRunId;
    this.refreshSeconds = Math.max(1, refreshSeconds);
    this.timeoutSeconds = Math.max(1, timeoutSeconds);
    this.timeoutExtendedSeconds = Math.max(0, timeoutExtendedSeconds);
    this.startedAt = startedAt;
    this.updatedAt = updatedAt;
    this.sections = List.copyOf(sections);
    this.riskHeatMap = riskHeatMap == null ? OctaneRiskHeatMap.disabled() : riskHeatMap;
    this.testMetrics = testMetrics == null ? OctaneTestMetrics.empty() : testMetrics;
    this.defectTrend =
        defectTrend == null
            ? OctaneDefectTrend.start(
                startedAt, (this.timeoutSeconds + this.timeoutExtendedSeconds) * 1000L)
            : defectTrend;
    this.testManagement =
        testManagement == null
            ? OctaneTestManagementAnalytics.empty(
                startedAt, (this.timeoutSeconds + this.timeoutExtendedSeconds) * 1000L)
            : testManagement;
    this.defectMetrics =
        defectMetrics == null
            ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of())
            : defectMetrics;
    this.criteriaEvaluation =
        criteriaEvaluation == null ? CriteriaEvaluation.unavailable() : criteriaEvaluation;
    this.testerPerformances =
        testerPerformances == null ? List.of() : List.copyOf(testerPerformances);
    this.basePassrateFigure = percentageThreshold(basePassrateFigure);
    this.baseExecutionFigure = percentageThreshold(baseExecutionFigure);
  }

  private static int toSeconds(int minutes) {
    return Math.max(1, minutes) * 60;
  }

  private static int toExtendedSeconds(int minutes) {
    return Math.max(0, minutes) * 60;
  }

  public static OctaneGateReportSnapshot empty() {
    String now = Instant.now().toString();
    OctaneGateReportSnapshot snapshot =
        new OctaneGateReportSnapshot(
            OctaneGateReportState.WAITING,
            "No Octane gate data yet.",
            "",
            "",
            30,
            toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
            toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED),
            now,
            now,
            List.of(),
            OctaneRiskHeatMap.disabled(),
            OctaneTestMetrics.empty(),
            OctaneDefectTrend.start(
                now,
                (toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES)
                        + toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED))
                    * 1000L),
            OctaneTestManagementAnalytics.empty(
                now,
                (toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES)
                        + toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED))
                    * 1000L),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
            CriteriaEvaluation.unavailable(),
            List.of(),
            GateRequest.DEFAULT_BASE_PASSRATE_FIGURE,
            GateRequest.DEFAULT_BASE_EXECUTION_FIGURE);
    return snapshot.withCalculatedTestMetrics(null);
  }

  public static OctaneGateReportSnapshot waiting(GateRequest request, int refreshSeconds) {
    return waiting(request, refreshSeconds, Instant.now().toString());
  }

  public static OctaneGateReportSnapshot waiting(
      GateRequest request, int refreshSeconds, String startedAt) {
    OctaneGateReportSnapshot snapshot =
        new OctaneGateReportSnapshot(
            OctaneGateReportState.WAITING,
            "Waiting for ALM Octane polling to start.",
            request.getCriteria(),
            request.getSuiteRunId(),
            refreshSeconds,
            toSeconds(request.getTimeoutMinutes()),
            toExtendedSeconds(request.getTimeoutMinutesExtended()),
            startedAt,
            Instant.now().toString(),
            List.of(),
            request.isRiskHeatMap() ? OctaneRiskHeatMap.waiting() : OctaneRiskHeatMap.disabled(),
            OctaneTestMetrics.empty(),
            OctaneDefectTrend.start(
                startedAt,
                (toSeconds(request.getTimeoutMinutes())
                        + toExtendedSeconds(request.getTimeoutMinutesExtended()))
                    * 1000L),
            OctaneTestManagementAnalytics.empty(
                startedAt,
                (toSeconds(request.getTimeoutMinutes())
                        + toExtendedSeconds(request.getTimeoutMinutesExtended()))
                    * 1000L),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
            CriteriaEvaluation.unavailable(),
            List.of(),
            request.getBasePassrateFigure(),
            request.getBaseExecutionFigure());
    return snapshot.withCalculatedTestMetrics(null);
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
        toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED),
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
    return fromResult(
        state,
        message,
        result,
        classifier,
        refreshSeconds,
        timeoutSeconds,
        toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED),
        startedAt);
  }

  public static OctaneGateReportSnapshot fromResult(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier,
      int refreshSeconds,
      int timeoutSeconds,
      int timeoutExtendedSeconds,
      String startedAt) {
    List<OctaneGateReportSection> sections = new ArrayList<>();
    if (result.isRegressionEvaluationEnabled()) {
      sections.add(OctaneGateReportSection.regressions(result, classifier));
    }
    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      sections.add(OctaneGateReportSection.scoped(scopeResult, classifier));
    }
    OctaneGateReportSnapshot snapshot =
        new OctaneGateReportSnapshot(
            state,
            message,
            result.getCriteria(),
            result.getSuiteRunId(),
            refreshSeconds,
            timeoutSeconds,
            timeoutExtendedSeconds,
            startedAt,
            result.getPolledAt().toString(),
            sections,
            result.getRiskHeatMap(),
            OctaneTestMetrics.empty(),
            OctaneDefectTrend.start(startedAt, (timeoutSeconds + timeoutExtendedSeconds) * 1000L),
            OctaneTestManagementAnalytics.fromResult(
                startedAt,
                (timeoutSeconds + timeoutExtendedSeconds) * 1000L,
                result,
                classifier,
                GateRequest.DEFAULT_BASE_EXECUTION_FIGURE),
            result.getDefectMetrics(),
            result.getCriteriaEvaluation(),
            OctaneTesterPerformance.fromResult(result, classifier),
            GateRequest.DEFAULT_BASE_PASSRATE_FIGURE,
            GateRequest.DEFAULT_BASE_EXECUTION_FIGURE);
    OctaneDefectTrend trend =
        snapshot
            .getDefectTrend()
            .append(
                result.getPolledAt().toString(),
                result.getRiskHeatMap(),
                snapshot.getExecutedTestCount());
    return snapshot.withDefectTrend(trend).withCalculatedTestMetrics(null);
  }

  public static OctaneGateReportSnapshot error(
      String message, String criteria, String suiteRunId, int refreshSeconds) {
    return error(
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED),
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
    return error(
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        toExtendedSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES_EXTENDED),
        startedAt,
        false);
  }

  public static OctaneGateReportSnapshot error(
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      int timeoutExtendedSeconds,
      String startedAt,
      boolean riskHeatMapEnabled) {
    OctaneGateReportSnapshot snapshot =
        new OctaneGateReportSnapshot(
            OctaneGateReportState.ERROR,
            message,
            criteria,
            suiteRunId,
            refreshSeconds,
            timeoutSeconds,
            timeoutExtendedSeconds,
            startedAt,
            Instant.now().toString(),
            List.of(),
            riskHeatMapEnabled
                ? OctaneRiskHeatMap.unavailable(
                    "Risk heat map is unavailable because polling stopped.")
                : OctaneRiskHeatMap.disabled(),
            OctaneTestMetrics.empty(),
            OctaneDefectTrend.start(startedAt, (timeoutSeconds + timeoutExtendedSeconds) * 1000L),
            OctaneTestManagementAnalytics.empty(
                startedAt, (timeoutSeconds + timeoutExtendedSeconds) * 1000L),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
            CriteriaEvaluation.unavailable(),
            List.of(),
            GateRequest.DEFAULT_BASE_PASSRATE_FIGURE,
            GateRequest.DEFAULT_BASE_EXECUTION_FIGURE);
    return snapshot.withCalculatedTestMetrics(null);
  }

  public OctaneGateReportSnapshot withCalculatedTestMetrics(
      OctaneGateReportSnapshot previousSnapshot) {
    return withTestMetrics(OctaneTestMetrics.fromSnapshots(this, previousSnapshot));
  }

  public OctaneGateReportSnapshot withTestMetrics(OctaneTestMetrics testMetrics) {
    return new OctaneGateReportSnapshot(
        state,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        timeoutExtendedSeconds,
        startedAt,
        updatedAt,
        sections,
        riskHeatMap,
        testMetrics,
        getDefectTrend(),
        getTestManagement(),
        getDefectMetrics(),
        getCriteriaEvaluation(),
        testerPerformances,
        basePassrateFigure,
        baseExecutionFigure);
  }

  public OctaneGateReportSnapshot withDefectTrend(OctaneDefectTrend defectTrend) {
    return new OctaneGateReportSnapshot(
        state,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        timeoutExtendedSeconds,
        startedAt,
        updatedAt,
        sections,
        riskHeatMap,
        testMetrics,
        defectTrend,
        getTestManagement(),
        getDefectMetrics(),
        getCriteriaEvaluation(),
        testerPerformances,
        basePassrateFigure,
        baseExecutionFigure);
  }

  public OctaneGateReportSnapshot withTesterThresholds(
      int basePassrateFigure, int baseExecutionFigure) {
    return new OctaneGateReportSnapshot(
        state,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        timeoutExtendedSeconds,
        startedAt,
        updatedAt,
        sections,
        riskHeatMap,
        testMetrics,
        getDefectTrend(),
        getTestManagement().withExecutionTarget(baseExecutionFigure),
        getDefectMetrics(),
        getCriteriaEvaluation(),
        testerPerformances,
        basePassrateFigure,
        baseExecutionFigure);
  }

  public OctaneGateReportSnapshot withTestManagement(OctaneTestManagementAnalytics testManagement) {
    return new OctaneGateReportSnapshot(
        state,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        timeoutExtendedSeconds,
        startedAt,
        updatedAt,
        sections,
        riskHeatMap,
        testMetrics,
        getDefectTrend(),
        testManagement,
        getDefectMetrics(),
        getCriteriaEvaluation(),
        testerPerformances,
        basePassrateFigure,
        baseExecutionFigure);
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

  public String getEmptyReportMessage() {
    if (message == null || message.isBlank()) {
      return "The report will populate after the first Octane poll.";
    }
    return message;
  }

  public String getCriteria() {
    return criteria;
  }

  public boolean hasCriteria() {
    return criteria != null && !criteria.isBlank();
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public List<String> getSuiteRunIds() {
    return Util.splitIdList(suiteRunId);
  }

  public boolean isRegressionEvaluationEnabled() {
    return !getSuiteRunIds().isEmpty();
  }

  public boolean isCriticalOnlyReport() {
    if (isRegressionEvaluationEnabled()) {
      return false;
    }
    List<OctaneGateReportSection> reportSections = getReportSections();
    return reportSections.size() == 1
        && "critical".equalsIgnoreCase(reportSections.get(0).getSource());
  }

  public int getRefreshSeconds() {
    return refreshSeconds;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public int getTimeoutExtendedSeconds() {
    return timeoutExtendedSeconds;
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

  public OctaneTestMetrics getTestMetrics() {
    return testMetrics;
  }

  public OctaneDefectTrend getDefectTrend() {
    if (defectTrend != null) {
      return defectTrend;
    }
    return OctaneDefectTrend.start(startedAt, (timeoutSeconds + timeoutExtendedSeconds) * 1000L);
  }

  public OctaneTestManagementAnalytics getTestManagement() {
    if (testManagement != null) {
      return testManagement;
    }
    return OctaneTestManagementAnalytics.empty(
            startedAt, (timeoutSeconds + timeoutExtendedSeconds) * 1000L)
        .withExecutionTarget(baseExecutionFigure);
  }

  public CriteriaEvaluation getCriteriaEvaluation() {
    return criteriaEvaluation == null ? CriteriaEvaluation.unavailable() : criteriaEvaluation;
  }

  public int getBasePassrateFigure() {
    return basePassrateFigure;
  }

  public int getBaseExecutionFigure() {
    return baseExecutionFigure;
  }

  public List<OctaneTesterPerformance> getTesterPerformances() {
    return testerPerformances == null ? List.of() : testerPerformances;
  }

  public List<OctaneTesterPerformance> getTesterPassRateDetails() {
    return getTesterPerformances().stream()
        .filter(tester -> tester.getExecutionRate() > 0.0)
        .filter(tester -> tester.getPassRate() < basePassrateFigure)
        .sorted(
            Comparator.comparingDouble((OctaneTesterPerformance tester) -> tester.getPassRate())
                .thenComparing(
                    (OctaneTesterPerformance tester) -> tester.getEmail(),
                    String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public boolean isTesterPassRateDetailsEmpty() {
    return getTesterPassRateDetails().isEmpty();
  }

  public int getTesterPassRateDetailsCount() {
    return getTesterPassRateDetails().size();
  }

  public List<OctaneTesterPerformance> getTesterExecutionDetails() {
    return getTesterPerformances().stream()
        .filter(tester -> tester.getExecutionRate() < baseExecutionFigure)
        .sorted(
            Comparator.comparingDouble(
                    (OctaneTesterPerformance tester) -> tester.getExecutionRate())
                .thenComparing(
                    (OctaneTesterPerformance tester) -> tester.getEmail(),
                    String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public boolean isTesterExecutionDetailsEmpty() {
    return getTesterExecutionDetails().isEmpty();
  }

  public int getTesterExecutionDetailsCount() {
    return getTesterExecutionDetails().size();
  }

  public Map<String, Object> getTesterDetails() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("basePassrateFigure", basePassrateFigure);
    details.put("baseExecutionFigure", baseExecutionFigure);
    details.put(
        "passRateTesters",
        getTesterPassRateDetails().stream().map(tester -> tester.toMap()).toList());
    details.put(
        "executionTesters",
        getTesterExecutionDetails().stream().map(tester -> tester.toMap()).toList());
    return details;
  }

  public DefectCriteriaMetrics getDefectMetrics() {
    return defectMetrics == null
        ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of())
        : defectMetrics;
  }

  public String getTestMetricsHtml() {
    return new OctaneTestMetricsRenderer().render(testMetrics);
  }

  public OctaneExecutionStatusDistribution getExecutionStatusDistribution() {
    return OctaneExecutionStatusDistribution.fromStatusCounts(
        projectProgressCounts().toStatusCounts());
  }

  public String getExecutionStatusDistributionHtml() {
    return new OctaneExecutionStatusDistributionRenderer().render(getExecutionStatusDistribution());
  }

  public List<OctaneGateReportSection> getReportSections() {
    return sections.stream().filter(section -> !section.isNoRuns()).toList();
  }

  public boolean isBuilding() {
    return state.isBuilding();
  }

  public boolean isExtendedTime() {
    return state == OctaneGateReportState.EXTENDED_TIME;
  }

  public String getTestingTimeTitle() {
    return "Testing Session Monitor";
  }

  public long getTestingTimeSpentMinutes() {
    if (isBuilding()) {
      return 0;
    }
    return Math.round(getTestingElapsedMillis() / 60000.0);
  }

  public String getTestingTimeSpentUnit() {
    return getTestingTimeSpentMinutes() == 1 ? "minute" : "minutes";
  }

  public long getTestingElapsedMillis() {
    try {
      Instant started = Instant.parse(startedAt);
      Instant updated = Instant.parse(updatedAt);
      long timeoutMillis = (timeoutSeconds + timeoutExtendedSeconds) * 1000L;
      long elapsedMillis = Duration.between(started, updated).toMillis();
      return Math.max(0L, Math.min(timeoutMillis, elapsedMillis));
    } catch (RuntimeException e) {
      return 0L;
    }
  }

  public boolean hasSections() {
    return !sections.isEmpty();
  }

  public boolean hasReportSections() {
    return !getReportSections().isEmpty();
  }

  public boolean isClientRenderedReport() {
    int barCount = 0;
    for (OctaneGateReportSection section : getReportSections()) {
      barCount += section.getSuiteRuns().size();
      if (barCount > CLIENT_RENDER_BAR_THRESHOLD) {
        return true;
      }
    }
    return false;
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

  public int getProjectTestTotal() {
    return projectProgressCounts().total;
  }

  public int getExecutedTestCount() {
    return projectProgressCounts().executed;
  }

  public int getPassedTestCount() {
    return projectProgressCounts().passed;
  }

  public boolean hasDefectMetrics() {
    return riskHeatMap.isEnabled() && riskHeatMap.isAvailable();
  }

  public int getOpenDefectCount() {
    return riskHeatMap.getDefectSeveritySummary().getOpenTotal();
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

  private static int percentageThreshold(int value) {
    return Math.min(100, Math.max(0, value));
  }

  private static class ProjectProgressCounts {
    private int total;
    private int executed;
    private int passed;
    private final Map<OctaneGateStatusBucket, Integer> statusCounts =
        OctaneGateSuiteRunChart.emptyCounts();

    private void add(GateMetrics metrics) {
      total += metrics.getTotal();
      executed += metrics.getExecuted();
      passed += metrics.getPassed();
      addStatusCount(OctaneGateStatusBucket.PASSED, metrics.getPassed());
      addStatusCount(OctaneGateStatusBucket.FAILED, metrics.getFailed());
      addStatusCount(OctaneGateStatusBucket.SKIPPED, metrics.getSkipped());
      addStatusCount(OctaneGateStatusBucket.RUNNING, metrics.getRunning());
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
        addStatusCount(status.getBucket(), status.getCount());
      }
    }

    private void addStatusCount(OctaneGateStatusBucket bucket, int count) {
      statusCounts.put(bucket, statusCounts.get(bucket) + count);
    }

    private List<OctaneGateStatusCount> toStatusCounts() {
      return OctaneGateSuiteRunChart.toStatusCounts(statusCounts, total);
    }
  }
}
