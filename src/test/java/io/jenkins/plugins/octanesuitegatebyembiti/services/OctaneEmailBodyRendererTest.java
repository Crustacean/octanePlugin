package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.DefectCriteriaMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefinedScope;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneRiskHeatMap;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import java.time.Instant;
import java.util.ArrayList;
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
  public void omitsBypassedRegressionRulesFromPrintedCriteriaAndEvaluationTable() {
    String criteria = "regressions.executionRate == 100 AND critical.executionRate == 100";
    GateMetrics criticalMetrics = new GateMetrics(2, 2, 2, 0, 0, 0);
    MetricsContext metricsContext =
        new MetricsContext(new GateMetrics(0, 0, 0, 0, 0, 0), Map.of("critical", criticalMetrics));
    CriteriaExpression expression = CriteriaExpression.parse(criteria);
    CriteriaEvaluation evaluation = expression.evaluateDetailed(metricsContext, false);
    GateResult result =
        new GateResult(
            "",
            expression.effectiveExpression(metricsContext, false),
            true,
            true,
            new GateMetrics(0, 0, 0, 0, 0, 0),
            List.of(),
            Map.of(),
            Map.of(),
            OctaneRiskHeatMap.disabled(),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
            evaluation,
            Instant.parse("2026-06-30T12:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Passed", result, classifier, 30);

    String html = renderer.render("Report", snapshot, REPORT_URL);
    int tableStart = html.indexOf("data-octane-email-table=\"criteria-evaluation\"");
    int tableEnd = html.indexOf("</table>", tableStart);
    String evaluationTable = html.substring(tableStart, tableEnd);

    assertTrue(evaluationTable.contains("critical.executionRate == 100%"));
    assertFalse(evaluationTable.contains("regressions.executionRate"));
    assertTrue(html.contains("Criteria:</strong> <code"));
    int criteriaStart = html.indexOf("Criteria:</strong> <code");
    int criteriaEnd = html.indexOf("</code>", criteriaStart);
    String appliedCriteria = html.substring(criteriaStart, criteriaEnd);
    assertTrue(appliedCriteria.contains("critical.executionRate == 100"));
    assertFalse(appliedCriteria.contains("critical.executionRate == 100%"));
    assertFalse(html.contains("regressions.executionRate"));
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

        The automated job for {{PROJECT_NAME}} tests has run for {{DURATION}}, has an execution rate of {{EXECUTIONRATE}} and a pass rate of {{PASSRATE}}, and is {{GATE_RESULT}}.

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
    assertTrue(
        html.contains(
            "tests has run for 0 seconds, has an execution rate of 100.00% and a pass rate of 90.00%, and is"));
    assertFalse(html.contains("{{DURATION}}"));
    assertFalse(html.contains("{{EXECUTIONRATE}}"));
    assertFalse(html.contains("{{PASSRATE}}"));
    assertTrue(html.contains("FS_TRIBE_DOMAIN"));
    assertTrue(html.contains("color:#009900;font-weight:700;\">SUCCESS"));
    assertFalse(html.contains("Set criteria:"));
    assertTrue(
        html.contains(
            "To get a green light for release, the build must pass 1 non-negotiable QA checks:"));
    assertTrue(
        html.contains(
            "<li>Regression tests completed must be exactly 100% OR Regression test pass rate "
                + "must be at least 95</li>"));
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
    assertTrue(emailTable(html, "defect-distribution").contains(">Total (8)</th>"));
    assertTrue(statusTable.contains(">Open (6)</th>"));
    assertTrue(statusTable.contains(">Closed (2)</th>"));
    assertTrue(statusTable.contains(">Total (8)</th>"));
    assertFalse(statusTable.contains("Total Defects"));
    assertTrue(html.contains("Criteria evaluation"));
    String criteriaTable = emailTable(html, "criteria-evaluation");
    assertTrue(criteriaTable.contains("regressions.executionRate == 100%"));
    assertTrue(criteriaTable.contains("regressions.passRate &gt;= 95%"));
    assertTrue(html.contains("Defect Logging Compliance"));
    assertTrue(html.contains("data-octane-email-section=\"criteria-reconciliation\""));
    assertTrue(html.contains("data-octane-email-table=\"defect-reconciliation\""));
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
    assertEquals(7, occurrences(html, "font-size:16px;font-weight:600;line-height:1.25"));
    assertTrue(html.contains("Execution graph</td>"));
    assertTrue(html.contains("font-size:15px;font-weight:600;line-height:1.4"));
    assertTrue(html.contains("font-size:15px;font-weight:400;line-height:1.4"));
    assertTrue(html.contains("padding:4px 8px"));
    assertTrue(html.contains("height:28px;line-height:1px;"));
    assertFalse(html.contains("height:12px;line-height:1px;"));
    assertFalse(html.contains("height:24px;line-height:1px;"));
    assertTrue(html.contains("border-collapse:collapse;margin:0;width:100%"));
    assertTrue(html.contains("font-size:0;line-height:0;padding:0;\"><img"));
    assertTrue(html.contains("margin:0;max-width:100%;padding:0;width:100%"));
    assertTrue(html.contains("table-layout:fixed"));
    assertTrue(html.contains("<colgroup>"));
    assertTrue(html.contains("src=\"cid:octane-report-zone.png\""));
    assertTrue(html.contains("gate execution report charts"));
    assertFalse(html.contains("position:absolute"));
  }

  @Test
  public void rendersEscapedProjectSummaryBulletsImmediatelyBeforeCriteria() {
    String html =
        renderCustomizedEmail(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            "*NoSpaceText\n** Child <value>\n***Third level\n**** Fourth level\n*****Fifth level",
            true,
            false,
            "",
            0);

    assertTrue(html.contains("data-octane-email-section=\"project-summary\""));
    assertTrue(html.contains("<li>NoSpaceText"));
    assertTrue(html.contains("list-style-type:disc;padding-left:15px"));
    assertTrue(html.contains("list-style-type:circle;padding-left:15px"));
    assertTrue(html.contains("list-style-type:square;padding-left:15px"));
    assertTrue(html.contains("<li>Child &lt;value&gt;"));
    assertTrue(html.contains("<li>Third level"));
    assertTrue(html.contains("<li>Fourth level"));
    assertTrue(html.contains("<li>Fifth level</li>"));
    assertEquals(2, occurrences(html, "list-style-type:disc;padding-left:15px"));
    assertEquals(2, occurrences(html, "list-style-type:circle;padding-left:15px"));
    assertTrue(
        html.indexOf("data-octane-email-section=\"project-summary\"")
            < html.indexOf("To get a green light for release"));

    String singleBullet =
        renderCustomizedEmail(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            "*NoSpaceText",
            true,
            false,
            "",
            0);
    assertTrue(singleBullet.contains("<li>NoSpaceText</li>"));
  }

  @Test
  public void rendersInlineMarkdownAfterTemplateVariableInterpolation() {
    String template =
        "**bold word**\n_italic word_\n_**bold italic word**_\n**{{PROJECT_NAME}}**\n"
            + "**first phrase** and **second phrase**";

    String html =
        renderer.render(
            template,
            "Global Pay",
            "Domain",
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            REPORT_URL,
            "octane-report-zone.png");

    assertTrue(html.contains("<strong>bold word</strong>"));
    assertTrue(html.contains("<em>italic word</em>"));
    assertTrue(html.contains("<strong><em>bold italic word</em></strong>"));
    assertTrue(html.contains("<strong>Global Pay</strong>"));
    assertTrue(html.contains("<strong>first phrase</strong> and <strong>second phrase</strong>"));
  }

  @Test
  public void rendersSummaryIndentationBeforeInlineMarkdown() {
    String html =
        renderCustomizedEmail(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            "*make money **or building dormitory**",
            true,
            false,
            "",
            0);

    assertTrue(html.contains("<li>make money <strong>or building dormitory</strong></li>"));
    assertFalse(html.contains("<strong>*make money"));
  }

  @Test
  public void omitsProjectSummaryWhenEmptyOrDisabled() {
    OctaneGateReportSnapshot snapshot = snapshot(OctaneGateReportState.PASSED, "Gate passed.");

    assertFalse(
        renderCustomizedEmail(snapshot, "*Hidden", false, false, "", 0)
            .contains("data-octane-email-section=\"project-summary\""));
    assertFalse(
        renderCustomizedEmail(snapshot, "  ", true, false, "", 0)
            .contains("data-octane-email-section=\"project-summary\""));
  }

  @Test
  public void filtersSortsAndLimitsDefectsBelowExecutionScreenshot() {
    String html =
        renderCustomizedEmail(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            "",
            false,
            true,
            "major, Closed",
            3);
    String table = emailTable(html, "defects");

    assertTrue(table.contains(">ID</th>"));
    assertTrue(table.contains(">Name</th>"));
    assertTrue(table.contains(">Severity</th>"));
    assertTrue(table.contains(">Status</th>"));
    assertTrue(table.contains(">1</td>"));
    assertTrue(table.contains(">2</td>"));
    assertTrue(table.contains(">3</td>"));
    assertFalse(table.contains(">4</td>"));
    assertTrue(table.contains(">Major</td>"));
    assertTrue(table.indexOf(">1</td>") < table.indexOf(">2</td>"));
    assertTrue(html.indexOf("src=\"cid:octane-report-zone.png\"") < html.indexOf(table));

    String closedOnly =
        emailTable(
            renderCustomizedEmail(
                snapshot(OctaneGateReportState.PASSED, "Gate passed."),
                "",
                false,
                true,
                "Closed",
                0),
            "defects");
    assertTrue(closedOnly.contains(">7</td>"));
    assertTrue(closedOnly.contains(">8</td>"));
    assertFalse(closedOnly.contains(">1</td>"));
  }

  @Test
  public void omitsDefectTableWhenDisabledOrNoDefectsMatch() {
    OctaneGateReportSnapshot snapshot = snapshot(OctaneGateReportState.PASSED, "Gate passed.");

    assertFalse(
        renderCustomizedEmail(snapshot, "", false, false, "", 0)
            .contains("data-octane-email-table=\"defects\""));
    String noMatches = renderCustomizedEmail(snapshot, "", false, true, "does-not-match", 0);
    assertFalse(noMatches.contains("data-octane-email-table=\"defects\""));
    assertFalse(noMatches.contains(">Defects</caption>"));
  }

  @Test
  public void rendersDefinedScopeImmediatelyAboveExecutionGraphAndOmitsEmptyScope() {
    OctaneGateReportSnapshot populated =
        snapshot(OctaneGateReportState.PASSED, "Gate passed.")
            .withDefinedScope(OctaneDefinedScope.parse("ESA - imelda sanya, Digisoc"));

    String html =
        renderer.render(
            "", "Project", "Domain", populated, REPORT_URL, "octane-report.png", "LIGHT");
    String empty =
        renderer.render(
            "",
            "Project",
            "Domain",
            snapshot(OctaneGateReportState.PASSED, "Gate passed."),
            REPORT_URL,
            "octane-report.png",
            "LIGHT");

    assertTrue(html.contains("data-octane-email-section=\"defined-scope\""));
    assertTrue(html.contains(">ESA</td>"));
    assertTrue(html.contains(">Imelda Sanya</td>"));
    assertTrue(html.contains(">Digisoc</td>"));
    assertTrue(html.contains(">-</td>"));
    int scopeTableStart = html.indexOf("data-octane-email-table=\"defined-scope\"");
    int scopeTableEnd = html.indexOf("</table>", scopeTableStart);
    String scopeTable = html.substring(scopeTableStart, scopeTableEnd);
    assertTrue(scopeTable.contains("table-layout:fixed"));
    assertTrue(scopeTable.contains("<col style=\"width:58%;\">"));
    assertTrue(scopeTable.contains("<col style=\"width:42%;\">"));
    assertFalse(scopeTable.contains("text-align:center"));
    assertTrue(scopeTable.contains("word-break:break-word"));
    assertTrue(html.indexOf("Defined Scope") < html.indexOf("Execution graph"));
    assertFalse(empty.contains("data-octane-email-section=\"defined-scope\""));
    assertFalse(empty.contains("No defined scope!"));
  }

  @Test
  public void splitsDefinedScopeTablesAccordingToEmailRowRules() {
    String eleven = renderDefinedScopeEmail(11);
    String twenty = renderDefinedScopeEmail(20);
    String thirtyThree = renderDefinedScopeEmail(33);

    assertEquals(2, occurrences(eleven, "data-octane-email-table=\"defined-scope\""));
    assertEquals(2, occurrences(twenty, "data-octane-email-table=\"defined-scope\""));
    assertEquals(2, occurrences(thirtyThree, "data-octane-email-table=\"defined-scope\""));
    int firstTableEnd =
        eleven.indexOf("</table>", eleven.indexOf("data-octane-email-table=\"defined-scope\""));
    assertTrue(eleven.substring(0, firstTableEnd).contains("Project 10"));
    assertFalse(eleven.substring(0, firstTableEnd).contains("Project 11"));
    int thirtyThreeFirstEnd =
        thirtyThree.indexOf(
            "</table>", thirtyThree.indexOf("data-octane-email-table=\"defined-scope\""));
    assertTrue(thirtyThree.substring(0, thirtyThreeFirstEnd).contains("Project 17"));
    assertFalse(thirtyThree.substring(0, thirtyThreeFirstEnd).contains("Project 18"));
  }

  @Test
  public void executionTableExcludesSkippedAndNoRunTestsFromPassRate() {
    OctaneGateReportSnapshot snapshot =
        reconciliationSnapshot(
            OctaneGateReportState.POLLING,
            List.of("passed", "passed", "failed", "blocked", "skipped", "planned"),
            0);

    String html = renderExecutionDetails(snapshot, OctaneReportTheme.LIGHT);

    assertTrue(detailRow(html, "Executed test cases").contains(">4</td>"));
    assertTrue(detailRow(html, "No run test cases").contains(">1</td>"));
    assertTrue(detailRow(html, "Skipped test cases").contains(">1</td>"));
    assertTrue(detailRow(html, "Execution rate").contains(">66.7%</td>"));
    assertTrue(passRateRow(html).contains(">50%</td>"));
  }

  @Test
  public void templateRateTokensReuseLiveReportTwoDecimalValues() {
    OctaneGateReportSnapshot snapshot =
        reconciliationSnapshot(
            OctaneGateReportState.POLLING,
            List.of("passed", "passed", "failed", "blocked", "skipped", "planned"),
            0);

    String html =
        renderer.render(
            "Rates: {{EXECUTIONRATE}} execution, {{PASSRATE}} pass.",
            "Payments",
            "Finance",
            snapshot,
            REPORT_URL,
            "octane-progress.png");

    assertTrue(html.contains("Rates: 66.67% execution, 50.00% pass."));
    assertFalse(html.contains("{{EXECUTIONRATE}}"));
    assertFalse(html.contains("{{PASSRATE}}"));
  }

  @Test
  public void rendersOngoingIntervalReportInSystemOrangeWithInlineScreenshot() {
    String html =
        renderer.render(
            """
            The automated job for {{PROJECT_NAME}} tests is {{GATE_RESULT}}, with an execution rate of {{EXECUTIONRATE}}, pass rate of {{PASSRATE}}.
            The latest Octane update was {{LAST_UPDATE}}.

            {{EXECUTION_DETAILS}}

            {{REPORT_SCREENSHOT}}
            """,
            "Payments",
            "Finance",
            snapshot(OctaneGateReportState.POLLING, "Gate polling."),
            REPORT_URL,
            "octane-progress.png",
            OctaneReportTheme.DARK.name());

    assertTrue(html.contains("color:#FF9F0A;font-weight:700;\">ONGOING"));
    assertTrue(html.contains("with an execution rate of 100.00%, pass rate of 90.00%."));
    assertTrue(html.contains("The latest Octane update was 15:00:00."));
    assertFalse(html.contains("{{LAST_UPDATE}}"));
    assertFalse(html.contains("{{EXECUTIONRATE}}"));
    assertFalse(html.contains("{{PASSRATE}}"));
    assertTrue(html.contains("data-octane-email-section=\"execution-report\""));
    assertTrue(html.contains("src=\"cid:octane-progress.png\""));
    assertPassRateCell(html, "#FF9F0A", "#000000", true);
    assertTrue(detailRow(html, "End date").contains(">In Progress</td>"));
    assertTrue(html.contains("Defect Logging Compliance"));
  }

  @Test
  public void rendersFinalTimestampInEndDateCellForCompletedReport() {
    String html =
        renderExecutionDetails(
            snapshot(OctaneGateReportState.PASSED, "Gate passed."), OctaneReportTheme.LIGHT);

    String endDateRow = detailRow(html, "End date");
    assertTrue(endDateRow.contains(">15:00:00 30/06/2026 EAT</td>"));
    assertFalse(endDateRow.contains("In Progress"));
  }

  @Test
  public void reconcilesDefectsForTallyUnderReportedAndSurplusStates() {
    String zeroTally =
        renderExecutionDetails(
            reconciliationSnapshot(OctaneGateReportState.POLLING, List.of("passed"), 0),
            OctaneReportTheme.LIGHT);
    String tally =
        renderExecutionDetails(
            reconciliationSnapshot(
                OctaneGateReportState.PASSED, List.of("passed", "blocked", "failed"), 2),
            OctaneReportTheme.LIGHT);
    String underReported =
        renderExecutionDetails(
            reconciliationSnapshot(
                OctaneGateReportState.FAILED, List.of("blocked", "failed", "failed"), 1),
            OctaneReportTheme.LIGHT);
    String surplus =
        renderExecutionDetails(
            reconciliationSnapshot(OctaneGateReportState.POLLING, List.of("passed"), 2),
            OctaneReportTheme.DARK);
    String closedAfterPassingRerun =
        renderExecutionDetails(
            reconciliationSnapshot(OctaneGateReportState.PASSED, List.of("passed", "passed"), 0, 2),
            OctaneReportTheme.LIGHT);
    String closedDefectsExcludedFromActual =
        renderExecutionDetails(
            reconciliationSnapshot(
                OctaneGateReportState.PASSED, List.of("blocked", "passed"), 1, 2),
            OctaneReportTheme.LIGHT);

    String zeroTallyTable = emailTable(zeroTally, "defect-reconciliation");
    assertReconciliationCounts(zeroTallyTable, 0, 0, 0, 0);
    assertTrue(zeroTallyTable.contains("0% (TALLY)"));

    String tallyTable = emailTable(tally, "defect-reconciliation");
    assertReconciliationCounts(tallyTable, 1, 1, 2, 2);
    assertTrue(tallyTable.contains("0% (TALLY)"));
    assertTrue(tallyTable.contains("border-left:4px solid #34C759"));

    String underReportedTable = emailTable(underReported, "defect-reconciliation");
    assertReconciliationCounts(underReportedTable, 1, 2, 3, 1);
    assertTrue(underReportedTable.contains("66.7% (UNDER-REPORTED)"));
    assertTrue(underReportedTable.contains("border-left:4px solid #FF3B30"));

    String surplusTable = emailTable(surplus, "defect-reconciliation");
    assertReconciliationCounts(surplusTable, 0, 0, 0, 2);
    assertTrue(surplusTable.contains("+100% (SURPLUS)"));
    assertTrue(surplusTable.contains("border-left:4px solid #FF9F0A"));

    String closedAfterPassingRerunTable =
        emailTable(closedAfterPassingRerun, "defect-reconciliation");
    assertReconciliationCounts(closedAfterPassingRerunTable, 0, 0, 0, 0);
    assertTrue(closedAfterPassingRerunTable.contains("0% (TALLY)"));

    String closedDefectsExcludedTable =
        emailTable(closedDefectsExcludedFromActual, "defect-reconciliation");
    assertReconciliationCounts(closedDefectsExcludedTable, 1, 0, 1, 1);
    assertTrue(closedDefectsExcludedTable.contains("0% (TALLY)"));
    assertFalse(closedDefectsExcludedTable.contains("SURPLUS"));
  }

  @Test
  public void placesCriteriaAndReconciliationTablesBesideEachOtherInFinalAndIntervalEmails() {
    OctaneGateReportSnapshot finalSnapshot =
        reconciliationSnapshot(
            OctaneGateReportState.PASSED, List.of("passed", "blocked", "failed"), 2);
    OctaneGateReportSnapshot intervalSnapshot =
        reconciliationSnapshot(
            OctaneGateReportState.POLLING, List.of("passed", "blocked", "failed"), 1);

    String finalHtml = renderExecutionDetails(finalSnapshot, OctaneReportTheme.LIGHT);
    String intervalHtml = renderExecutionDetails(intervalSnapshot, OctaneReportTheme.DARK);

    assertPairedCriteriaAndReconciliationTables(finalHtml);
    assertPairedCriteriaAndReconciliationTables(intervalHtml);
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

    assertPassRateCell(lightPass, "#34C759", "#000000", true);
    assertPassRateCell(lightFail, "#FF3B30", "#000000", true);
    assertPassRateCell(darkPass, "#30D158", "#000000", true);
    assertPassRateCell(darkFail, "#FF453A", "#000000", true);
    assertPassRateCell(fallback, "transparent", "inherit", false);
  }

  @Test
  public void placesAutomationUsageBelowPassRateAndPaintsItsTargetStatus() {
    String achieved = renderExecutionDetails(automationSnapshot(8, 2, 80), OctaneReportTheme.LIGHT);
    String warning = renderExecutionDetails(automationSnapshot(7, 3, 80), OctaneReportTheme.DARK);
    String action = renderExecutionDetails(automationSnapshot(6, 4, 80), OctaneReportTheme.LIGHT);
    String unavailable =
        renderExecutionDetails(automationSnapshot(0, 0, 80), OctaneReportTheme.LIGHT);

    assertTrue(achieved.indexOf("Pass Rate") < achieved.indexOf("Automation Usage"));
    assertAutomationUsageCell(achieved, "80%", "#34C759", "#000000", true);
    assertAutomationUsageCell(warning, "70%", "#FF9F0A", "#000000", true);
    assertAutomationUsageCell(action, "60%", "#FF3B30", "#000000", true);
    assertAutomationUsageCell(unavailable, "0%", "transparent", "inherit", false);
    assertFalse(detailRow(achieved, "Automation Usage").contains("🔥"));
    assertFalse(detailRow(unavailable, "Automation Usage").contains("🐢"));
    assertFalse(detailRow(achieved, "Automation Usage").contains("role=\"presentation\""));
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
    assertTrue(
        enabled.indexOf("To get a green light for release") < enabled.indexOf("Defect groups:"));
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

  private String renderCustomizedEmail(
      OctaneGateReportSnapshot snapshot,
      String projectSummary,
      boolean includeProjectSummary,
      boolean printDefects,
      String defectFilter,
      int defectLimit) {
    return renderer.render(
        "{{CRITERIA}}\n{{EXECUTION_DETAILS}}\n{{REPORT_SCREENSHOT}}",
        "Business Payments Secure Checkout",
        "FS_TRIBE_DOMAIN",
        snapshot,
        REPORT_URL,
        "octane-report-zone.png",
        OctaneReportTheme.LIGHT.name(),
        false,
        projectSummary,
        includeProjectSummary,
        printDefects,
        defectFilter,
        defectLimit);
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

  private void assertAutomationUsageCell(
      String html, String value, String backgroundColor, String fontColor, boolean expectsBgcolor) {
    String row = detailRow(html, "Automation Usage");
    assertEquals(expectsBgcolor, row.contains("bgcolor="));
    if (expectsBgcolor) {
      assertTrue(row.contains("bgcolor=\"" + backgroundColor + "\""));
    }
    assertTrue(row.contains("background-color:" + backgroundColor + ";"));
    assertTrue(row.contains("color:" + fontColor + ";"));
    assertTrue(row.contains(">" + value + "</td>"));
  }

  private String passRateRow(String html) {
    return detailRow(html, "Pass Rate");
  }

  private String detailRow(String html, String labelText) {
    int label = html.indexOf(labelText);
    assertTrue("Missing " + labelText + " row", label >= 0);
    int start = html.lastIndexOf("<tr>", label);
    int end = html.indexOf("</tr>", label);
    assertTrue("Missing " + labelText + " row bounds", start >= 0 && end > label);
    return html.substring(start, end + "</tr>".length());
  }

  private void assertReconciliationCounts(
      String table, int blocked, int failed, int expected, int actual) {
    assertTrue(table.contains(">Blocked Tests</th><td"));
    assertTrue(table.contains(">" + blocked + "</td></tr>"));
    assertTrue(table.contains(">Failed Tests</th><td"));
    assertTrue(table.contains(">" + failed + "</td></tr>"));
    assertTrue(table.contains(">Total Expected Defects</th><td"));
    assertTrue(table.contains(">" + expected + "</td></tr>"));
    assertTrue(table.contains(">Actual Open Defects</th><td"));
    assertTrue(table.contains(">" + actual + "</td></tr>"));
  }

  private void assertPairedCriteriaAndReconciliationTables(String html) {
    String marker = "data-octane-email-section=\"criteria-reconciliation\"";
    int markerIndex = html.indexOf(marker);
    assertTrue("Missing criteria/reconciliation row", markerIndex >= 0);
    int rowStart = html.lastIndexOf("<table", markerIndex);
    int rowEnd = html.indexOf("</table></td></tr></table>", markerIndex);
    assertTrue("Missing criteria/reconciliation row bounds", rowStart >= 0 && rowEnd > rowStart);
    String row = html.substring(rowStart, rowEnd);
    assertTrue(row.contains("data-octane-email-table=\"criteria-evaluation\""));
    assertTrue(row.contains("data-octane-email-table=\"defect-reconciliation\""));
    assertTrue(
        row.indexOf("data-octane-email-table=\"criteria-evaluation\"")
            < row.indexOf("data-octane-email-table=\"defect-reconciliation\""));
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
    List<DefectRecord> defects =
        List.of(
            defect("1", "Critical", "opened"),
            defect("2", "Very High", "opened"),
            defect("3", "High", "opened"),
            defect("4", "Medium", "opened"),
            defect("5", "Low", "opened"),
            defect("6", "", "opened"),
            defect("7", "High", "closed"),
            defect("8", "Low", "closed"));
    OctaneDefectSeveritySummary defectSummary = OctaneDefectSeveritySummary.fromDefects(defects);
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
            defects,
            evaluation,
            Instant.parse("2026-06-30T12:00:00Z"));
    return OctaneGateReportSnapshot.fromResult(state, message, result, classifier, 30);
  }

  private OctaneGateReportSnapshot reconciliationSnapshot(
      OctaneGateReportState state, List<String> statuses, int defectsRaised) {
    return reconciliationSnapshot(state, statuses, defectsRaised, 0);
  }

  private OctaneGateReportSnapshot reconciliationSnapshot(
      OctaneGateReportState state, List<String> statuses, int openDefects, int closedDefects) {
    List<RunRecord> runs = new ArrayList<>();
    for (int index = 0; index < statuses.size(); index++) {
      runs.add(new RunRecord("run-" + index, "Run " + index, statuses.get(index), "Test Engineer"));
    }
    List<DefectRecord> defects = new ArrayList<>();
    for (int index = 0; index < openDefects; index++) {
      defects.add(defect("reconciliation-defect-" + index, "High", "opened"));
    }
    for (int index = 0; index < closedDefects; index++) {
      defects.add(defect("closed-reconciliation-defect-" + index, "High", "closed"));
    }
    CriteriaEvaluation evaluation =
        CriteriaEvaluation.available(
            state == OctaneGateReportState.PASSED,
            List.of(
                new CriteriaComparisonEvaluation(
                    "regressions.executionRate", "==", 100, 100, true, true)));
    GateResult result =
        new GateResult(
            "suite-1",
            "regressions.executionRate == 100",
            evaluation.isPassed(),
            true,
            GateMetrics.fromRuns(runs, classifier),
            runs,
            Map.of("suite-1", runs),
            Map.of(),
            OctaneRiskHeatMap.disabled(),
            new DefectCriteriaMetrics(OctaneDefectSeveritySummary.fromDefects(defects), List.of()),
            defects,
            evaluation,
            Instant.parse("2026-06-30T12:00:00Z"));
    return OctaneGateReportSnapshot.fromResult(
        state, "Reconciliation snapshot.", result, classifier, 30);
  }

  private OctaneGateReportSnapshot automationSnapshot(int automated, int manual, int target) {
    List<RunRecord> runs = new ArrayList<>();
    for (int index = 0; index < automated; index++) {
      runs.add(
          new RunRecord(
              "automated-" + index,
              "Automated " + index,
              "passed",
              "Jenkins Agent",
              "Assigned Tester",
              "test-a-" + index,
              "Test",
              "",
              ""));
    }
    for (int index = 0; index < manual; index++) {
      runs.add(
          new RunRecord(
              "manual-" + index,
              "Manual " + index,
              "passed",
              "Assigned Tester",
              "Assigned Tester",
              "test-m-" + index,
              "Test",
              "",
              ""));
    }
    GateResult result =
        new GateResult(
            "suite-1",
            "regressions.executionRate == 100",
            true,
            true,
            GateMetrics.fromRuns(runs, classifier),
            runs,
            Map.of("suite-1", runs),
            Map.of(),
            Instant.parse("2026-06-30T12:00:00Z"));
    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.fromResult(
            OctaneGateReportState.PASSED, "Gate passed.", result, classifier, 30);
    return snapshot
        .withTestMetrics(snapshot.getTestMetrics().withAutomatedTestingTarget(target))
        .withCalculatedTestMetrics(null);
  }

  private String renderDefinedScopeEmail(int count) {
    List<OctaneDefinedScope> scopes = new ArrayList<>();
    for (int index = 1; index <= count; index++) {
      scopes.add(new OctaneDefinedScope("Project " + index, "Owner " + index));
    }
    OctaneGateReportSnapshot snapshot =
        snapshot(OctaneGateReportState.PASSED, "Gate passed.").withDefinedScope(scopes);
    return renderer.render(
        "", "Project", "Domain", snapshot, REPORT_URL, "octane-report.png", "LIGHT");
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
