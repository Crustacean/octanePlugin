package io.jenkins.plugins.octanesuitegatebyembiti.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.services.OctaneScaleTestFixture;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.regex.Pattern;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneScaleReportActionTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void denseReportUsesSmallMetadataEtagAndDeferredClientRendering() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    GateRequest request = new GateRequest("octane-prod", "suite-0");
    OctaneGateReportAction action = OctaneGateReportAction.attachTo(build, request);
    action.onPoll(OctaneScaleTestFixture.result(0, 500, 1), OctaneScaleTestFixture.classifier());

    long buildXmlBytes = Files.size(build.getRootDir().toPath().resolve("build.xml"));
    assertTrue(buildXmlBytes < 100_000L);
    assertFalse(
        Files.readString(build.getRootDir().toPath().resolve("build.xml")).contains("<snapshot"));
    assertNotNull(action.getReportDataChecksum());
    assertFalse(action.getReportDataChecksum().isBlank());

    URL reportUrl =
        jenkins
            .getURL()
            .toURI()
            .resolve(build.getUrl() + OctaneGateReportAction.URL_NAME + "/")
            .toURL();
    HtmlPage report = jenkins.createWebClient().getPage(reportUrl);
    String reportXml = report.asXml();
    assertTrue(reportXml.contains("data-client-rendered=\"true\""));
    assertTrue(reportXml.contains("Loading report data"));
    long domNodes = Pattern.compile("<[A-Za-z][^!?/]*?").matcher(reportXml).results().count();
    assertTrue("initial DOM should remain below 5,000 nodes", domNodes < 5_000L);

    URI reportUri = reportUrl.toURI();
    URL snapshotUrl = reportUri.resolve("snapshot").toURL();
    Page snapshot = jenkins.createWebClient().getPage(snapshotUrl);
    String snapshotEtag = snapshot.getWebResponse().getResponseHeaderValue("ETag");
    assertNotNull(snapshotEtag);
    assertTrue(snapshot.getWebResponse().getContentLength() < 250_000L);

    WebRequest unchangedRequest = new WebRequest(snapshotUrl);
    unchangedRequest.setAdditionalHeader("If-None-Match", snapshotEtag);
    Page unchanged = jenkins.createWebClient().getPage(unchangedRequest);
    assertEquals(304, unchanged.getWebResponse().getStatusCode());

    URL dataUrl = reportUri.resolve("data").toURL();
    Page index = jenkins.createWebClient().getPage(dataUrl);
    long indexBytes = index.getWebResponse().getContentLength();
    assertTrue(indexBytes < 250_000L);
    assertTrue(index.getWebResponse().getContentAsString().contains("\"barCount\":500"));
    String dataEtag = index.getWebResponse().getResponseHeaderValue("ETag");
    assertNotNull(dataEtag);
    WebRequest unchangedDataRequest = new WebRequest(dataUrl);
    unchangedDataRequest.setAdditionalHeader("If-None-Match", dataEtag);
    Page unchangedData = jenkins.createWebClient().getPage(unchangedDataRequest);
    assertEquals(304, unchangedData.getWebResponse().getStatusCode());

    URL sectionUrl = reportUri.resolve("data?section=0&cursor=0&limit=80").toURL();
    Page section = jenkins.createWebClient().getPage(sectionUrl);
    String sectionJson = section.getWebResponse().getContentAsString();
    assertTrue(sectionJson.contains("\"totalBars\":500"));
    assertTrue(sectionJson.contains("\"nextCursor\":80"));

    URL nextSectionUrl = reportUri.resolve("data?section=0&cursor=80&limit=80").toURL();
    Page nextSection = jenkins.createWebClient().getPage(nextSectionUrl);
    assertTrue(nextSection.getWebResponse().getContentAsString().contains("\"cursor\":80"));
    System.out.printf(
        "Octane report acceptance: buildXmlBytes=%d initialDomNodes=%d indexBytes=%d%n",
        buildXmlBytes, domNodes, indexBytes);
  }

  @Test
  public void readsLegacyInlineSnapshotWhenArtifactMetadataIsAbsent() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    OctaneGateReportAction action = new OctaneGateReportAction();
    Field legacySnapshot = OctaneGateReportAction.class.getDeclaredField("snapshot");
    legacySnapshot.setAccessible(true);
    legacySnapshot.set(action, OctaneScaleTestFixture.snapshot(0, 1, 1));
    Field snapshotCache = OctaneGateReportAction.class.getDeclaredField("snapshotCache");
    snapshotCache.setAccessible(true);
    snapshotCache.set(action, null);
    action.onAttached(build);

    assertEquals("2026-07-16T12:00:00Z", action.getSnapshot().getUpdatedAt());
  }
}
