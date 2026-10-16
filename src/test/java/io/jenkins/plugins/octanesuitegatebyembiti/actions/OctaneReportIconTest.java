package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OctaneReportIconTest {
  @Test
  void buildPageRendersTheGraphSymbolFromTheInstalledPlugin(JenkinsRule jenkins) throws Exception {
    var project = jenkins.createFreeStyleProject();
    var build = jenkins.buildAndAssertSuccess(project);
    var action = OctaneGateReportAction.attachTo(build, new GateRequest("octane-prod", "4501"));
    assertEquals("symbol-bar-chart-outline plugin-ionicons-api", action.getIconFileName());
    assertNotNull(jenkins.jenkins.getPluginManager().getPlugin("ionicons-api"));
    String svg = Symbol.get(new SymbolRequest.Builder().withRaw(action.getIconFileName()).build());
    assertTrue(svg.contains("stroke=\"currentColor\""));
    assertEquals(3, svg.split("<rect", -1).length - 1);
    try (var client = jenkins.createWebClient()) {
      var page = client.getPage(build);
      var link = page.getAnchorByText(action.getDisplayName());
      assertNotNull(link.querySelector("svg"));
      assertEquals(3, link.querySelectorAll("svg rect").size());
    }
  }
}
