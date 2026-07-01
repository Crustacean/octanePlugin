package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.DefectCriteriaMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneRiskHeatMap;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneEmailBodyRendererTest {
  private static final String REPORT_URL =
      "https://jenkins.example/job/example/1/octaneSuiteGateReport/";
  private final OctaneEmailBodyRenderer renderer = new OctaneEmailBodyRenderer();
  private final StatusClassifier classifier =
      new StatusClassifier(
          StatusClassifier.DEFAULT_PASSED_STATUSES,
          StatusClassifier.DEFAULT_FAILED_STATUSES,
          StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
          StatusClassifier.DEFAULT_RUNNING_STATUSES);

  @Test
  public void rendersPassedVerdictAndOrderedCriteriaTable() {
    String html =
        renderer.render(
            "Attached report.", snapshot(OctaneGateReportState.PASSED, "Gate passed."), REPORT_URL);

    assertTrue(html.contains("Octane Criteria Verdict"));
    assertTrue(html.contains("color:#009900;\">PASS"));
    assertTrue(html.contains("<table"));
    assertTrue(html.contains("<th scope=\"col\""));
    assertTrue(html.contains("regressions.executionRate == 100%"));
    assertTrue(html.contains("regressions.passRate &gt;= 95%"));
    assertTrue(html.contains(">92.5%</td>"));
    assertTrue(html.contains(">OK</td>"));
    assertTrue(html.contains(">NOT OK</td>"));
    assertTrue(html.indexOf("regressions.executionRate") < html.indexOf("regressions.passRate"));
  }

  @Test
  public void mapsFailureAndNonEvaluatedStates() {
    String failed =
        renderer.render(
            "Report", snapshot(OctaneGateReportState.TIMED_OUT, "Timed out."), REPORT_URL);
    String unavailable =
        renderer.render(
            "Report",
            OctaneGateReportSnapshot.error("Polling error", "100% execution", "1", 30),
            REPORT_URL);

    assertTrue(failed.contains("color:#990000;\">FAIL"));
    assertTrue(failed.contains("Timed out"));
    assertTrue(unavailable.contains("NOT EVALUATED"));
    assertTrue(unavailable.contains("Detailed evaluation unavailable for this build."));
  }

  @Test
  public void escapesBodyAndIncludesReportLinkOnlyOnce() {
    String configuredBody = "Hello <script>alert('x')</script>\n" + REPORT_URL;

    String html =
        renderer.render(
            configuredBody, snapshot(OctaneGateReportState.PASSED, "Gate passed."), REPORT_URL);

    assertFalse(html.contains("<script>"));
    assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
    assertEquals(2, occurrences(html, REPORT_URL));
    assertEquals(1, occurrences(html, "href=\"" + REPORT_URL + "\""));
  }

  @Test
  public void fallsBackForLegacyResultWithoutDetailedEvaluation() {
    GateResult result =
        new GateResult(
            "1",
            "regressions.passRate == 100",
            true,
            true,
            new GateMetrics(1, 1, 1, 0, 0, 0),
            Map.of(),
            Instant.parse("2026-06-30T12:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result, classifier, 30);

    String html = renderer.render("Report", snapshot, REPORT_URL);

    assertTrue(html.contains("PASS"));
    assertTrue(html.contains("Detailed evaluation unavailable for this build."));
  }

  private OctaneGateReportSnapshot snapshot(OctaneGateReportState state, String message) {
    CriteriaEvaluation evaluation =
        CriteriaEvaluation.available(
            state == OctaneGateReportState.PASSED,
            List.of(
                new CriteriaComparisonEvaluation(
                    "regressions.executionRate", "==", 100, 100, true, true),
                new CriteriaComparisonEvaluation(
                    "regressions.passRate", ">=", 95, 92.5, true, false)));
    GateResult result =
        new GateResult(
            "1",
            "regressions.executionRate == 100 OR regressions.passRate >= 95",
            evaluation.isPassed(),
            true,
            new GateMetrics(10, 10, 9, 1, 0, 0),
            List.of(),
            Map.of(),
            Map.of(),
            OctaneRiskHeatMap.disabled(),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
            evaluation,
            Instant.parse("2026-06-30T12:00:00Z"));
    return OctaneGateReportSnapshot.fromResult(state, message, result, classifier, 30);
  }

  private int occurrences(String value, String expected) {
    int count = 0;
    int index = value.indexOf(expected);
    while (index >= 0) {
      count++;
      index = value.indexOf(expected, index + expected.length());
    }
    return count;
  }
}
