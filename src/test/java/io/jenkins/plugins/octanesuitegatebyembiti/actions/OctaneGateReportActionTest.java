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
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
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
    assertTrue(text.contains("Testing Time Remaining"));
    assertTrue(text.contains("Status Check"));
    assertTrue(text.contains("Testing Session Timer"));
    assertTrue(text.contains("Execution Progress"));
    assertTrue(text.contains("All Testcase execution"));
    assertTrue(text.contains("Execution Pass Rate"));
    assertTrue(text.contains("All Testcase Pass Rate (1 / 2)"));
    assertTrue(text.contains("Total: 2"));
    assertTrue(text.contains("Total Suiteruns: 1"));
    assertTrue(text.contains("Ada Tester"));
    assertTrue(text.contains("ada tester"));
    assertFalse(text.contains("Total Testcases"));
    assertFalse(text.contains("Global + Critical execution"));
    assertFalse(text.contains("Execution 100.0%, pass"));
    assertFalse(text.contains("Suite runs: 4501"));
    assertTrue(xml.contains("#009900"));
    assertTrue(xml.contains("octane-donut"));
    assertTrue(xml.contains("octane-distribution-meta"));
    assertTrue(xml.contains("octane-total-label"));
    assertTrue(xml.contains("octane-donut-label"));
    assertTrue(
        xml.contains("viewBox=\"-10 -10 120 120\"") || xml.contains("viewbox=\"-10 -10 120 120\""));
    assertTrue(xml.contains("max-width: 280px"));
    assertFalse(xml.contains("min-height: 294px"));
    assertTrue(xml.contains("overflow: visible"));
    assertTrue(xml.contains("r=\"46\" fill="));
    assertTrue(xml.contains("r=\"30\""));
    assertTrue(xml.contains(">50%</text>"));
    assertTrue(xml.contains("border-radius: 14px"));
    assertFalse(xml.contains("border-radius: 6px"));
    assertTrue(xml.contains("border-color: #4391F5"));
    assertTrue(xml.contains("font-size: 0.85rem"));
    assertTrue(xml.contains("height: 1.15rem"));
    assertTrue(xml.contains("letter-spacing: 0.04rem"));
    assertTrue(xml.contains("width: 1.15rem"));
    assertTrue(xml.contains("octane-card-actions"));
    assertTrue(xml.contains("octane-expand-toggle"));
    assertTrue(xml.contains("octane-icon-expand"));
    assertTrue(xml.contains("octane-icon-collapse"));
    assertTrue(xml.contains("aria-expanded=\"false\""));
    assertTrue(xml.contains("Expand widget"));
    assertTrue(xml.contains("octane-expanded-backdrop"));
    assertTrue(xml.contains(".octane-chart-card.octane-expanded"));
    assertTrue(xml.contains("inset: 1rem"));
    assertTrue(xml.contains("resize: none"));
    assertTrue(xml.contains("max-height: min(76vh, 76vw)"));
    assertTrue(xml.contains("grid-template-rows: minmax(0, 1fr) 1.7rem"));
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
    assertTrue(xml.contains("octane-y-axis-label"));
    assertTrue(xml.contains(">Test Runs<"));
    assertTrue(xml.contains("#576779"));
    assertFalse(xml.contains("#CCCCCC"));
    assertTrue(xml.contains("column-gap: 0.09rem"));
    assertTrue(xml.contains("grid-template-columns: 1.35rem max-content minmax(0, 1fr)"));
    assertTrue(xml.contains("grid-template-rows: 260px 1.7rem"));
    assertTrue(xml.contains("octane-bar-plot"));
    assertTrue(xml.contains("octane-vertical-bars"));
    assertTrue(xml.contains("octane-x-axis-labels"));
    assertTrue(xml.contains("octane-axis-label-column"));
    assertTrue(xml.contains("overflow-x: hidden"));
    assertFalse(xml.contains("overflow-x: auto"));
    assertTrue(xml.contains("flex: 1 1 5.6rem"));
    assertTrue(xml.contains("width: clamp(0.85rem, 62%, 2.6rem)"));
    assertTrue(xml.contains("font-size: clamp(0.53rem, 0.8vw, 0.7rem)"));
    assertTrue(xml.contains("text-align: center"));
    assertTrue(xml.contains("transform: none"));
    assertFalse(xml.contains("rotate(-45deg)"));
    assertTrue(xml.contains("octane-bar-popup"));
    assertTrue(xml.contains("min-width: 10.92rem"));
    assertTrue(xml.contains("position: fixed"));
    assertTrue(xml.contains("font-size: 0.644rem"));
    assertTrue(xml.contains("overflow-wrap: anywhere"));
    assertTrue(xml.contains("octane-bar-popup-name"));
    assertTrue(xml.contains("octane-bar-popup-row"));
    assertTrue(xml.contains("octane-bar-popup-total"));
    assertTrue(xml.contains("octane-bar-popup-visible"));
    assertTrue(xml.contains("positionBarPopup"));
    assertTrue(xml.contains("event.clientX + gap + popupWidth"));
    assertTrue(xml.contains("popup.setAttribute(\"data-placement\""));
    assertTrue(xml.contains("window.addEventListener(\"resize\", hideBarPopup)"));
    assertFalse(xml.contains(".octane-suite-column:hover .octane-bar-popup"));
    assertFalse(xml.contains("transform: translate(-50%"));
    assertFalse(xml.contains("class=\"octane-total\""));
    assertTrue(xml.contains("octane-timer-donut"));
    assertTrue(xml.contains("viewBox=\"0 0 240 240\"") || xml.contains("viewbox=\"0 0 240 240\""));
    assertTrue(xml.contains("shape-rendering: geometricPrecision"));
    assertFalse(xml.contains("octane-timer-border"));
    assertTrue(xml.contains("octane-timer-progress-halo"));
    assertTrue(xml.contains("data-timer-progress-halo=\"true\""));
    assertTrue(xml.contains("data-total-seconds=\"2700\""));
    assertTrue(xml.contains("data-total-seconds=\"15\""));
    assertTrue(xml.contains("data-timer-value=\"true\""));
    assertTrue(xml.contains("data-timer-progress=\"true\""));
    assertTrue(xml.contains("data-timer-head=\"true\""));
    assertTrue(xml.contains("data-timer-tail-stop=\"true\""));
    assertTrue(xml.contains("data-timer-mid-stop=\"true\""));
    assertTrue(xml.contains("octane-timer-zone octane-card-zone"));
    assertTrue(xml.contains("octane-report-zone octane-card-zone"));
    assertTrue(xml.contains("border: 1px solid var(--background)"));
    assertFalse(xml.contains("http-equiv=\"refresh\""));
    assertTrue(xml.contains("data-snapshot-url=\"snapshot\""));
    assertTrue(xml.contains("data-current-updated-at=\"2026-05-15T00:00:00Z\""));
    assertTrue(xml.contains("data-live-update-status=\"true\""));
    assertTrue(xml.contains("display: inline"));
    assertTrue(xml.contains("white-space: nowrap"));
    assertFalse(xml.contains(".octane-live-update {\n          margin-top"));
    assertTrue(xml.contains("setLiveUpdateStatus(\"...\")"));
    assertTrue(xml.contains("\"+\" + (waitedMillis / 1000).toFixed(1) + \"s\""));
    assertTrue(xml.contains(": \"Done.\""));
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
    assertTrue(xml.contains("data-execution-progress-head=\"true\""));
    assertTrue(xml.contains("data-progress-circle=\"true\""));
    assertTrue(xml.contains("data-progress-head=\"true\""));
    assertTrue(xml.contains("data-progress-value-text=\"true\""));
    assertTrue(xml.contains("octane-execution-progress-gradient"));
    assertTrue(xml.contains("octane-pass-rate-progress-gradient"));
    assertTrue(xml.contains("font-size: 2.6082rem"));
    assertTrue(xml.contains("font-size: 0.91875rem"));
    assertTrue(xml.contains("data-timer-value=\"true\" x=\"120\" y=\"118\""));
    assertTrue(xml.contains("data-timer-unit=\"true\" x=\"120\" y=\"132.4\""));
    assertTrue(xml.contains("stroke-width: 16"));
    assertTrue(xml.contains("style=\"height: 100.00%;\""));
    assertTrue(xml.contains("title=\"2 tests\""));
    assertTrue(xml.contains("TIMER_CENTER = 120"));
    assertTrue(xml.contains("TIMER_RADIUS = 84"));
    assertTrue(xml.contains("PROGRESS_COLOR_PHASES"));
    assertTrue(xml.contains("limit: 5"));
    assertTrue(xml.contains("passRate: ["));
    assertTrue(xml.contains("limit: 20"));
    assertTrue(xml.contains("limit: 60"));
    assertTrue(xml.contains("limit: 94"));
    assertTrue(xml.contains("limit: 95"));
    assertTrue(xml.contains("#fb4b4b"));
    assertTrue(xml.contains("#e14343"));
    assertTrue(xml.contains("#ff7e5f"));
    assertTrue(xml.contains("#fff47f"));
    assertTrue(xml.contains("#4caf50"));
    assertTrue(xml.contains("#4bfb4b"));
    assertTrue(xml.contains("#3cc83c"));
    assertTrue(xml.contains("#757575"));
    assertTrue(xml.contains("#95b1c8"));
    assertTrue(xml.contains("COMPLETE_PROGRESS_COLORS"));
    assertTrue(xml.contains("execution: \"#3cc83c\""));
    assertTrue(xml.contains("passRate: \"#3cc83c\""));
    assertTrue(xml.contains("timeout: \"#e14343\""));
    assertTrue(xml.contains("poll: \"#95b1c8\""));
    assertFalse(xml.contains("#00e676"));
    assertFalse(xml.contains("#00b85e"));
    assertTrue(xml.contains("progress >= 100"));
    assertTrue(xml.contains("applyGradientStops"));
    assertTrue(xml.contains("progressKind === \"pass-rate\" ? \"passRate\" : progressKind"));
    assertFalse(xml.contains("progressCircle.setAttribute(\"stroke\", executionColor)"));
    assertFalse(xml.contains("progressHalo.setAttribute(\"stroke\", executionColor)"));
    assertTrue(xml.contains("requestAnimationFrame"));
    assertTrue(xml.contains("performance.now"));
    assertTrue(xml.contains("fetchSnapshot"));
    assertTrue(xml.contains("beginSnapshotRefresh"));
    assertTrue(xml.contains("retryDelayMillis: 500"));
    assertTrue(xml.contains("createExpandButton"));
    assertTrue(xml.contains("decorateReportZone(updatedReportZone)"));
    assertTrue(xml.contains("findCardByKey"));
    assertTrue(xml.contains("expandedKey"));
    assertTrue(xml.contains("setExpandButtonState"));
    assertTrue(xml.contains("removeExpandedState"));
    assertTrue(xml.contains("event.key === \"Escape\""));
    assertTrue(xml.contains("event.target.closest(\".octane-expand-toggle\")"));
    assertTrue(xml.contains("card.setAttribute(\"draggable\", \"false\")"));
    assertTrue(xml.contains("card.classList.contains(\"octane-expanded\")"));
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
                new URL(
                    jenkins.getURL(),
                    build.getUrl() + OctaneGateReportAction.URL_NAME + "/snapshot"));
    String json = jsonPage.getWebResponse().getContentAsString();
    JSONObject payload = JSONObject.fromObject(json);

    assertTrue(jsonPage.getWebResponse().getContentType().contains("application/json"));
    assertEquals("2026-05-15T00:00:00Z", payload.getString("updatedAt"));
    assertEquals("03:00:00", payload.getString("updatedAtText"));
    assertFalse(payload.getBoolean("building"));
    assertEquals("Passed", payload.getString("stateLabel"));
    assertEquals(15, payload.getInt("refreshSeconds"));
    assertEquals("100%", payload.getString("executionProgressText"));
    assertEquals(50.0, payload.getDouble("passRateProgress"), 0.001);
    assertEquals("50%", payload.getString("passRateProgressText"));
    assertEquals("All Testcase Pass Rate (1 / 2)", payload.getString("passRateLabel"));
    assertTrue(payload.getString("reportZoneHtml").contains("id=\"octane-report-zone\""));
    assertFalse(payload.getString("reportZoneHtml").contains("id=\"octane-timer-zone\""));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("client_id"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("client_secret"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("password"));
    assertFalse(json.toLowerCase(Locale.ROOT).contains("credentialsid"));
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
