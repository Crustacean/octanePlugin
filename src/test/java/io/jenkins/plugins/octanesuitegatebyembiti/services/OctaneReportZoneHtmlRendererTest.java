package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneReportZoneHtmlRendererTest {
  private final StatusClassifier classifier =
      new StatusClassifier(
          StatusClassifier.DEFAULT_PASSED_STATUSES,
          StatusClassifier.DEFAULT_FAILED_STATUSES,
          StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
          StatusClassifier.DEFAULT_RUNNING_STATUSES);

  @Test
  public void rendersOnlyReportZoneForEmailScreenshot() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result(), classifier, 30);

    String html = new OctaneReportZoneHtmlRenderer().render(snapshot);

    assertTrue(html.contains("id=\"octane-report-zone\""));
    assertTrue(html.contains("REGRESSIONS Status Distribution"));
    assertTrue(html.contains("CRITICAL Distribution_CRITICAL"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_CRITICAL"));
    assertTrue(html.contains("Total: 3"));
    assertTrue(html.contains("Total Suiteruns: 2"));
    assertFalse(html.contains("Total Testcases"));
    assertTrue(html.contains("border: 1px solid #f5f7fb"));
    assertTrue(html.contains("#009900"));
    assertTrue(html.contains("#990000"));
    assertTrue(html.contains("#808080"));
    assertFalse(html.contains("#631919"));
    assertTrue(html.contains("octane-donut"));
    assertTrue(html.contains("octane-distribution-meta"));
    assertTrue(html.contains("octane-total-label"));
    assertTrue(html.contains("octane-donut-label"));
    assertTrue(html.contains("viewBox=\"-10 -10 120 120\""));
    assertTrue(html.contains("max-width: 280px"));
    assertTrue(html.contains("overflow: visible"));
    assertFalse(html.contains("octane-legend-value"));
    assertTrue(html.contains("octane-vertical-bars"));
    assertFalse(html.contains("id=\"octane-timer-zone\""));
    assertFalse(html.contains("Testing Time Remaining"));
    assertFalse(html.contains("Status Check"));
    assertFalse(html.contains("Time to next Poll"));
    assertFalse(html.contains("Execution Progress"));
  }

  @Test
  public void rendersPartialReportZoneForLiveRefresh() {
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result(), classifier, 30);

    String html = new OctaneReportZoneHtmlRenderer().renderZone(snapshot);

    assertTrue(html.contains("id=\"octane-report-zone\""));
    assertTrue(html.contains("draggable=\"true\""));
    assertTrue(html.contains("REGRESSIONS Status Distribution"));
    assertFalse(html.contains("<html>"));
    assertFalse(html.contains("<body>"));
    assertFalse(html.contains("id=\"octane-timer-zone\""));
  }

  @Test
  public void escapesDynamicReportValues() {
    GateResult result =
        new GateResult(
            "4501",
            "100% pass",
            true,
            true,
            new GateMetrics(1, 1, 1, 0, 0, 0),
            List.of(new RunRecord("1", "one", "passed")),
            Map.of("<4501>", List.of(new RunRecord("1", "one", "passed"))),
            Map.of(),
            Instant.parse("2026-05-15T00:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result, classifier, 30);

    String html = new OctaneReportZoneHtmlRenderer().render(snapshot);

    assertTrue(html.contains("&lt;4501&gt;"));
    assertFalse(html.contains("><4501><"));
  }

  @Test
  public void skipsEmptySectionsInReportZone() {
    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put("4502", List.of(new RunRecord("1", "critical one", "passed")));
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
                    new GateMetrics(1, 1, 1, 0, 0, 0),
                    criticalSuiteRuns.get("4502"),
                    criticalSuiteRuns)),
            Instant.parse("2026-05-15T00:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result, classifier, 30);

    String html = new OctaneReportZoneHtmlRenderer().renderZone(snapshot);

    assertFalse(html.contains("REGRESSIONS Status Distribution</h2>"));
    assertFalse(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS</h2>"));
    assertTrue(html.contains("CRITICAL Distribution_CRITICAL"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_CRITICAL"));
    assertFalse(html.contains("No run results have been returned yet."));
  }

  @Test
  public void skipsEmptyCriticalSectionInReportZone() {
    Map<String, List<RunRecord>> regressionSuiteRuns = new LinkedHashMap<>();
    regressionSuiteRuns.put("4501", List.of(new RunRecord("1", "one", "passed")));
    GateResult result =
        new GateResult(
            "4501",
            "100% pass",
            true,
            true,
            new GateMetrics(1, 1, 1, 0, 0, 0),
            regressionSuiteRuns.get("4501"),
            regressionSuiteRuns,
            Map.of(
                "critical",
                new GateScopeResult(
                    "critical",
                    "",
                    List.of(),
                    "9999",
                    List.of("9999"),
                    new GateMetrics(0, 0, 0, 0, 0, 0),
                    List.of(),
                    Map.of("9999", List.of()))),
            Instant.parse("2026-05-15T00:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result, classifier, 30);

    String html = new OctaneReportZoneHtmlRenderer().renderZone(snapshot);

    assertTrue(html.contains("REGRESSIONS Status Distribution</h2>"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS</h2>"));
    assertFalse(html.contains("CRITICAL Distribution_CRITICAL"));
    assertFalse(html.contains("Testing progress per Tester Suite Runs_CRITICAL"));
    assertFalse(html.contains("No run results have been returned yet."));
  }

  private GateResult result() {
    Map<String, List<RunRecord>> regressionSuiteRuns = new LinkedHashMap<>();
    regressionSuiteRuns.put(
        "4501", List.of(new RunRecord("1", "one", "passed"), new RunRecord("2", "two", "failed")));
    regressionSuiteRuns.put("4502", List.of(new RunRecord("3", "three", "planned")));

    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put("4502", List.of(new RunRecord("3", "three", "passed")));

    return new GateResult(
        "4501,4502",
        "critical.passRate == 100",
        true,
        true,
        new GateMetrics(3, 2, 1, 1, 0, 1),
        List.of(
            new RunRecord("1", "one", "passed"),
            new RunRecord("2", "two", "failed"),
            new RunRecord("3", "three", "planned")),
        regressionSuiteRuns,
        Map.of(
            "critical",
            new GateScopeResult(
                "critical",
                "",
                List.of(),
                "4502",
                List.of("4502"),
                new GateMetrics(1, 1, 1, 0, 0, 0),
                List.of(new RunRecord("3", "three", "passed")),
                criticalSuiteRuns)),
        Instant.parse("2026-05-15T00:00:00Z"));
  }
}
