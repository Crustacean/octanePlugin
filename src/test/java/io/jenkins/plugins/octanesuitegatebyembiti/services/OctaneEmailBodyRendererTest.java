package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.DefectCriteriaMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
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

  @Test
  public void rendersProjectExecutionTablesAndCidScreenshotFromTemplate() {
    String template =
        """
        Hello Team,

        The automated job for {{PROJECT_NAME}} tests has run and is {{GATE_RESULT}}.

        Set criteria: {{CRITERIA}}

        Click here to {{REPORT_LINK}}.

        See below the execution details:

        {{EXECUTION_DETAILS}}

        {{REPORT_SCREENSHOT}}

        Thanks.
        QA Automation Team
        """;

    String html =
        renderer.render(
            template,
            "Business Payments Secure Checkout",
            "FS_TRIBE_DOMAIN",
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            REPORT_URL,
            "octane-report-zone.png");

    assertTrue(html.contains("Business Payments Secure Checkout"));
    assertTrue(html.contains("FS_TRIBE_DOMAIN"));
    assertTrue(html.contains("color:#009900;font-weight:700;\">SUCCESS"));
    assertTrue(html.contains(">view the report output</a>"));
    assertTrue(html.contains("Project Details"));
    assertTrue(html.contains("Test case execution"));
    assertTrue(html.contains("Total test cases"));
    assertTrue(html.contains("Execution rate"));
    assertTrue(html.contains("Pass Rate"));
    assertTrue(html.indexOf("Execution rate") < html.indexOf("Pass Rate"));
    assertTrue(html.contains(">90%</td>"));
    assertTrue(html.contains("Defect Distribution Matrix (Severity vs. Priority)"));
    assertTrue(html.contains("Defect Status Table (by Severity)"));
    assertTrue(html.contains(">Highest</th>"));
    assertTrue(html.contains(">Medium</th>"));
    assertTrue(html.contains(">Low</th>"));
    assertTrue(html.contains("aria-label=\"All severities, Highest priority: 5\""));
    assertTrue(html.contains("aria-label=\"Low severity, Medium priority: 2\""));
    String statusTable = emailTable(html, "defect-status");
    assertTrue(statusTable.contains(">Major</th>"));
    assertTrue(statusTable.contains(">Minor</th>"));
    assertFalse(statusTable.contains(">Low</th>"));
    assertFalse(statusTable.contains(">Critical</th>"));
    assertFalse(statusTable.contains(">Very High</th>"));
    assertFalse(statusTable.contains(">High</th>"));
    assertFalse(statusTable.contains(">Medium</th>"));
    assertFalse(statusTable.contains(">Unspecified</th>"));
    assertTrue(statusTable.contains("aria-label=\"Major, Open: 4\""));
    assertTrue(statusTable.contains("aria-label=\"Major, Closed: 1\""));
    assertTrue(statusTable.contains("aria-label=\"Major, Total: 5\""));
    assertTrue(statusTable.contains("aria-label=\"Minor, Open: 2\""));
    assertTrue(statusTable.contains("aria-label=\"Minor, Closed: 1\""));
    assertTrue(statusTable.contains("aria-label=\"Minor, Total: 3\""));
    assertTrue(statusTable.contains(">Total</th>"));
    assertFalse(statusTable.contains("Total Defects"));
    assertTrue(html.contains("Criteria evaluation"));
    assertTrue(html.indexOf("Project Details") < html.indexOf("Defect Distribution Matrix"));
    assertTrue(html.indexOf("Defect Distribution Matrix") < html.indexOf("Criteria evaluation"));
    assertTrue(html.contains("border:2px solid #374151;padding:16px"));
    int reportStart = html.indexOf("data-octane-email-section=\"execution-report\"");
    int screenshotStart = html.indexOf("src=\"cid:octane-report-zone.png\"");
    int signOffStart = html.indexOf("Thanks.");
    int reportEnd = html.lastIndexOf("</td></tr></table>", signOffStart);
    assertTrue(reportStart >= 0);
    assertTrue(reportStart < screenshotStart);
    assertTrue(screenshotStart < reportEnd);
    assertTrue(reportEnd < signOffStart);
    assertEquals(1, occurrences(html, "data-octane-email-section=\"execution-report\""));
    assertEquals(6, occurrences(html, "font-size:16px;font-weight:600;line-height:1.25"));
    assertTrue(html.contains("Execution graph</td>"));
    assertTrue(html.contains("font-size:15px;font-weight:600;line-height:1.4"));
    assertTrue(html.contains("font-size:15px;font-weight:400;line-height:1.4"));
    assertTrue(html.contains("padding:4px 8px"));
    assertTrue(html.contains("height:28px;line-height:1px;"));
    assertTrue(html.contains("table-layout:fixed"));
    assertTrue(html.contains("<colgroup>"));
    assertTrue(html.contains("src=\"cid:octane-report-zone.png\""));
    assertTrue(html.contains("gate execution report charts"));
    assertFalse(html.contains("position:absolute"));
  }

  @Test
  public void paintsPassRateValueCellForThemeAndCriteriaStatus() {
    String lightPass =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."), OctaneReportTheme.LIGHT);
    String lightFail =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.FAILED, "Gate failed."), OctaneReportTheme.LIGHT);
    String darkPass =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."), OctaneReportTheme.DARK);
    String darkFail =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.FAILED, "Gate failed."), OctaneReportTheme.DARK);
    String fallback =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.ERROR, "Gate error."), OctaneReportTheme.LIGHT);

    assertPassRateCell(lightPass, "#34C759", "#FFFFFF", true);
    assertPassRateCell(lightFail, "#FF3B30", "#FFFFFF", true);
    assertPassRateCell(darkPass, "#30D158", "#000000", true);
    assertPassRateCell(darkFail, "#FF453A", "#FFFFFF", true);
    assertPassRateCell(fallback, "transparent", "inherit", false);
  }

  @Test
  public void conditionallyRendersDefectGroupsBelowCriteria() {
    String template =
        """
        Set criteria: {{CRITERIA}}

        Click here to {{REPORT_LINK}}.
        """;
    OctaneGateReportSnapshot snapshot = snapshot(OctaneGateReportState.PASSED, "Gate passed.");

    String enabled =
        renderer.render(
            template,
            "Business Payments Secure Checkout",
            "FS_TRIBE_DOMAIN",
            snapshot,
            REPORT_URL,
            "octane-report-zone.png",
            OctaneReportTheme.LIGHT.name(),
            true);
    String disabled =
        renderer.render(
            template,
            "Business Payments Secure Checkout",
            "FS_TRIBE_DOMAIN",
            snapshot,
            REPORT_URL,
            "octane-report-zone.png",
            OctaneReportTheme.LIGHT.name(),
            false);

    assertTrue(enabled.contains("Defect groups: ( "));
    assertTrue(
        enabled.contains(
            "<strong>Major:</strong> Critical, Very High, High, Unspecified ; "
                + "<strong>Minor:</strong> Low, Medium"));
    assertTrue(enabled.indexOf("Set criteria:") < enabled.indexOf("Defect groups:"));
    assertTrue(enabled.indexOf("Defect groups:") < enabled.indexOf("view the report output"));
    assertFalse(disabled.contains("Defect groups:"));
    assertFalse(disabled.contains("margin:8px 0 0;\">Defect groups"));
  }

  @Test
  public void defectGroupsFallbackListsIndependentSeveritiesWhenNoGroupsAreConfigured() {
    String html =
        renderer.render(
            "Set criteria: {{CRITERIA}}",
            "Business Payments Secure Checkout",
            "FS_TRIBE_DOMAIN",
            OctaneGateReportSnapshot.error("Polling error", "100% execution", "1", 30),
            REPORT_URL,
            "octane-report-zone.png",
            OctaneReportTheme.LIGHT.name(),
            true);

    assertTrue(
        html.contains(
            "Defect groups: ( Critical ; Very High ; High ; Medium ; Low ; Unspecified )"));
  }

  @Test
  public void defectStatusTableRendersUngroupedSeveritiesAsStandaloneColumns() {
    String html =
        renderer.render(
            """
            {{EXECUTION_DETAILS}}
            {{REPORT_SCREENSHOT}}
            """,
            "Business Payments Secure Checkout",
            "FS_TRIBE_DOMAIN",
            snapshot(OctaneGateReportState.PASSED, "Gate passed.", "Medium"),
            REPORT_URL,
            "octane-report-zone.png");

    String statusTable = emailTable(html, "defect-status");

    assertTrue(statusTable.contains(">Major</th>"));
    assertTrue(statusTable.contains(">Minor</th>"));
    assertTrue(statusTable.contains(">Low</th>"));
    assertTrue(statusTable.contains("aria-label=\"Low, Open: 1\""));
    assertTrue(statusTable.contains("aria-label=\"Low, Closed: 1\""));
    assertTrue(statusTable.contains("aria-label=\"Low, Total: 2\""));
  }

  private String renderExecutionDetails(
      OctaneGateReportSnapshot snapshot, OctaneReportTheme theme) {
    return renderer.render(
        """
        {{EXECUTION_DETAILS}}
        {{REPORT_SCREENSHOT}}
        """,
        "Business Payments Secure Checkout",
        "FS_TRIBE_DOMAIN",
        snapshot,
        REPORT_URL,
        "octane-report-zone.png",
        theme.name());
  }

  private void assertPassRateCell(
      String html, String backgroundColor, String fontColor, boolean expectsBgcolor) {
    String row = passRateRow(html);
    int valueCellStart = row.indexOf("</th><td");
    assertTrue("Pass Rate value cell should follow label cell", valueCellStart >= 0);
    assertEquals(expectsBgcolor, row.indexOf("bgcolor=", valueCellStart) >= 0);
    if (expectsBgcolor) {
      assertTrue(row.contains("bgcolor=\"" + backgroundColor + "\""));
    }
    assertTrue(row.contains("background-color:" + backgroundColor + ";"));
    assertTrue(row.contains("color:" + fontColor + ";"));
    assertTrue(row.contains(">90%</td>"));
  }

  private String passRateRow(String html) {
    int label = html.indexOf("Pass Rate");
    assertTrue("Missing Pass Rate row", label >= 0);
    int start = html.lastIndexOf("<tr>", label);
    int end = html.indexOf("</tr>", label);
    assertTrue("Missing Pass Rate row bounds", start >= 0 && end > label);
    return html.substring(start, end + "</tr>".length());
  }

  private OctaneGateReportSnapshot snapshot(OctaneGateReportState state, String message) {
    return snapshot(state, message, "Low, Medium");
  }

  private OctaneGateReportSnapshot snapshot(
      OctaneGateReportState state, String message, String minorTypes) {
    CriteriaEvaluation evaluation =
        CriteriaEvaluation.available(
            state == OctaneGateReportState.PASSED,
            List.of(
                new CriteriaComparisonEvaluation(
                    "regressions.executionRate", "==", 100, 100, true, true),
                new CriteriaComparisonEvaluation(
                    "regressions.passRate", ">=", 95, 92.5, true, false)));
    OctaneDefectGroup major = defectGroup("major", "Critical, Very High, High, Unspecified");
    OctaneDefectGroup minor = defectGroup("minor", minorTypes);
    OctaneDefectSeveritySummary defectSummary =
        OctaneDefectSeveritySummary.fromDefects(
            List.of(
                defect("d1", "Critical", "opened"),
                defect("d2", "Very High", "opened"),
                defect("d3", "High", "opened"),
                defect("d4", "Medium", "opened"),
                defect("d5", "Low", "opened"),
                defect("d6", "", "opened"),
                defect("d7", "High", "closed"),
                defect("d8", "Low", "closed")));
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
            new DefectCriteriaMetrics(defectSummary, List.of(major, minor)),
            evaluation,
            Instant.parse("2026-06-30T12:00:00Z"));
    return OctaneGateReportSnapshot.fromResult(state, message, result, classifier, 30);
  }

  private OctaneDefectGroup defectGroup(String name, String types) {
    OctaneDefectGroup group = new OctaneDefectGroup(name);
    group.setTypes(types);
    return group;
  }

  private DefectRecord defect(String id, String severity, String phase) {
    return new DefectRecord(id, "Defect " + id, severity, "", phase, "run", "test", "", "");
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

  private String emailTable(String html, String tableName) {
    String marker = "data-octane-email-table=\"" + tableName + "\"";
    int start = html.indexOf(marker);
    assertTrue("Missing email table " + tableName, start >= 0);
    int tableStart = html.lastIndexOf("<table", start);
    int tableEnd = html.indexOf("</table>", start);
    assertTrue("Missing email table close for " + tableName, tableStart >= 0 && tableEnd > start);
    return html.substring(tableStart, tableEnd + "</table>".length());
  }
}
