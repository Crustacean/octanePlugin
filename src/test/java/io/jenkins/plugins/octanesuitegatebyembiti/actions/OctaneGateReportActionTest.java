package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneGateReportActionTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void attachesToBuildAndRendersReportPage() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setPollIntervalSeconds(15);
    request.setTimeoutMinutes(45);
    request.setBasePassrateFigure(70);
    request.setBaseExecutionFigure(90);

    OctaneGateReportAction action = OctaneGateReportAction.attachTo(build, request);
    action.onFinal(
        OctaneGateReportState.PASSED,
        "ALM Octane suite gate passed.",
        result(),
        new StatusClassifier(
            StatusClassifier.DEFAULT_PASSED_STATUSES,
            StatusClassifier.DEFAULT_FAILED_STATUSES,
            StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
            StatusClassifier.DEFAULT_RUNNING_STATUSES));

    assertSame(action, build.getAction(OctaneGateReportAction.class));
    assertNotNull(build.getAction(OctaneGateReportAction.class).getSnapshot());

    HtmlPage page = jenkins.createWebClient().getPage(build, OctaneGateReportAction.URL_NAME);
    String text = page.asNormalizedText();
    String xml = page.asXml();
    assertTrue(text.contains("Octane Gate Report"));
    assertTrue(text.contains("Last update: 03:00:00"));
    assertFalse(text.contains("Last update (EAT)"));
    assertTrue(text.contains("REGRESSION Tests Status Distribution"));
    assertTrue(text.contains("Testing progress per Tester Suite Runs_REGRESSIONS"));
    assertTrue(text.contains("Testing Time"));
    assertFalse(text.contains("Testing Time Remaining"));
    assertTrue(text.contains("Test Metrics"));
    assertTrue(text.contains("Current Job Analytics"));
    assertTrue(text.contains("Avg. Execution Time"));
    assertTrue(text.contains("Success Rate"));
    assertTrue(text.contains("Execution Completion"));
    assertTrue(text.contains("Open Defects"));
    assertTrue(text.contains("Status Check"));
    assertTrue(text.contains("Testing Session Timer"));
    assertTrue(text.contains("Execution Progress"));
    assertTrue(text.contains("All Testcase execution"));
    assertTrue(text.contains("All Testcase Status"));
    assertTrue(text.contains("Testcases"));
    assertTrue(text.contains("Execution Pass Rate"));
    assertTrue(text.contains("Execution Defect Rate"));
    assertTrue(text.contains("Defect Arrival vs. Resolution Trend Analysis"));
    assertFalse(text.contains("Defect Volumes"));
    assertTrue(text.contains("Volume"));
    assertTrue(text.contains("Density"));
    assertTrue(xml.contains("data-defect-target-view=\"volumes\""));
    assertTrue(xml.contains("data-defect-target-view=\"density\""));
    assertTrue(xml.contains("data-defect-title=\"Defect Density\""));
    assertTrue(xml.contains("data-defect-subtitle=\"Execution Defect Density\""));
    assertTrue(xml.contains("octane-defect-pane-label-full"));
    assertTrue(xml.contains("octane-defect-pane-label-short"));
    assertTrue(xml.contains(">V</span>"));
    assertTrue(xml.contains(">D</span>"));
    assertTrue(text.contains("Defects Raised"));
    assertTrue(text.contains("Defects"));
    assertTrue(xml.contains("octane-defect-density-axis-title"));
    assertTrue(xml.contains(">Defect Density</div>"));
    assertTrue(text.contains("Opened Defects"));
    assertTrue(text.contains("All Testcase Pass Rate (1 / 2)"));
    assertTrue(text.contains("Total: 2"));
    assertTrue(text.contains("Total Suiteruns: 1"));
    assertTrue(text.contains("Tester Details"));
    assertTrue(text.contains("Testers with LESS THAN 70% Pass Rate"));
    assertTrue(text.contains("Testers with LESS THAN 90% Execution"));
    assertTrue(text.contains("Suiterun Passrate"));
    assertTrue(text.contains("Suiterun Execution"));
    assertTrue(text.contains("Everything Good!"));
    assertTrue(xml.contains("id=\"tester-details-zone\""));
    assertTrue(xml.contains("class=\"octane-tester-details-toggle\""));
    assertTrue(xml.contains("aria-expanded=\"true\""));
    assertTrue(xml.contains("max-height: calc(100vh - 2rem)"));
    assertTrue(xml.contains("scrollbar-color: #666 transparent"));
    assertTrue(xml.contains("function updateTesterDetails(payload)"));
    assertTrue(xml.contains("updateTesterDetails(payload)"));
    assertTrue(xml.contains("event.target.closest(\".octane-tester-details-toggle\")"));
    assertTrue(xml.contains("Ada Tester"));
    assertTrue(text.contains("ada tester"));
    assertFalse(text.contains("Total Testcases"));
    assertFalse(text.contains("Global + Critical execution"));
    assertFalse(text.contains("Execution 100.0%, pass"));
    assertFalse(text.contains("Suite runs: 4501"));
    assertTrue(xml.contains("--octane-status-passed: #34C759"));
    assertTrue(xml.contains("--octane-status-passed: #30D158"));
    assertTrue(xml.contains("--octane-status-failed: #FF3B30"));
    assertTrue(xml.contains("--octane-status-failed: #FF453A"));
    assertTrue(xml.contains("--octane-status-blocked: #FF9500"));
    assertTrue(xml.contains("--octane-status-blocked: #FF9F0A"));
    assertTrue(xml.contains("--octane-status-skipped: #AF52DE"));
    assertTrue(xml.contains("--octane-status-skipped: #BF5AF2"));
    assertTrue(xml.contains("--octane-status-no-run: #8E8E93"));
    assertTrue(xml.contains("var(--octane-status-passed)"));
    assertTrue(xml.contains("var(--octane-status-failed)"));
    assertTrue(xml.contains("octane-donut"));
    assertTrue(xml.contains("octane-distribution-meta"));
    assertTrue(xml.contains("octane-total-label"));
    assertTrue(xml.contains("octane-donut-label"));
    assertTrue(
        xml.contains("viewBox=\"-10 -10 120 120\"") || xml.contains("viewbox=\"-10 -10 120 120\""));
    assertTrue(xml.contains("max-width: 280px"));
    assertFalse(xml.contains("min-height: 294px"));
    assertTrue(xml.contains("overflow: visible"));
    assertTrue(xml.contains("<path d=") || xml.contains("r=\"46\" fill="));
    assertTrue(xml.contains("r=\"30\""));
    assertTrue(xml.contains(">50%</text>"));
    assertTrue(xml.contains("border-radius: 14px"));
    assertFalse(xml.contains("border-radius: 6px"));
    assertTrue(xml.contains("contain: layout style paint"));
    assertTrue(xml.contains("container-type: inline-size"));
    assertTrue(xml.contains("@supports not (font-size: 1cqw)"));
    assertTrue(xml.contains("font-size: clamp(0.72rem, 3.2cqw, 1.08rem)"));
    assertTrue(xml.contains("font-size: clamp(0.66rem, 2.6cqw, 1rem)"));
    assertTrue(xml.contains("font-size: clamp(0.9rem, 2.2cqw, 1.3rem)"));
    assertTrue(xml.contains("font-size: clamp(0.58rem, 2.35cqw, 0.9rem)"));
    assertTrue(
        xml.contains(".octane-chart-card[data-card-key=&quot;timer-poll&quot;]")
            || xml.contains(".octane-chart-card[data-card-key=\"timer-poll\"]"));
    assertTrue(xml.contains("display: flex"));
    assertTrue(xml.contains("flex-direction: column"));
    assertTrue(xml.contains(".octane-risk-heat-map-panel-inner"));
    assertTrue(xml.contains("align-content: stretch"));
    assertTrue(xml.contains("grid-template-columns: minmax(0, 1fr)"));
    assertTrue(xml.contains("grid-template-rows: minmax(0, 1fr) max-content"));
    assertTrue(xml.contains("gap: clamp(0.35rem, 1vh, 0.65rem)"));
    assertTrue(xml.contains(".octane-risk-heat-map-container"));
    assertTrue(xml.contains("container-type: size"));
    assertTrue(xml.contains("min-height: 0"));
    assertFalse(xml.contains("min-height: 180px"));
    assertTrue(xml.contains("overflow: hidden"));
    assertTrue(xml.contains(".octane-risk-issues-container"));
    assertTrue(xml.contains("align-self: end"));
    assertTrue(xml.contains("padding-top: clamp(0.2rem, 0.8vh, 0.55rem)"));
    assertTrue(xml.contains("padding-bottom: clamp(0.45rem, 0.8cqw, 0.7rem)"));
    assertTrue(xml.contains("height: 100%"));
    assertTrue(xml.contains("max-width: 220px"));
    assertTrue(xml.contains("height: min(100cqw, 100cqh, 220px)"));
    assertTrue(xml.contains("width: min(100%, 220px)"));
    assertTrue(xml.contains("width: min(100cqw, 100cqh, 220px)"));
    assertTrue(xml.contains("width: min(100cqw, 100cqh, 38vh, 38vw)"));
    assertTrue(xml.contains("width: min(100cqw, 100cqh, 76vh, 76vw)"));
    assertTrue(xml.contains("container-type: inline-size"));
    assertTrue(xml.contains("width: 75%"));
    assertTrue(
        xml.contains("font-family: Inter")
            && xml.contains("Segoe UI")
            && xml.contains("Arial, sans-serif"));
    assertTrue(xml.contains("font-size: 0.875em"));
    assertTrue(xml.contains("font-weight: 500"));
    assertTrue(xml.contains("line-height: 1.35"));
    assertTrue(xml.contains("min-height: clamp(2.58em, 3.4cqw, 4.25em)"));
    assertTrue(xml.contains("text-overflow: clip"));
    assertTrue(xml.contains("@container (max-width: 44rem)"));
    assertTrue(xml.contains(".octane-defect-severity-label"));
    assertTrue(xml.contains("display: none"));
    assertTrue(xml.contains("border-color: #4391F5"));
    assertTrue(xml.contains("font-size: 0"));
    assertTrue(xml.contains("height: 1.15rem"));
    assertTrue(xml.contains("letter-spacing: 0"));
    assertTrue(xml.contains("width: 1.15rem"));
    assertTrue(xml.contains("aria-label=\"Move widget\""));
    assertTrue(xml.contains("Use arrow keys to reorder"));
    assertTrue(xml.contains("octane-reorder-status"));
    assertTrue(xml.contains("aria-live=\"polite\""));
    assertTrue(xml.contains(".octane-card-tools:focus-visible"));
    assertTrue(xml.contains("outline: 3px solid #4391F5"));
    assertTrue(xml.contains("octane-grabber-icon"));
    assertTrue(xml.contains("<circle cx=\"12\" cy=\"5\" r=\"2\""));
    assertFalse(xml.contains(":::"));
    assertFalse(xml.contains(".octane-card-tools::before"));
    assertTrue(xml.contains("octane-card-actions"));
    assertTrue(xml.contains("octane-expand-toggle"));
    assertTrue(xml.contains("octane-icon-expand"));
    assertTrue(xml.contains("octane-icon-collapse"));
    assertTrue(xml.contains("data-target-view=\"metrics\""));
    assertTrue(xml.contains("data-target-view-label=\"test metrics\""));
    assertTrue(xml.contains("octane-icon-metrics"));
    assertTrue(xml.contains("data-target-view=\"breakdown\""));
    assertTrue(xml.contains("data-target-view-label=\"status breakdown\""));
    assertTrue(xml.contains("octane-icon-breakdown"));
    assertTrue(xml.contains("data-target-view=\"defects\""));
    assertTrue(xml.contains("data-target-view-label=\"execution defect rate\""));
    assertTrue(xml.contains("octane-icon-defects"));
    assertTrue(xml.contains("octane-flip-face-defects"));
    assertTrue(xml.contains("octane-defect-face-header"));
    assertTrue(xml.contains("data-defect-view-title=\"true\""));
    assertTrue(xml.contains("data-defect-view-subtitle=\"true\""));
    assertTrue(xml.contains("octane-defect-main-view-toggle"));
    assertTrue(xml.contains("data-defect-analytics=\"true\""));
    assertTrue(xml.contains("data-active-defect-view=\"volumes\""));
    assertTrue(xml.contains("octane-defect-pane-switcher"));
    assertTrue(xml.contains("octane-defect-face-actions"));
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-header ')]"
                    + "/*[1][contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-heading ')]")
            .size());
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-header ')]"
                    + "/*[2][contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-pane-switcher ')]")
            .size());
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-header ')]"
                    + "/*[3][contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-actions ')]")
            .size());
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-actions ')]"
                    + "/button[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-view-toggle ')]")
            .size());
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-actions ')]"
                    + "/button[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-expand-toggle ')]")
            .size());
    assertEquals(
        1,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "//div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-face-actions ')]"
                    + "/button[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-card-tools ')]")
            .size());
    assertEquals(
        0,
        page.getByXPath(
                "//section[@data-card-key='progress-pass-rate']"
                    + "/div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-card-actions ')]"
                    + "/div[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-pane-switcher ')]")
            .size());
    assertTrue(xml.contains("--octane-control-size: 1.15rem"));
    assertTrue(xml.contains("height: var(--octane-control-size, 1.15rem)"));
    assertTrue(
        xml.contains(
            ".octane-defect-face-header {\n          align-items: flex-start;\n"
                + "          display: flex;"));
    assertTrue(xml.contains("flex-wrap: nowrap"));
    assertTrue(xml.contains("border-radius: 9999px"));
    assertTrue(xml.contains("padding: 2px"));
    assertTrue(xml.contains("display: inline-flex"));
    assertTrue(xml.contains("justify-content: center"));
    assertTrue(xml.contains(".octane-defect-pane {\n          box-sizing: border-box;"));
    assertTrue(xml.contains("grid-template-rows: minmax(0, 1fr);\n          height: 100%;"));
    assertTrue(
        xml.contains(
            ".octane-defect-analytics {\n            grid-template-rows: minmax(0, 1fr);"));
    assertTrue(
        xml.contains(
            ".octane-chart-card[data-card-key=\"progress-pass-rate\"][data-active-view=\"defects\"]"));
    assertTrue(xml.contains("data-defect-target-view=\"volumes\""));
    assertTrue(xml.contains("data-defect-target-view=\"density\""));
    assertTrue(xml.contains("data-defect-pane=\"volumes\""));
    assertTrue(xml.contains("data-defect-pane=\"density\""));
    assertTrue(xml.contains("data-defect-trend-panel=\"true\""));
    assertTrue(xml.contains("octane-defect-trend-summary-card"));
    assertTrue(
        xml.contains(
            ".octane-defect-trend-summary-card {\n          align-content: start;\n"
                + "          background: transparent;\n          border: 0;"));
    assertTrue(xml.contains("padding: 2px 0"));
    assertTrue(xml.contains(".octane-defect-trend-value,\n        .octane-defect-density-value"));
    assertTrue(
        xml.contains(".octane-defect-trend-total-label,\n        .octane-defect-density-label"));
    assertTrue(xml.contains("octane-defect-trend-line-opened"));
    assertTrue(xml.contains("octane-defect-trend-line-closed"));
    assertTrue(xml.contains("octane-defect-trend-axis-title"));
    assertTrue(xml.contains("data-executed=\""));
    assertTrue(xml.contains("data-defect-density-panel=\"true\""));
    assertTrue(xml.contains("data-defect-density-raised-total=\"true\""));
    assertTrue(xml.contains("grid-template-columns: max-content max-content minmax(0, 1fr)"));
    assertTrue(xml.contains("octane-defect-density-svg"));
    assertTrue(xml.contains("octane-defect-density-area"));
    assertTrue(xml.contains("octane-defect-density-line"));
    assertTrue(xml.contains("fill: #3B82F6"));
    assertTrue(xml.contains("fill-opacity: 0.76"));
    assertTrue(xml.contains("stroke: #3B82F6"));
    assertTrue(xml.contains("stroke-width: 1px"));
    assertTrue(xml.contains("stroke-width=\"1\""));
    assertTrue(xml.contains("shape-rendering: geometricPrecision"));
    assertTrue(xml.contains("shape-rendering=\"geometricPrecision\""));
    assertTrue(xml.contains("octane-defect-density-axis-line-dotted"));
    assertTrue(xml.contains("data-defect-density-bucket=\"true\""));
    assertEquals(
        1,
        page.getByXPath(
                "//*[contains(concat(' ', normalize-space(@class), ' '),"
                    + " ' octane-defect-trend-axis-line ')]")
            .size());
    assertTrue(xml.contains("font-size: clamp(0.66rem, 2.1cqw, 0.75rem)"));
    assertTrue(xml.contains("octane-defect-trend-plot"));
    assertTrue(xml.contains("octane-defect-trend-y-labels"));
    assertTrue(xml.contains("octane-defect-trend-x-labels"));
    assertTrue(xml.contains("stroke-dasharray: 5 7"));
    assertTrue(xml.contains("stroke-width: clamp(2px, 0.28cqw, 4px)"));
    assertTrue(xml.contains("function niceDefectTrendScale"));
    assertTrue(
        xml.contains("for (var value = scale.step; value <= scale.maximum; value += scale.step)")
            || xml.contains(
                "for (var value = scale.step; value &lt;= scale.maximum; value += scale.step)"));
    assertTrue(
        xml.contains("for (var yValue = scale.maximum; yValue >= 0; yValue -= scale.step)")
            || xml.contains(
                "for (var yValue = scale.maximum; yValue &gt;= 0; yValue -= scale.step)"));
    assertTrue(xml.contains("function animateDefectTrend"));
    assertTrue(xml.contains("scheduleTimerFrame(animateDefectTrend)"));
    assertTrue(xml.contains("function updateDefectTrend"));
    assertTrue(xml.contains("updateDefectTrend(payload)"));
    assertTrue(xml.contains("function setDefectAnalyticsView"));
    assertTrue(xml.contains("data-defect-view-title"));
    assertTrue(xml.contains("selectedButton.getAttribute(\"data-defect-title\")"));
    assertTrue(xml.contains("function buildDefectDensityBuckets"));
    assertTrue(xml.contains("function renderDefectDensity"));
    assertTrue(xml.contains("event.target.closest(\".octane-defect-pane-toggle\")"));
    assertTrue(xml.contains("octane-flip-face-breakdown"));
    assertTrue(xml.contains("data-execution-breakdown-panel=\"true\""));
    assertTrue(xml.contains("octane-execution-breakdown-content"));
    assertTrue(xml.contains("data-status-count=\"2\""));
    assertTrue(xml.contains("octane-execution-half-pie"));
    assertTrue(xml.contains("octane-execution-half-pie-segment"));
    assertTrue(
        xml.contains("viewBox=\"0 36 320 160\"") || xml.contains("viewbox=\"0 36 320 160\""));
    assertTrue(
        xml.contains("preserveAspectRatio=\"xMidYMid meet\"")
            || xml.contains("preserveaspectratio=\"xMidYMid meet\""));
    assertTrue(xml.contains("octane-execution-half-pie-total\" x=\"160\" y=\"146\""));
    assertTrue(xml.contains("octane-execution-half-pie-label\" x=\"160\" y=\"172\""));
    assertTrue(xml.contains("stroke-width: 24"));
    assertTrue(xml.contains("width: min(92cqw, var(--octane-execution-height-width), 80rem)"));
    assertTrue(xml.contains("--octane-execution-height-width: 172cqh"));
    assertTrue(xml.contains("--octane-execution-height-width: 132cqh"));
    assertTrue(xml.contains("font-size: clamp(0.7rem, 1.8cqw, 1.3rem)"));
    assertTrue(xml.contains("font-size: clamp(1rem, 2cqw, 1.5rem)"));
    assertTrue(xml.contains("octane-flip-face-metrics"));
    assertTrue(xml.contains("data-test-metrics-panel=\"true\""));
    assertTrue(xml.contains("octane-test-metrics-grid"));
    assertTrue(xml.contains("octane-test-metric-card"));
    assertTrue(xml.contains(".octane-test-metric-card:nth-child(3)"));
    assertTrue(xml.contains("border-bottom-left-radius: 14px"));
    assertTrue(xml.contains(".octane-test-metric-card:nth-child(4)"));
    assertTrue(xml.contains("border-bottom-right-radius: 14px"));
    assertTrue(xml.contains("data-test-metric-key=\"avg-time\""));
    assertTrue(xml.contains("data-test-metric-key=\"success-rate\""));
    assertTrue(xml.contains("data-test-metric-key=\"execution\""));
    assertTrue(xml.contains("data-test-metric-key=\"defects\""));
    assertTrue(xml.contains("octane-zone-focus-toggle"));
    assertTrue(xml.contains("octane-icon-zone-expand"));
    assertFalse(xml.contains("octane-icon-zone-collapse"));
    assertTrue(xml.contains("Expand section"));
    assertFalse(xml.contains("Collapse section"));
    assertTrue(xml.contains("octane-zone-focused"));
    assertTrue(xml.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"));
    assertTrue(xml.contains(".octane-timer-zone.octane-zone-focused"));
    assertTrue(xml.contains(".octane-report-zone.octane-zone-focused"));
    assertTrue(xml.contains(".octane-chart-card:nth-of-type(3)"));
    assertTrue(xml.contains(".octane-chart-card:nth-of-type(4)"));
    assertTrue(xml.contains("grid-column: 1"));
    assertTrue(xml.contains("grid-column: 2"));
    assertTrue(xml.contains("grid-template-columns: minmax(0, 1fr)"));
    assertTrue(xml.contains("data-zone-key=\"timers\""));
    assertTrue(xml.contains("data-zone-key=\"reports\""));
    assertTrue(xml.contains("aria-expanded=\"false\""));
    assertTrue(xml.contains("Expand widget"));
    assertTrue(xml.contains("octane-expanded-backdrop"));
    assertTrue(xml.contains(".octane-chart-card.octane-expanded"));
    assertTrue(xml.contains("data-octane-overlay-inert"));
    assertTrue(xml.contains("activateOverlayAccessibility"));
    assertTrue(xml.contains("releaseOverlayAccessibility"));
    assertTrue(xml.contains("document.addEventListener(\"focusin\""));
    assertTrue(xml.contains("inset: 1rem"));
    assertTrue(xml.contains("padding: 1rem"));
    assertTrue(xml.contains("height: auto"));
    assertTrue(xml.contains("max-width: none"));
    assertTrue(xml.contains("width: auto"));
    assertFalse(xml.contains("width: calc(100vw - 2rem)"));
    assertFalse(xml.contains(".octane-zone-focused .octane-zone-focus-toggle"));
    assertTrue(xml.contains("--octane-axis-label-row: clamp(1.05rem, 2vh, 1.7rem)"));
    assertTrue(xml.contains("grid-template-rows: minmax(0, 1fr) var(--octane-axis-label-row)"));
    assertTrue(xml.contains("font-size: 12px"));
    assertTrue(xml.contains("max-height: 100%"));
    assertTrue(xml.contains("resize: none"));
    assertTrue(xml.contains("max-height: min(76vh, 76vw)"));
    assertTrue(xml.contains("--octane-axis-label-row: 1.7rem"));
    assertTrue(xml.contains("data-card-key=\"timer-timeout\""));
    assertTrue(xml.contains("data-card-key=\"timer-poll\""));
    assertTrue(xml.contains("data-card-key=\"progress-execution\""));
    assertTrue(xml.contains("data-card-key=\"progress-pass-rate\""));
    assertTrue(xml.contains("data-card-key=\"distribution-regressions\""));
    assertTrue(xml.contains("data-card-key=\"bars-regressions\""));
    assertFalse(xml.contains("total runs"));
    assertFalse(xml.contains("grid-template-columns: minmax(180px, 240px) max-content"));
    assertTrue(xml.contains("display: inline-flex"));
    assertFalse(xml.contains("octane-legend-value"));
    assertTrue(xml.contains("octane-suite-chart-meta"));
    assertTrue(xml.contains("octane-bar-graph"));
    assertTrue(xml.contains("octane-chart-data-summary"));
    assertTrue(xml.contains("<caption>REGRESSION Tests Status Distribution</caption>"));
    assertTrue(
        xml.contains("<caption>Testing progress per Tester Suite Runs_REGRESSIONS</caption>"));
    assertTrue(xml.contains("octane-y-axis-label"));
    assertTrue(xml.contains(">Test Runs<"));
    assertTrue(xml.contains("#827C7B"));
    assertTrue(xml.contains("background-image: radial-gradient"));
    assertTrue(xml.contains("rgba(33, 38, 45, 0.92)"));
    assertTrue(
        xml.contains("background-size: 0.55rem calc(100% / var(--octane-grid-line-count, 4))"));
    assertTrue(xml.contains("#30363D"));
    assertTrue(xml.contains("octane-grid-line-count"));
    assertFalse(xml.contains("border-left: 1px solid #576779"));
    assertFalse(xml.contains("#CCCCCC"));
    assertTrue(xml.contains("column-gap: 0.09rem"));
    assertTrue(xml.contains("grid-template-columns: 1.35rem max-content minmax(0, 1fr)"));
    assertTrue(xml.contains("grid-template-rows: 260px var(--octane-axis-label-row)"));
    assertTrue(xml.contains("octane-bar-plot"));
    assertTrue(xml.contains("octane-vertical-bars"));
    assertTrue(xml.contains("octane-vertical-bar-wrap"));
    assertTrue(xml.contains("octane-fluid-bars-dense"));
    assertTrue(xml.contains("octane-bar-overflow-indicator"));
    assertTrue(xml.contains("octane-bar-overflow-line"));
    assertTrue(xml.contains("octane-bar-overflow-count"));
    assertTrue(xml.contains("flex: 0 0 24px"));
    assertTrue(xml.contains("margin-inline-start: auto"));
    assertTrue(xml.contains("max-width: 24px"));
    assertTrue(xml.contains("width: 24px"));
    assertTrue(xml.contains("flex: 0 0 8px"));
    assertTrue(xml.contains("flex-shrink: 0 !important"));
    assertTrue(xml.contains("min-width: 8px !important"));
    assertTrue(xml.contains("margin-right: 2px !important"));
    assertTrue(xml.contains("padding: 0"));
    assertTrue(xml.contains("var FLUID_BAR_SLOT_WIDTH = 10"));
    assertTrue(xml.contains("var FLUID_BAR_OVERFLOW_WIDTH = 24"));
    assertTrue(xml.contains("function maxVisibleBarsForWidth(width)"));
    assertTrue(xml.contains("container.getBoundingClientRect().width"));
    assertTrue(xml.contains("var allSuiteRuns = container.octaneAllSuiteRuns"));
    assertTrue(xml.contains("allSuiteRuns.slice(0, maxVisibleBars)"));
    assertTrue(
        xml.contains("count.textContent = &quot;+&quot; + hiddenCount")
            || xml.contains("count.textContent = \"+\" + hiddenCount"));
    assertTrue(xml.contains("new window.ResizeObserver(scheduleFluidBarCharts)"));
    assertTrue(xml.contains("initializeFluidBarCharts(updatedReportZone)"));
    assertFalse(xml.contains("octane-x-axis-labels"));
    assertFalse(xml.contains("octane-axis-label-column"));
    assertTrue(xml.contains("overflow-x: hidden"));
    assertFalse(xml.contains("overflow-x: auto"));
    assertTrue(xml.contains("grid-template-rows: minmax(0, 1fr) var(--octane-axis-label-row)"));
    assertTrue(xml.contains("width: min(clamp(0.715rem, 70.2%, 3.12rem), calc(100% - 0.1rem))"));
    assertTrue(xml.contains("width: min(clamp(1.105rem, 80.6%, 3.38rem), calc(100% - 0.1rem))"));
    assertTrue(
        xml.contains("font-family: Inter, &quot;Segoe UI&quot;, Arial, sans-serif")
            || xml.contains("font-family: Inter, \"Segoe UI\", Arial, sans-serif"));
    assertTrue(xml.contains("font-weight: 400"));
    assertTrue(xml.contains("color: #827C7B"));
    assertTrue(xml.contains("text-align: center"));
    assertTrue(xml.contains("transform: none"));
    assertFalse(xml.contains("rotate(-45deg)"));
    assertTrue(xml.contains("octane-bar-popup"));
    assertTrue(xml.contains("id=\"octane-bar-popup-overlay\""));
    assertTrue(xml.contains("data-bar-key=\""));
    assertTrue(xml.contains("data-dominant-status-color=\""));
    assertTrue(xml.contains("data-dominant-status-label=\""));
    assertTrue(xml.contains("data-status-passed-count=\""));
    assertTrue(xml.contains("data-status-passed-color=\"#30D158\""));
    assertTrue(xml.contains("data-status-failed-color=\"#FF453A\""));
    assertTrue(xml.contains("data-status-blocked-color=\"#FF9F0A\""));
    assertTrue(xml.contains("data-status-skipped-color=\"#BF5AF2\""));
    assertTrue(xml.contains("data-status-running-color=\"#8E8E93\""));
    assertTrue(
        xml.contains(
            "border: 1px solid var(--octane-popup-border-color, var(--panel-border-color))"));
    assertTrue(xml.contains("0 0 0 1px var(--octane-popup-border-color, transparent)"));
    assertTrue(xml.contains("--octane-popup-border-color: #"));
    assertTrue(xml.contains("background: #30D158"));
    assertTrue(xml.contains("background: #FF453A"));
    assertTrue(xml.contains("min-width: min(10.92rem, calc(100vw - 1rem))"));
    assertTrue(xml.contains("position: fixed"));
    assertTrue(xml.contains("font-size: 0.644rem"));
    assertTrue(xml.contains("overflow-wrap: anywhere"));
    assertTrue(xml.contains("octane-bar-popup-name"));
    assertTrue(xml.contains("octane-bar-popup-row"));
    assertTrue(xml.contains("octane-bar-popup-total"));
    assertTrue(xml.contains("octane-bar-popup-visible"));
    assertTrue(xml.contains("octane-bar-popup-restoring"));
    assertTrue(xml.contains("positionBarPopup"));
    assertTrue(xml.contains("chooseBarPopupPlacement"));
    assertTrue(xml.contains("chartViewportRectangle"));
    assertTrue(xml.contains("pointerAnchorRectangle"));
    assertTrue(xml.contains("barRectanglesForColumn"));
    assertTrue(xml.contains("pointInsideBar"));
    assertFalse(xml.contains("pointInsideColumn"));
    assertTrue(xml.contains("document.body.appendChild(barPopupOverlay)"));
    assertTrue(xml.contains("var bar = event.target.closest(\".octane-vertical-bar\")"));
    assertTrue(xml.contains("event.relatedTarget && bar.contains(event.relatedTarget)"));
    assertTrue(xml.contains("positionBarPopup(barPopupOverlay, column, point, input)"));
    assertTrue(xml.contains("popup.setAttribute(\"data-placement\""));
    assertTrue(xml.contains("window.addEventListener(\"resize\", refreshActiveBarPopup)"));
    assertFalse(xml.contains(".octane-suite-column:hover .octane-bar-popup"));
    assertFalse(xml.contains("transform: translate(-50%"));
    assertFalse(xml.contains("class=\"octane-total\""));
    assertTrue(xml.contains("octane-timer-donut"));
    assertTrue(xml.contains("viewBox=\"0 0 240 240\"") || xml.contains("viewbox=\"0 0 240 240\""));
    assertTrue(xml.contains("shape-rendering: geometricPrecision"));
    assertFalse(xml.contains("octane-timer-border"));
    assertFalse(xml.contains("octane-timer-progress-halo"));
    assertFalse(xml.contains("data-timer-progress-halo=\"true\""));
    assertFalse(xml.contains("octane-timer-extended-progress"));
    assertFalse(xml.contains("data-timer-extended-progress=\"true\""));
    assertTrue(xml.contains("M120 36 A84 84 0 1 0 120 204 A84 84 0 1 0 120 36"));
    assertTrue(xml.contains("stroke-dasharray=\"100 100\""));
    assertFalse(xml.contains("#881113"));
    assertTrue(xml.contains("data-total-seconds=\"2700\""));
    assertTrue(xml.contains("data-extended-total-seconds=\"0\""));
    assertTrue(xml.contains("data-extended-active=\"false\""));
    assertTrue(xml.contains("data-extended-time=\"false\""));
    assertTrue(xml.contains("data-manual-exit-requested=\"false\""));
    assertTrue(xml.contains("data-exit-extended-form=\"true\""));
    assertTrue(xml.contains("data-visible=\"false\""));
    assertTrue(xml.contains("Exit Octane and Continue"));
    assertTrue(xml.contains("exitOctaneAndContinue"));
    assertTrue(xml.contains("data-total-seconds=\"15\""));
    assertTrue(xml.contains("data-timer-value=\"true\""));
    assertTrue(xml.contains("data-timer-progress=\"true\""));
    assertFalse(xml.contains("data-timer-head=\"true\""));
    assertFalse(xml.contains("octane-timer-head-buffer"));
    assertFalse(xml.contains("data-timer-head-buffer=\"true\""));
    assertTrue(xml.contains("data-timer-tail-stop=\"true\""));
    assertTrue(xml.contains("data-timer-mid-stop=\"true\""));
    assertTrue(xml.contains("octane-timer-zone octane-card-zone"));
    assertTrue(xml.contains("octane-report-zone octane-card-zone"));
    assertTrue(xml.contains("border: 1px solid var(--background)"));
    assertFalse(xml.contains("http-equiv=\"refresh\""));
    assertTrue(xml.contains("data-snapshot-url=\"snapshot\""));
    assertTrue(xml.contains("data-current-updated-at=\"2026-05-15T00:00:00Z\""));
    assertTrue(xml.contains("data-live-update-line=\"true\""));
    assertTrue(xml.contains("data-live-update-title=\"true\""));
    assertTrue(xml.contains("data-live-update-status=\"true\""));
    assertTrue(xml.contains("octane-live-update-last-updated"));
    assertTrue(xml.contains("LAST UPDATED:"));
    assertTrue(xml.contains("display: inline"));
    assertTrue(xml.contains("white-space: nowrap"));
    assertFalse(xml.contains(".octane-live-update {\n          margin-top"));
    assertTrue(xml.contains("setLiveUpdateStatus(\"...\", false)"));
    assertTrue(xml.contains("\"+\" + (waitedMillis / 1000).toFixed(1) + \"s\""));
    assertTrue(xml.contains("formatLastUpdatedStatus(payload)"));
    assertFalse(xml.contains(": \"Done.\""));
    assertFalse(xml.contains("Updating report..."));
    assertFalse(xml.contains("Report updated in "));
    assertFalse(xml.contains("Report finished."));
    assertTrue(xml.contains("id=\"octane-report-zone\""));
    assertFalse(xml.contains("#631919"));
    assertTrue(xml.contains("data-octane-progress=\"execution\""));
    assertTrue(xml.contains("data-progress-value=\"100.0\""));
    assertTrue(xml.contains("data-octane-progress=\"pass-rate\""));
    assertTrue(xml.contains("data-progress-value=\"50.0\""));
    assertTrue(xml.contains("data-execution-progress-circle=\"true\""));
    assertFalse(xml.contains("data-execution-progress-head=\"true\""));
    assertFalse(xml.contains("data-execution-progress-head-buffer=\"true\""));
    assertTrue(xml.contains("data-progress-circle=\"true\""));
    assertFalse(xml.contains("data-progress-head=\"true\""));
    assertFalse(xml.contains("data-progress-head-buffer=\"true\""));
    assertTrue(xml.contains("data-progress-value-text=\"true\""));
    assertTrue(xml.contains("octane-execution-progress-gradient"));
    assertTrue(xml.contains("octane-pass-rate-progress-gradient"));
    assertTrue(xml.contains("font-size: 2.6082rem"));
    assertTrue(xml.contains("font-size: 0.91875rem"));
    assertTrue(xml.contains("data-timer-value=\"true\" x=\"120\" y=\"118\""));
    assertTrue(xml.contains("data-timer-unit=\"true\" x=\"120\" y=\"132.4\""));
    assertTrue(xml.contains("data-timeout-title=\"true\""));
    assertTrue(xml.contains("testingTimeSpentMillis"));
    assertTrue(xml.contains("function fitTimerText"));
    assertTrue(xml.contains("116 / (valueLength * 0.56)"));
    assertTrue(xml.contains("displayUnit = \"min + sec\""));
    assertTrue(xml.contains("displayUnit = \"min + min\""));
    assertTrue(xml.contains("Testing Time Remaining"));
    assertTrue(xml.contains("stroke-width: 1"));
    assertTrue(xml.contains("stroke-width: 16"));
    assertTrue(xml.contains("opacity: 1"));
    assertTrue(xml.contains("TIMER_ACTIVE_OPACITY = \"1\""));
    assertTrue(xml.contains("remainingProgress <= 0 ? \"0\" : TIMER_ACTIVE_OPACITY"));
    assertFalse(xml.contains("class=\"octane-timer-head\""));
    assertFalse(xml.contains("data-timer-head=\"true\""));
    assertFalse(xml.contains("TIMER_HEAD_RADIUS"));
    assertFalse(xml.contains("state.head"));
    assertFalse(xml.contains("octane-timer-leading-shadow"));
    assertFalse(xml.contains("data-timer-leading-shadow=\"true\""));
    assertTrue(xml.contains("function timerTrackTotalMillis"));
    assertTrue(xml.contains("return state.totalMillis + state.extendedTotalMillis"));
    assertTrue(xml.contains("function timerTrackRemainingMillis"));
    assertTrue(xml.contains("trackRemaining / trackTotalMillis"));
    assertTrue(xml.contains("function timerColorProgress"));
    assertTrue(xml.contains("(testingTimeSpentMillis(state, now) / state.totalMillis) * 100"));
    assertFalse(xml.contains("function extendedProgressPercent"));
    assertTrue(xml.contains("style=\"height: 100.00%;\""));
    assertTrue(xml.contains("title=\"Ada Tester (suite runs: 4501)\""));
    assertTrue(xml.contains("--octane-color-good: #34C759"));
    assertTrue(xml.contains("--octane-color-good: #30D158"));
    assertTrue(xml.contains("--octane-color-warn: #FFCC00"));
    assertTrue(xml.contains("--octane-color-warn: #FFD60A"));
    assertTrue(xml.contains("--octane-color-bad: #FF3B30"));
    assertTrue(xml.contains("--octane-color-bad: #FF453A"));
    assertTrue(xml.contains("--octane-color-neutral: #007AFF"));
    assertTrue(xml.contains("--octane-color-neutral: #0A84FF"));
    assertTrue(xml.contains("@media (prefers-color-scheme: dark)"));
    assertTrue(xml.contains("html[data-theme=\"dark\"] .octane-dashboard"));
    assertTrue(xml.contains("html[data-theme=\"light\"] .octane-dashboard"));
    assertTrue(xml.contains("var COLOR_GOOD = \"--octane-color-good\""));
    assertTrue(xml.contains("var COLOR_WARN = \"--octane-color-warn\""));
    assertTrue(xml.contains("var COLOR_BAD = \"--octane-color-bad\""));
    assertTrue(xml.contains("var COLOR_NEUTRAL = \"--octane-color-neutral\""));
    assertTrue(xml.contains("PROGRESS_COLOR_PHASES"));
    assertTrue(xml.contains("passRate: ["));
    assertTrue(xml.contains("limit: 20"));
    assertTrue(xml.contains("limit: 49"));
    assertTrue(xml.contains("limit: 50"));
    assertTrue(xml.contains("limit: 79"));
    assertTrue(xml.contains("limit: 89"));
    assertTrue(xml.contains("token: COLOR_GOOD"));
    assertTrue(xml.contains("token: COLOR_WARN"));
    assertTrue(xml.contains("token: COLOR_BAD"));
    assertTrue(xml.contains("token: COLOR_NEUTRAL"));
    assertTrue(xml.contains("colorTokenValue(phases[index].token)"));
    assertFalse(xml.contains("#fb4b4b"));
    assertFalse(xml.contains("#e14343"));
    assertFalse(xml.contains("#ff7e5f"));
    assertFalse(xml.contains("#fff47f"));
    assertFalse(xml.contains("#4caf50"));
    assertFalse(xml.contains("#4bfb4b"));
    assertFalse(xml.contains("#3cc83c"));
    assertFalse(xml.contains("#757575"));
    assertFalse(xml.contains("#95b1c8"));
    assertFalse(xml.contains("COMPLETE_PROGRESS_COLORS"));
    assertFalse(xml.contains("#00e676"));
    assertFalse(xml.contains("#00b85e"));
    assertFalse(xml.contains("progress >= 100"));
    assertTrue(xml.contains("applyGradientStops"));
    assertTrue(xml.contains("progressKind === \"pass-rate\" ? \"passRate\" : progressKind"));
    assertFalse(xml.contains("progressCircle.setAttribute(\"stroke\", executionColor)"));
    assertFalse(xml.contains("progressHalo.setAttribute(\"stroke\", executionColor)"));
    assertTrue(xml.contains("requestAnimationFrame"));
    assertTrue(xml.contains("prefers-reduced-motion: reduce"));
    assertTrue(xml.contains("matchMedia(\"(prefers-reduced-motion: reduce)"));
    assertTrue(xml.contains("scheduleTimerFrame"));
    assertTrue(xml.contains("window.setTimeout(callback, 1000)"));
    assertTrue(xml.contains("moveCardWithKeyboard"));
    assertTrue(xml.contains("performance.now"));
    assertTrue(xml.contains("fetchSnapshot"));
    assertTrue(xml.contains("beginSnapshotRefresh"));
    assertTrue(xml.contains("retryDelayMillis: 500"));
    assertTrue(xml.contains("createExpandButton"));
    assertTrue(xml.contains("createZoneFocusButton"));
    assertTrue(xml.contains("removeZoneFocusButton"));
    assertTrue(xml.contains("restoreZoneFocusButton"));
    assertTrue(xml.contains("decorateReportZone(updatedReportZone)"));
    assertTrue(xml.contains("decorateCardZones(updatedReportZone)"));
    assertTrue(xml.contains("focusZone"));
    assertTrue(xml.contains("focusedKey"));
    assertTrue(xml.contains("setZoneButtonState"));
    assertTrue(xml.contains("removeFocusedZone"));
    assertTrue(xml.contains("findZoneByKey"));
    assertTrue(xml.contains("findCardByKey"));
    assertTrue(xml.contains("function autoShowHeatMapOnCompletion"));
    assertTrue(xml.contains("function autoShowTestMetricsOnCompletion"));
    assertTrue(xml.contains("function autoShowExecutionBreakdownOnCompletion"));
    assertTrue(xml.contains("function autoShowDefectTrendOnCompletion"));
    assertTrue(xml.contains("function completionReached"));
    assertTrue(xml.contains("function manualExitRequested"));
    assertTrue(xml.contains("function primaryTimeoutReached"));
    assertTrue(xml.contains("function totalTimeoutReached"));
    assertTrue(xml.contains("(timeoutSeconds + Math.max(0, timeoutExtendedSeconds)) * 1000"));
    assertTrue(xml.contains("manualExitRequested(payload) || totalTimeoutReached(payload)"));
    assertFalse(xml.contains("function initialAutoFlipBoundaryReached"));
    assertTrue(xml.contains("function autoShowCardViewOnce"));
    assertTrue(xml.contains("function runCompletionAutoFlips"));
    assertTrue(xml.contains("function hasAutoFlipped"));
    assertTrue(xml.contains("var completionAutoFlipState"));
    assertTrue(xml.contains("payload.manualExitRequested === true"));
    assertTrue(xml.contains("data-manual-exit-requested"));
    assertTrue(xml.contains("data-has-auto-flipped"));
    assertTrue(xml.contains("function updateTestMetrics"));
    assertTrue(xml.contains("payload.testMetricsHtml"));
    assertTrue(xml.contains("function updateExecutionStatusDistribution"));
    assertTrue(xml.contains("payload.executionStatusDistributionHtml"));
    assertTrue(xml.contains("autoShowCardViewOnce(payload, \"timer-timeout\", \"metrics\")"));
    assertTrue(
        xml.contains("autoShowCardViewOnce(payload, \"progress-execution\", \"breakdown\")"));
    assertTrue(xml.contains("autoShowCardViewOnce(payload, \"progress-pass-rate\", \"defects\")"));
    assertTrue(xml.contains("runCompletionAutoFlips(currentReportPayload())"));
    assertTrue(xml.contains("runCompletionAutoFlips(payload)"));
    assertTrue(xml.contains("button.getAttribute(\"data-target-view\")"));
    assertTrue(xml.contains("button.getAttribute(\"data-target-view-label\")"));
    assertTrue(xml.contains("card.querySelector(\".octane-view-toggle\")"));
    assertTrue(xml.contains("payload.stateLabel === \"Timed out\""));
    assertTrue(xml.contains("timeoutSeconds * 1000"));
    assertTrue(xml.contains("function executionProgressReached"));
    assertTrue(xml.contains("isFinite(executionProgress) && executionProgress >= 100"));
    assertTrue(
        xml.contains(
            "return timedOut || primaryTimeoutReached(payload)"
                + " || executionProgressReached(payload)"));
    assertTrue(
        xml.contains(
            "return timedOut || manualExitRequested(payload)"
                + " || totalTimeoutReached(payload)"));
    assertTrue(xml.contains("setCardView(card, targetView)"));
    assertTrue(xml.contains("autoShowCardViewOnce(payload, \"timer-poll\", \"heatmap\")"));
    assertTrue(xml.contains("expandedKey"));
    assertTrue(xml.contains("expandedBackdrop.addEventListener(\"click\""));
    assertTrue(xml.contains("setExpandButtonState"));
    assertTrue(xml.contains("removeExpandedState"));
    assertTrue(xml.contains("event.key === \"Escape\""));
    assertTrue(xml.contains("event.target.closest(\".octane-expand-toggle\")"));
    assertTrue(xml.contains("event.target.closest(\".octane-zone-focus-toggle\")"));
    assertTrue(xml.contains("card.setAttribute(\"draggable\", \"false\")"));
    assertTrue(xml.contains("card.classList.contains(\"octane-expanded\")"));
    assertTrue(xml.contains("captureBarPopupRestoreState"));
    assertTrue(xml.contains("barPopupRefreshInProgress = true"));
    assertTrue(xml.contains("restoreBarPopupAfterRefresh(updatedReportZone"));
    assertTrue(xml.contains("barPopupOverlay.innerHTML = source.innerHTML"));
    assertTrue(xml.contains("applyBarPopupDominantColor"));
    assertTrue(xml.contains("statusMetricForColumn(column, \"failed\", \"Failed\")"));
    assertTrue(xml.contains("statusMetricForColumn(column, \"blocked\", \"Blocked\")"));
    assertTrue(xml.contains("statusMetricForColumn(column, \"passed\", \"Passed\")"));
    assertTrue(xml.contains("statusMetricForColumn(column, \"skipped\", \"Skipped\")"));
    assertTrue(xml.contains("statusMetricForColumn(column, \"running\", \"Running\")"));
    assertTrue(xml.contains("metrics[index].count > dominant.count"));
    assertTrue(xml.contains("data-status-\" + key + \"-count"));
    assertTrue(xml.contains("data-status-\" + key + \"-color"));
    assertTrue(
        xml.contains("barPopupOverlay.style.setProperty(\"--octane-popup-border-color\", color)"));
    assertTrue(
        xml.contains("barPopupOverlay.style.removeProperty(\"--octane-popup-border-color\")"));
    assertFalse(xml.contains("barPopupOverlay.style.borderColor = color"));
    assertFalse(xml.contains("barPopupOverlay.style.boxShadow ="));
    assertTrue(xml.contains("findColumnByKeys"));
    assertTrue(xml.contains("replaceWith(updatedReportZone)"));
    assertTrue(xml.contains("payload.passRateProgress"));
    assertTrue(xml.contains("payload.passRateLabel"));
    assertFalse(xml.contains("setInterval(updateTimers, 1000)"));
    assertFalse(xml.contains("transition: stroke-dasharray"));
    assertTrue(xml.contains("draggable=\"true\""));
    assertTrue(xml.contains("overflow-y: hidden"));
    assertTrue(xml.contains("text-overflow: ellipsis"));
    assertTrue(xml.contains(".octane-vertical-bars::before"));
    assertTrue(xml.contains("zoneForCard"));
    assertTrue(xml.contains("targetZone !== draggedZone"));
    assertBarPopupInteractions(page);
  }

  @Test
  public void activeReportUsesSnapshotRefreshInsteadOfMetaRefresh() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setPollIntervalSeconds(15);

    OctaneGateReportAction.attachTo(build, request);

    HtmlPage page = jenkins.createWebClient().getPage(build, OctaneGateReportAction.URL_NAME);
    String xml = page.asXml();
    assertFalse(xml.contains("http-equiv=\"refresh\""));
    assertTrue(xml.contains("data-report-building=\"true\""));
    assertTrue(xml.contains("window.fetch(snapshotUrl"));
    assertTrue(xml.contains("payload.updatedAt !== currentUpdatedAt"));
    assertTrue(xml.contains("payload.building === false"));
  }

  @Test
  public void servesSnapshotJsonWithoutSecrets() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setPollIntervalSeconds(15);

    OctaneGateReportAction action = OctaneGateReportAction.attachTo(build, request);
    action.onFinal(
        OctaneGateReportState.PASSED,
        "ALM Octane suite gate passed.",
        result(),
        new StatusClassifier(
            StatusClassifier.DEFAULT_PASSED_STATUSES,
            StatusClassifier.DEFAULT_FAILED_STATUSES,
            StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
            StatusClassifier.DEFAULT_RUNNING_STATUSES));

    Page jsonPage =
        jenkins
            .createWebClient()
            .getPage(
                jenkins
                    .getURL()
                    .toURI()
                    .resolve(build.getUrl() + OctaneGateReportAction.URL_NAME + "/snapshot")
                    .toURL());
    String json = jsonPage.getWebResponse().getContentAsString();
    JSONObject payload = JSONObject.fromObject(json);

    assertTrue(jsonPage.getWebResponse().getContentType().contains("application/json"));
    assertEquals("2026-05-15T00:00:00Z", payload.getString("updatedAt"));
    assertEquals("03:00:00", payload.getString("updatedAtText"));
    assertTrue(payload.containsKey("startedAt"));
    assertFalse(payload.getBoolean("building"));
    assertEquals("Passed", payload.getString("stateLabel"));
    assertEquals(15, payload.getInt("refreshSeconds"));
    assertEquals(7200, payload.getInt("timeoutSeconds"));
    assertEquals(0, payload.getInt("timeoutExtendedSeconds"));
    assertFalse(payload.getBoolean("extendedTime"));
    assertFalse(payload.getBoolean("manualExitRequested"));
    assertEquals("100%", payload.getString("executionProgressText"));
    assertEquals(50.0, payload.getDouble("passRateProgress"), 0.001);
    assertEquals("50%", payload.getString("passRateProgressText"));
    assertEquals("All Testcase Pass Rate (1 / 2)", payload.getString("passRateLabel"));
    assertTrue(payload.containsKey("testerDetails"));
    assertEquals(95, payload.getJSONObject("testerDetails").getInt("basePassrateFigure"));
    assertEquals(100, payload.getJSONObject("testerDetails").getInt("baseExecutionFigure"));
    assertEquals(1, payload.getJSONObject("testerDetails").getJSONArray("passRateTesters").size());
    assertTrue(payload.getString("testMetricsHtml").contains("octane-test-metrics-grid"));
    assertTrue(
        payload.getString("executionStatusDistributionHtml").contains("octane-execution-half-pie"));
    assertTrue(
        payload.getString("executionStatusDistributionHtml").contains("x=\"160\" y=\"146\""));
    assertTrue(
        payload.getString("executionStatusDistributionHtml").contains("x=\"160\" y=\"172\""));
    assertEquals(4, payload.getJSONObject("testMetrics").getJSONArray("cards").size());
    assertTrue(payload.getJSONObject("defectTrend").getJSONArray("points").size() >= 1);
    assertEquals(0, payload.getJSONObject("defectTrend").getInt("openedTotal"));
    assertEquals("#ff6361", payload.getJSONObject("defectTrend").getString("openedColor"));
    assertEquals("#7BE5B3", payload.getJSONObject("defectTrend").getString("closedColor"));
    assertTrue(payload.getJSONObject("defectTrend").getJSONArray("densityBuckets").size() >= 1);
    assertTrue(
        payload
            .getJSONObject("defectTrend")
            .getJSONArray("points")
            .getJSONObject(0)
            .containsKey("executed"));
    assertTrue(payload.getString("reportZoneHtml").contains("id=\"octane-report-zone\""));
    assertFalse(payload.getString("reportZoneHtml").contains("id=\"octane-timer-zone\""));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("client_id"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("client_secret"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("password"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("credentialsid"));
  }

  @Test
  public void extendedTimeReportShowsManualExitControl() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setTimeoutMinutes(45);
    request.setTimeoutMinutesExtended(10);

    OctaneGateReportAction action = OctaneGateReportAction.attachTo(build, request);
    action.onExtendedTime(
        result(),
        new StatusClassifier(
            StatusClassifier.DEFAULT_PASSED_STATUSES,
            StatusClassifier.DEFAULT_FAILED_STATUSES,
            StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
            StatusClassifier.DEFAULT_RUNNING_STATUSES));

    HtmlPage page = jenkins.createWebClient().getPage(build, OctaneGateReportAction.URL_NAME);
    String xml = page.asXml();

    assertTrue(xml.contains("Extended time"));
    assertTrue(xml.contains("data-extended-time=\"true\""));
    assertTrue(xml.contains("data-extended-total-seconds=\"600\""));
    assertTrue(xml.contains("data-extended-active=\"true\""));
    assertFalse(xml.contains("data-timer-head=\"true\""));
    assertFalse(xml.contains("data-timer-leading-shadow=\"true\""));
    assertFalse(xml.contains("data-timer-extended-progress=\"true\""));
    assertTrue(xml.contains("stroke-dasharray=\"100 100\""));
    assertTrue(xml.contains("data-visible=\"true\""));
    assertTrue(xml.contains("Exit Octane and Continue"));
    assertFalse(xml.contains("Extended time is active"));
    assertFalse(xml.contains("The latest Octane data will still be checked before continuing"));
  }

  private void assertBarPopupInteractions(HtmlPage page) {
    HtmlElement firstBar =
        page.getFirstByXPath(
            "//div[contains(concat(' ', normalize-space(@class), ' '),"
                + " ' octane-vertical-bar ')]");
    HtmlElement overlay = page.getHtmlElementById("octane-bar-popup-overlay");
    HtmlElement reportZone = page.getHtmlElementById("octane-report-zone");
    HtmlElement card =
        firstBar.getFirstByXPath(
            "ancestor::section[contains(concat(' ', normalize-space(@class), ' '),"
                + " ' octane-chart-card ')][1]");

    assertPopupHidesForEveryExitDirection(firstBar, overlay, "normal");

    reportZone.setAttribute("class", reportZone.getAttribute("class") + " octane-zone-focused");
    assertPopupHidesForEveryExitDirection(firstBar, overlay, "group-focused");
    reportZone.setAttribute(
        "class", reportZone.getAttribute("class").replace(" octane-zone-focused", ""));

    card.setAttribute("class", card.getAttribute("class") + " octane-expanded");
    assertPopupHidesForEveryExitDirection(firstBar, overlay, "individual-focused");
    card.setAttribute("class", card.getAttribute("class").replace(" octane-expanded", ""));

    page.executeJavaScript(
        "var first = document.querySelector('.octane-suite-column');"
            + " var adjacent = first.cloneNode(true);"
            + " adjacent.setAttribute('data-bar-key', 'adjacent-bar');"
            + " adjacent.querySelector('.octane-bar-popup-name').textContent = 'Adjacent Tester';"
            + " first.parentNode.appendChild(adjacent);");
    List<HtmlElement> bars =
        page.getByXPath(
            "//div[contains(concat(' ', normalize-space(@class), ' '),"
                + " ' octane-vertical-bar ')]");
    bars.get(0).mouseMove();
    String firstPopup = overlay.asNormalizedText();
    bars.get(1).mouseMove();
    assertTrue(overlay.asNormalizedText().contains("Adjacent Tester"));
    assertFalse(overlay.asNormalizedText().equals(firstPopup));
    bars.get(1).mouseOut();
    assertEquals("true", overlay.getAttribute("aria-hidden"));
  }

  private void assertPopupHidesForEveryExitDirection(
      HtmlElement bar, HtmlElement overlay, String mode) {
    for (String direction : List.of("left", "right", "top", "bottom")) {
      bar.mouseMove();
      assertEquals(mode + " " + direction, "false", overlay.getAttribute("aria-hidden"));
      assertTrue(overlay.getAttribute("class").contains("octane-bar-popup-visible"));
      bar.mouseOut();
      assertEquals(mode + " " + direction, "true", overlay.getAttribute("aria-hidden"));
      assertFalse(overlay.getAttribute("class").contains("octane-bar-popup-visible"));
    }
  }

  private GateResult result() {
    return new GateResult(
        "4501",
        "100% pass",
        true,
        true,
        new GateMetrics(2, 2, 1, 1, 0, 0),
        List.of(
            new RunRecord("1", "one", "passed", "Ada Tester"),
            new RunRecord("2", "two", "failed", "Ada Tester")),
        Map.of(
            "4501",
            List.of(
                new RunRecord("1", "one", "passed", "Ada Tester"),
                new RunRecord("2", "two", "failed", "Ada Tester"))),
        Map.of(),
        Instant.parse("2026-05-15T00:00:00Z"));
  }
}
