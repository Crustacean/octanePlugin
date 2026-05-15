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
  public void aggregatesGlobalPieTotalsAndSuiteRunBars() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.POLLING, "Polling", result(), classifier, 30);

    OctaneGateReportSection global = snapshot.getSections().get(0);
    assertEquals("Global suite runs", global.getName());
    assertEquals(5, global.getMetrics().getTotal());
    assertEquals(2, count(global, OctaneGateStatusBucket.PASSED));
    assertEquals(1, count(global, OctaneGateStatusBucket.FAILED));
    assertEquals(1, count(global, OctaneGateStatusBucket.SKIPPED));
    assertEquals(1, count(global, OctaneGateStatusBucket.RUNNING));
    assertEquals(2, global.getSuiteRuns().size());
    assertEquals("4501", global.getSuiteRuns().get(0).getSuiteRunId());
    assertEquals(2, global.getSuiteRunCount());
    assertEquals(3, global.getSuiteRuns().get(0).getTotal());
    assertEquals("height: 100.00%;", global.getSuiteRuns().get(0).getBarHeightStyle());
    assertEquals("height: 66.67%;", global.getSuiteRuns().get(1).getBarHeightStyle());
    assertEquals(87.5, snapshot.getExecutionProgress(), 0.001);
    assertEquals("88%", snapshot.getExecutionProgressText());
    assertFalse(global.getPieSlices().isEmpty());
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
  public void usesRequiredStatusColors() {
    assertEquals("#78c679", OctaneGateStatusBucket.PASSED.getColor());
    assertEquals("#ff6361", OctaneGateStatusBucket.FAILED.getColor());
    assertEquals("#ffb74d", OctaneGateStatusBucket.SKIPPED.getColor());
    assertEquals("#778899", OctaneGateStatusBucket.RUNNING.getColor());
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

    OctaneGateReportSection global = snapshot.getSections().get(0);
    assertTrue(global.isEmpty());
    assertEquals(0.0, snapshot.getExecutionProgress(), 0.001);
    assertEquals("0%", snapshot.getExecutionProgressText());
    assertTrue(global.getPieSlices().isEmpty());
    assertEquals(1, global.getSuiteRuns().size());
    assertEquals(0, global.getSuiteRuns().get(0).getTotal());
    assertEquals("height: 0%;", global.getSuiteRuns().get(0).getBarHeightStyle());
  }

  private int count(OctaneGateReportSection section, OctaneGateStatusBucket bucket) {
    return section.getTotals().stream()
        .filter(status -> status.getBucket() == bucket)
        .findFirst()
        .orElseThrow()
        .getCount();
  }

  private GateResult result() {
    Map<String, List<RunRecord>> globalSuiteRuns = new LinkedHashMap<>();
    globalSuiteRuns.put(
        "4501",
        List.of(
            new RunRecord("1", "one", "passed"),
            new RunRecord("2", "two", "failed"),
            new RunRecord("3", "three", "planned")));
    globalSuiteRuns.put(
        "4502",
        List.of(new RunRecord("4", "four", "passed"), new RunRecord("5", "five", "skipped")));

    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put(
        "4502",
        List.of(new RunRecord("4", "four", "passed"), new RunRecord("5", "five", "failed")));
    criticalSuiteRuns.put("4503", List.of(new RunRecord("6", "six", "passed")));

    return new GateResult(
        "4501,4502",
        "critical.passRate == 100",
        false,
        false,
        new GateMetrics(5, 4, 2, 1, 1, 1),
        List.of(
            new RunRecord("1", "one", "passed"),
            new RunRecord("2", "two", "failed"),
            new RunRecord("3", "three", "planned"),
            new RunRecord("4", "four", "passed"),
            new RunRecord("5", "five", "skipped")),
        globalSuiteRuns,
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
                    new RunRecord("4", "four", "passed"),
                    new RunRecord("5", "five", "failed"),
                    new RunRecord("6", "six", "passed")),
                criticalSuiteRuns)),
        Instant.parse("2026-05-15T00:00:00Z"));
  }
}
