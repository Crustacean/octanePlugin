package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneGateReportSnapshotTest {
  private final StatusClassifier classifier =
      new StatusClassifier(
          StatusClassifier.DEFAULT_PASSED_STATUSES,
          StatusClassifier.DEFAULT_FAILED_STATUSES,
          StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
          StatusClassifier.DEFAULT_RUNNING_STATUSES);

  @Test
  public void aggregatesRegressionPieTotalsAndSuiteRunBars() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result(), classifier, 30);

    OctaneGateReportSection regressions = snapshot.getSections().get(0);
    assertEquals("Regressions suite runs", regressions.getName());
    assertEquals("regressions", regressions.getSource());
    assertEquals(5, regressions.getMetrics().getTotal());
    assertEquals(2, count(regressions, OctaneGateStatusBucket.PASSED));
    assertEquals(1, count(regressions, OctaneGateStatusBucket.FAILED));
    assertEquals(0, count(regressions, OctaneGateStatusBucket.BLOCKED));
    assertEquals(1, count(regressions, OctaneGateStatusBucket.SKIPPED));
    assertEquals(1, count(regressions, OctaneGateStatusBucket.RUNNING));
    assertEquals(2, regressions.getSuiteRuns().size());
    assertEquals("Ada Tester", regressions.getSuiteRuns().get(0).getDisplayName());
    assertEquals(2, regressions.getSuiteRunCount());
    assertEquals(3, regressions.getSuiteRuns().get(0).getTotal());
    assertEquals("height: 100.00%;", regressions.getSuiteRuns().get(0).getBarHeightStyle());
    assertEquals("height: 66.67%;", regressions.getSuiteRuns().get(1).getBarHeightStyle());
    assertEquals(83.333, snapshot.getExecutionProgress(), 0.001);
    assertEquals("83%", snapshot.getExecutionProgressText());
    assertEquals(6, snapshot.getPassRateTotal());
    assertEquals(3, snapshot.getPassRatePassed());
    assertEquals(50.0, snapshot.getPassRateProgress(), 0.001);
    assertEquals("50%", snapshot.getPassRateProgressText());
    assertEquals("All Testcase Pass Rate (3 / 6)", snapshot.getPassRateLabel());
    assertFalse(regressions.getPieSlices().isEmpty());
  }

  @Test
  public void groupsSuiteRunBarsByRunByName() {
    Map<String, List<RunRecord>> suiteRuns = new LinkedHashMap<>();
    suiteRuns.put(
        "4501",
        List.of(
            new RunRecord("1", "one", "passed", "Alex Engineer"),
            new RunRecord("2", "two", "failed", "Alex Engineer")));
    suiteRuns.put("4502", List.of(new RunRecord("3", "three", "passed", "Alex Engineer")));
    GateResult result =
        new GateResult(
            "4501,4502",
            "100% execution",
            false,
            true,
            new GateMetrics(3, 3, 2, 1, 0, 0),
            List.of(
                new RunRecord("1", "one", "passed", "Alex Engineer"),
                new RunRecord("2", "two", "failed", "Alex Engineer"),
                new RunRecord("3", "three", "passed", "Alex Engineer")),
            suiteRuns,
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    OctaneGateReportSection regressions = snapshot.getSections().get(0);
    assertEquals(1, regressions.getSuiteRuns().size());
    assertEquals(2, regressions.getSuiteRunCount());
    assertEquals("Alex Engineer", regressions.getSuiteRuns().get(0).getDisplayName());
    assertEquals(List.of("4501", "4502"), regressions.getSuiteRuns().get(0).getSuiteRunIds());
    assertEquals(3, regressions.getSuiteRuns().get(0).getTotal());
    assertTrue(regressions.getSuiteRuns().get(0).getTitle().contains("4501, 4502"));
  }

  @Test
  public void donutPercentageLabelsFitInsideChartViewport() {
    Map<String, List<RunRecord>> suiteRuns = new LinkedHashMap<>();
    suiteRuns.put(
        "edge-labels",
        List.of(new RunRecord("1", "one", "passed"), new RunRecord("2", "two", "failed")));
    GateResult result =
        new GateResult(
            "edge-labels",
            "100% execution",
            false,
            true,
            new GateMetrics(2, 2, 1, 1, 0, 0),
            suiteRuns.get("edge-labels"),
            suiteRuns,
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    OctaneGateReportSection regressions = snapshot.getSections().get(0);
    assertEquals(2, regressions.getPieSlices().size());
    for (OctaneGatePieSlice slice : regressions.getPieSlices()) {
      double x = Double.parseDouble(slice.getLabelX());
      double y = Double.parseDouble(slice.getLabelY());
      assertTrue(
          "label text should stay inside donut viewport", x - 8.0 >= -10.0 && x + 8.0 <= 110.0);
      assertTrue(
          "label text should stay inside donut viewport", y - 4.0 >= -10.0 && y + 4.0 <= 110.0);
    }
  }

  @Test
  public void suiteRunAxisLabelsUseLowercaseEmailUserName() {
    OctaneGateSuiteRunChart emailChart =
        OctaneGateSuiteRunChart.fromRunByGroup(
            "Ada.Tester@Example.COM",
            List.of("4501"),
            List.of(new RunRecord("1", "one", "passed", "Ada.Tester@Example.COM")),
            classifier);
    OctaneGateSuiteRunChart nameChart =
        OctaneGateSuiteRunChart.fromRunByGroup(
            "Ada Tester",
            List.of("4502"),
            List.of(new RunRecord("2", "two", "passed", "Ada Tester")),
            classifier);

    assertEquals("ada.tester", emailChart.getAxisLabel());
    assertEquals("ada tester", nameChart.getAxisLabel());
  }

  @Test
  public void createsSeparateCriticalScopeSectionForOverlappingSuiteRuns() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result(), classifier, 30);

    OctaneGateReportSection critical = snapshot.getSections().get(1);
    assertEquals("Critical suite runs", critical.getName());
    assertEquals(List.of("4502", "4503"), critical.getSuiteRunIds());
    assertEquals(3, critical.getMetrics().getTotal());
    assertEquals(2, count(critical, OctaneGateStatusBucket.PASSED));
    assertEquals(1, count(critical, OctaneGateStatusBucket.FAILED));
  }

  @Test
  public void formatsLastUpdatedTimeInEastAfricaTimeWithoutMillis() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result(), classifier, 30);

    assertEquals("2026-05-15T00:00:00Z", snapshot.getUpdatedAt());
    assertEquals("03:00:00", snapshot.getUpdatedAtText());
  }

  @Test
  public void usesRequiredStatusColors() {
    assertEquals("var(--octane-status-passed)", OctaneGateStatusBucket.PASSED.getColor());
    assertEquals("var(--octane-status-failed)", OctaneGateStatusBucket.FAILED.getColor());
    assertEquals("var(--octane-status-blocked)", OctaneGateStatusBucket.BLOCKED.getColor());
    assertEquals("var(--octane-status-skipped)", OctaneGateStatusBucket.SKIPPED.getColor());
    assertEquals("var(--octane-status-no-run)", OctaneGateStatusBucket.RUNNING.getColor());
    assertEquals("#30D158", OctaneGateStatusBucket.PASSED.getTooltipColor());
    assertEquals("#FF453A", OctaneGateStatusBucket.FAILED.getTooltipColor());
    assertEquals("#FF9F0A", OctaneGateStatusBucket.BLOCKED.getTooltipColor());
    assertEquals("#64D2FF", OctaneGateStatusBucket.SKIPPED.getTooltipColor());
    assertEquals("#8E8E93", OctaneGateStatusBucket.RUNNING.getTooltipColor());
  }

  @Test
  public void suiteRunDominantStatusUsesLargestStatusCount() {
    OctaneGateSuiteRunChart chart =
        OctaneGateSuiteRunChart.fromRunByGroup(
            "Ada Tester",
            List.of("4501"),
            List.of(
                new RunRecord("1", "one", "passed", "Ada Tester"),
                new RunRecord("2", "two", "failed", "Ada Tester"),
                new RunRecord("3", "three", "failed", "Ada Tester")),
            classifier);

    assertEquals("Failed", chart.getDominantStatusLabel());
    assertEquals("#FF453A", chart.getDominantStatusColor());
    assertEquals(2, chart.getDominantStatusCount());
  }

  @Test
  public void suiteRunDominantStatusTieBreaksTowardRisk() {
    assertDominantStatusForTie("failed", "blocked", "Failed", "#FF453A");
    assertDominantStatusForTie("blocked", "planned", "Blocked", "#FF9F0A");
    assertDominantStatusForTie("planned", "skipped", "Skipped", "#64D2FF");
    assertDominantStatusForTie("skipped", "passed", "Passed", "#30D158");
  }

  @Test
  public void emptySuiteRunHasNoDominantStatus() {
    OctaneGateSuiteRunChart chart =
        OctaneGateSuiteRunChart.fromRunByGroup(
            "Empty Tester", List.of("empty"), List.of(), classifier);

    assertEquals("", chart.getDominantStatusLabel());
    assertEquals("", chart.getDominantStatusColor());
    assertEquals(0, chart.getDominantStatusCount());
  }

  @Test
  public void reportsBlockedStatusesSeparatelyFromFailedChartBucket() {
    Map<String, List<RunRecord>> suiteRuns = new LinkedHashMap<>();
    suiteRuns.put(
        "blocked-suite",
        List.of(
            new RunRecord("1", "one", "blocked"),
            new RunRecord("2", "two", "failed"),
            new RunRecord("3", "three", "passed")));
    GateResult result =
        new GateResult(
            "blocked-suite",
            "100% execution",
            false,
            true,
            new GateMetrics(3, 3, 1, 2, 0, 0),
            suiteRuns.get("blocked-suite"),
            suiteRuns,
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    OctaneGateReportSection regressions = snapshot.getSections().get(0);
    assertEquals(1, count(regressions, OctaneGateStatusBucket.PASSED));
    assertEquals(1, count(regressions, OctaneGateStatusBucket.FAILED));
    assertEquals(1, count(regressions, OctaneGateStatusBucket.BLOCKED));
    assertEquals(0, count(regressions, OctaneGateStatusBucket.SKIPPED));
    assertEquals(0, count(regressions, OctaneGateStatusBucket.RUNNING));
    assertEquals(1, suiteRunStatusCount(regressions, OctaneGateStatusBucket.BLOCKED));
  }

  @Test
  public void storesTimerConfigurationForReportCountdowns() {
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setPollIntervalSeconds(17);
    request.setTimeoutMinutes(45);
    request.setTimeoutMinutesExtended(12);

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.waiting(request, 17, "2026-05-15T00:00:00Z");

    assertEquals(17, snapshot.getRefreshSeconds());
    assertEquals(2700, snapshot.getTimeoutSeconds());
    assertEquals(720, snapshot.getTimeoutExtendedSeconds());
    assertEquals("2026-05-15T00:00:00Z", snapshot.getStartedAt());
  }

  @Test
  public void handlesZeroRunResults() {
    GateResult result =
        new GateResult(
            "empty",
            "100% pass",
            false,
            false,
            new GateMetrics(0, 0, 0, 0, 0, 0),
            List.of(),
            Map.of("empty", List.of()),
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    OctaneGateReportSection regressions = snapshot.getSections().get(0);
    assertTrue(regressions.isEmpty());
    assertEquals(0.0, snapshot.getExecutionProgress(), 0.001);
    assertEquals("0%", snapshot.getExecutionProgressText());
    assertEquals(0, snapshot.getPassRateTotal());
    assertEquals(0, snapshot.getPassRatePassed());
    assertEquals(0.0, snapshot.getPassRateProgress(), 0.001);
    assertEquals("0%", snapshot.getPassRateProgressText());
    assertEquals("All Testcase Pass Rate (0 / 0)", snapshot.getPassRateLabel());
    assertTrue(regressions.getPieSlices().isEmpty());
    assertTrue(snapshot.getReportSections().isEmpty());
    assertFalse(snapshot.hasReportSections());
    assertEquals(1, regressions.getSuiteRuns().size());
    assertEquals(0, regressions.getSuiteRuns().get(0).getTotal());
    assertEquals("height: 0%;", regressions.getSuiteRuns().get(0).getBarHeightStyle());
  }

  @Test
  public void calculatesTestMetricsFromCurrentSnapshotAndPreviousCycle() {
    OctaneGateReportSnapshot previous =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED,
            "Previous",
            result(),
            classifier,
            30,
            3600,
            "2026-05-14T23:45:00Z");
    OctaneGateReportSnapshot current =
        OctaneGateReportSnapshot.fromResult(
                OctaneGateReportState.PASSED,
                "Current",
                resultWithRiskHeatMap(),
                classifier,
                30,
                3600,
                "2026-05-14T23:50:00Z")
            .withCalculatedTestMetrics(previous);

    assertEquals("2m 0s", metric(current, "avg-time").getValue());
    assertEquals("5 executed tests", metric(current, "avg-time").getDetail());
    assertEquals("-1m 0s from last cycle", metric(current, "avg-time").getTrendText());
    assertEquals("positive", metric(current, "avg-time").getTrendTone());
    assertEquals("50.0%", metric(current, "success-rate").getValue());
    assertEquals("3 / 6 passed", metric(current, "success-rate").getDetail());
    assertEquals("83.3%", metric(current, "execution").getValue());
    assertEquals("5 / 6 executed", metric(current, "execution").getDetail());
    assertEquals("3 open", metric(current, "defects").getValue());
    assertEquals("50.0 per 100 tests", metric(current, "defects").getDetail());
  }

  @Test
  public void testMetricsHandleZeroExecutedAndMissingRiskMap() {
    GateResult result =
        new GateResult(
            "empty",
            "100% pass",
            false,
            false,
            new GateMetrics(0, 0, 0, 0, 0, 0),
            List.of(),
            Map.of("empty", List.of()),
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    assertEquals("N/A", metric(snapshot, "avg-time").getValue());
    assertEquals("Awaiting executed tests", metric(snapshot, "avg-time").getTrendText());
    assertEquals("0.0%", metric(snapshot, "success-rate").getValue());
    assertEquals("0.0%", metric(snapshot, "execution").getValue());
    assertEquals("N/A", metric(snapshot, "defects").getValue());
    assertEquals("Risk heat map unavailable", metric(snapshot, "defects").getDetail());
  }

  @Test
  public void reportSectionsHideEmptySectionsButKeepValidData() {
    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put(
        "4502",
        List.of(
            new RunRecord("1", "critical one", "passed"),
            new RunRecord("2", "critical two", "passed")));
    GateResult result =
        new GateResult(
            "",
            "critical.passRate == 100",
            true,
            true,
            new GateMetrics(0, 0, 0, 0, 0, 0),
            List.of(),
            Map.of(),
            Map.of(
                "critical",
                new GateScopeResult(
                    "critical",
                    "",
                    List.of(),
                    "4502",
                    List.of("4502"),
                    new GateMetrics(2, 2, 2, 0, 0, 0),
                    criticalSuiteRuns.get("4502"),
                    criticalSuiteRuns)),
            Instant.parse("2026-05-15T00:00:00Z"));

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result, classifier, 30);

    assertEquals(2, snapshot.getSections().size());
    assertEquals(1, snapshot.getReportSections().size());
    assertEquals("critical", snapshot.getReportSections().get(0).getSource());
    assertTrue(snapshot.hasReportSections());
  }

  private int count(OctaneGateReportSection section, OctaneGateStatusBucket bucket) {
    return section.getTotals().stream()
        .filter(status -> status.getBucket() == bucket)
        .findFirst()
        .orElseThrow()
        .getCount();
  }

  private int suiteRunStatusCount(OctaneGateReportSection section, OctaneGateStatusBucket bucket) {
    return section.getSuiteRuns().get(0).getStatuses().stream()
        .filter(status -> status.getBucket() == bucket)
        .findFirst()
        .orElseThrow()
        .getCount();
  }

  private OctaneTestMetricCard metric(OctaneGateReportSnapshot snapshot, String key) {
    return snapshot.getTestMetrics().getCards().stream()
        .filter(card -> key.equals(card.getKey()))
        .findFirst()
        .orElseThrow();
  }

  private void assertDominantStatusForTie(
      String firstStatus, String secondStatus, String expectedLabel, String expectedColor) {
    OctaneGateSuiteRunChart chart =
        OctaneGateSuiteRunChart.fromRunByGroup(
            "Tie Tester",
            List.of("4501"),
            List.of(
                new RunRecord("1", "one", firstStatus, "Tie Tester"),
                new RunRecord("2", "two", secondStatus, "Tie Tester")),
            classifier);

    assertEquals(expectedLabel, chart.getDominantStatusLabel());
    assertEquals(expectedColor, chart.getDominantStatusColor());
    assertEquals(1, chart.getDominantStatusCount());
  }

  private GateResult result() {
    Map<String, List<RunRecord>> regressionSuiteRuns = new LinkedHashMap<>();
    regressionSuiteRuns.put(
        "4501",
        List.of(
            new RunRecord("1", "one", "passed", "Ada Tester"),
            new RunRecord("2", "two", "failed", "Ada Tester"),
            new RunRecord("3", "three", "planned", "Ada Tester")));
    regressionSuiteRuns.put(
        "4502",
        List.of(
            new RunRecord("4", "four", "passed", "Ben Tester"),
            new RunRecord("5", "five", "skipped", "Ben Tester")));

    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put(
        "4502",
        List.of(
            new RunRecord("4", "four", "passed", "Ben Tester"),
            new RunRecord("5", "five", "failed", "Ben Tester")));
    criticalSuiteRuns.put("4503", List.of(new RunRecord("6", "six", "passed", "Cara Tester")));

    return new GateResult(
        "4501,4502",
        "critical.passRate == 100",
        false,
        false,
        new GateMetrics(5, 4, 2, 1, 1, 1),
        List.of(
            new RunRecord("1", "one", "passed", "Ada Tester"),
            new RunRecord("2", "two", "failed", "Ada Tester"),
            new RunRecord("3", "three", "planned", "Ada Tester"),
            new RunRecord("4", "four", "passed", "Ben Tester"),
            new RunRecord("5", "five", "skipped", "Ben Tester")),
        regressionSuiteRuns,
        Map.of(
            "critical",
            new GateScopeResult(
                "critical",
                "",
                List.of(),
                "4502,4503",
                List.of("4502", "4503"),
                new GateMetrics(3, 3, 2, 1, 0, 0),
                List.of(
                    new RunRecord("4", "four", "passed", "Ben Tester"),
                    new RunRecord("5", "five", "failed", "Ben Tester"),
                    new RunRecord("6", "six", "passed", "Cara Tester")),
                criticalSuiteRuns)),
        Instant.parse("2026-05-15T00:00:00Z"));
  }

  private GateResult resultWithRiskHeatMap() {
    GateResult base = result();
    List<DefectRecord> defects =
        List.of(
            new DefectRecord(
                "d1", "Critical defect", "critical", "", "new", "1", "1", "p1", "Project"),
            new DefectRecord("d2", "High defect", "high", "", "opened", "2", "2", "p1", "Project"),
            new DefectRecord("d3", "Missing severity", "", "", "opened", "3", "3", "p1", "Project"),
            new DefectRecord(
                "d4", "Closed defect", "low", "", "closed", "4", "4", "p1", "Project"));
    OctaneRiskHeatMap riskHeatMap =
        OctaneRiskHeatMap.of(
            new OctaneRiskHeatMapNode("project", "Project", 80, 6, 4, List.of()),
            4,
            4,
            0,
            1,
            OctaneDefectSeveritySummary.fromDefects(defects));
    return new GateResult(
        base.getSuiteRunId(),
        base.getCriteria(),
        base.isPassed(),
        base.isTerminal(),
        base.getMetrics(),
        base.getRuns(),
        base.getSuiteRuns(),
        base.getScopedResults(),
        riskHeatMap,
        base.getPolledAt());
  }
}
