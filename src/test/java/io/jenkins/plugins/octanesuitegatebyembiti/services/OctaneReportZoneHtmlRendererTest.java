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
    assertTrue(html.contains("REGRESSION Tests Status Distribution"));
    assertTrue(html.contains("CRITICAL Tests Status Distribution"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_CRITICAL"));
    assertTrue(html.contains("Total: 3"));
    assertTrue(html.contains("Total Suiteruns: 2"));
    assertTrue(html.contains("Ada Tester"));
    assertTrue(html.contains("ada tester"));
    assertTrue(html.contains("Ben Tester"));
    assertTrue(html.contains("ben tester"));
    assertTrue(html.contains("suite runs: 4501"));
    assertFalse(html.contains("Total Testcases"));
    assertTrue(html.contains("border: 1px solid #f5f7fb"));
    assertTrue(html.contains("font-size: 11px"));
    assertTrue(html.contains("letter-spacing: 2px"));
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
    assertFalse(html.contains("min-height: 294px"));
    assertTrue(html.contains("overflow: visible"));
    assertTrue(html.contains("r=\"46\" fill="));
    assertTrue(html.contains("r=\"30\""));
    assertFalse(html.contains("octane-legend-value"));
    assertTrue(html.contains("octane-suite-chart-meta"));
    assertTrue(html.contains("octane-bar-graph"));
    assertTrue(html.contains("octane-y-axis-label"));
    assertTrue(html.contains(">Test Runs<"));
    assertTrue(html.contains("#576779"));
    assertFalse(html.contains("#CCCCCC"));
    assertTrue(html.contains("column-gap: 1px"));
    assertTrue(html.contains("grid-template-columns: 22px max-content minmax(0, 1fr)"));
    assertTrue(html.contains("grid-template-rows: 260px 27px"));
    assertTrue(html.contains("octane-bar-plot"));
    assertTrue(html.contains("octane-vertical-bars"));
    assertTrue(html.contains("octane-x-axis-labels"));
    assertTrue(html.contains("octane-axis-label-column"));
    assertTrue(html.contains("overflow-x: hidden"));
    assertTrue(html.contains("flex: 1 1 90px"));
    assertTrue(html.contains("width: clamp(14px, 62%, 42px)"));
    assertTrue(html.contains("font-size: clamp(9px, 0.8vw, 11px)"));
    assertTrue(html.contains("text-align: center"));
    assertTrue(html.contains("transform: none"));
    assertFalse(html.contains("rotate(-45deg)"));
    assertTrue(html.contains("octane-bar-popup"));
    assertTrue(html.contains("min-width: 175px"));
    assertTrue(html.contains("font-size: 9px"));
    assertTrue(html.contains("overflow-wrap: anywhere"));
    assertTrue(html.contains("octane-bar-popup-name"));
    assertTrue(html.contains("octane-bar-popup-row"));
    assertTrue(html.contains("octane-bar-popup-total"));
    assertFalse(html.contains("class=\"octane-total\""));
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
    assertTrue(html.contains("REGRESSION Tests Status Distribution"));
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

    assertFalse(html.contains("REGRESSION Tests Status Distribution</h2>"));
    assertFalse(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS</h2>"));
    assertTrue(html.contains("CRITICAL Tests Status Distribution"));
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

    assertTrue(html.contains("REGRESSION Tests Status Distribution</h2>"));
    assertTrue(html.contains("Testing progress per Tester Suite Runs_REGRESSIONS</h2>"));
    assertFalse(html.contains("CRITICAL Tests Status Distribution"));
    assertFalse(html.contains("Testing progress per Tester Suite Runs_CRITICAL"));
    assertFalse(html.contains("No run results have been returned yet."));
  }

  private GateResult result() {
    Map<String, List<RunRecord>> regressionSuiteRuns = new LinkedHashMap<>();
    regressionSuiteRuns.put(
        "4501",
        List.of(
            new RunRecord("1", "one", "passed", "Ada Tester"),
            new RunRecord("2", "two", "failed", "Ada Tester")));
    regressionSuiteRuns.put("4502", List.of(new RunRecord("3", "three", "planned", "Ben Tester")));

    Map<String, List<RunRecord>> criticalSuiteRuns = new LinkedHashMap<>();
    criticalSuiteRuns.put("4502", List.of(new RunRecord("3", "three", "passed", "Ben Tester")));

    return new GateResult(
        "4501,4502",
        "critical.passRate == 100",
        true,
        true,
        new GateMetrics(3, 2, 1, 1, 0, 1),
        List.of(
            new RunRecord("1", "one", "passed", "Ada Tester"),
            new RunRecord("2", "two", "failed", "Ada Tester"),
            new RunRecord("3", "three", "planned", "Ben Tester")),
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
                List.of(new RunRecord("3", "three", "passed", "Ben Tester")),
                criticalSuiteRuns)),
        Instant.parse("2026-05-15T00:00:00Z"));
  }
}
