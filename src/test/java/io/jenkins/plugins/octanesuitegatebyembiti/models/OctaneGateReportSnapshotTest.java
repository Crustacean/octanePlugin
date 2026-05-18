package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    assertEquals("#009900", OctaneGateStatusBucket.PASSED.getColor());
    assertEquals("#990000", OctaneGateStatusBucket.FAILED.getColor());
    assertEquals("#631919", OctaneGateStatusBucket.BLOCKED.getColor());
    assertEquals("#ffb74d", OctaneGateStatusBucket.SKIPPED.getColor());
    assertEquals("#808080", OctaneGateStatusBucket.RUNNING.getColor());
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

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.waiting(request, 17, "2026-05-15T00:00:00Z");

    assertEquals(17, snapshot.getRefreshSeconds());
    assertEquals(2700, snapshot.getTimeoutSeconds());
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
}
