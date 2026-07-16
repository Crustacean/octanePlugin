package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class HeadlessBrowserReportScreenshotServiceTest {
  @Test
  public void convertsRemotePathsToFileUrls() throws IOException {
    HeadlessBrowserReportScreenshotService service = new HeadlessBrowserReportScreenshotService();

    assertEquals(
        "file:///tmp/octane%20report/report.html",
        service.toFileUrl("/tmp/octane report/report.html"));
    assertEquals(
        "file:///C:/jenkins/workspace/report.html",
        service.toFileUrl("C:\\jenkins\\workspace\\report.html"));
  }

  @Test
  public void exposesStableWorkspacePaths() {
    assertEquals(
        ".octane-suite-gate/report-email/octane-report-zone.png",
        HeadlessBrowserReportScreenshotService.ATTACHMENT_PATTERN);
    assertTrue(HeadlessBrowserReportScreenshotService.ATTACHMENT_PATTERN.endsWith(".png"));
  }

  @Test
  public void validatesBrowserWithAHeadlessProbeInsteadOfVersionCommand() {
    HeadlessBrowserReportScreenshotService service = new HeadlessBrowserReportScreenshotService();

    List<String> command = service.browserProbeCommand("chrome.exe", "C:\\jenkins\\chrome-profile");

    assertEquals("chrome.exe", command.get(0));
    assertTrue(command.contains("--headless"));
    assertTrue(command.contains("--dump-dom"));
    assertTrue(command.contains("about:blank"));
    assertTrue(command.contains("--user-data-dir=C:\\jenkins\\chrome-profile"));
    assertFalse(command.contains("--version"));
    assertTrue(HeadlessBrowserReportScreenshotService.BROWSER_PROBE_TIMEOUT_SECONDS > 0);
  }

  @Test
  public void capturesWithAnIsolatedProfileAndBoundedRenderingBudget() {
    HeadlessBrowserReportScreenshotService service = new HeadlessBrowserReportScreenshotService();

    List<String> command =
        service.screenshotCommand(
            "chrome.exe",
            "C:\\jenkins\\chrome-profile",
            "C:\\jenkins\\report.png",
            "file:///C:/jenkins/report.html",
            1400,
            800);

    assertTrue(command.contains("--user-data-dir=C:\\jenkins\\chrome-profile"));
    assertTrue(command.contains("--virtual-time-budget=3000"));
    assertTrue(command.contains("--window-size=1400,800"));
    assertTrue(command.contains("--screenshot=C:\\jenkins\\report.png"));
    assertTrue(HeadlessBrowserReportScreenshotService.SCREENSHOT_TIMEOUT_SECONDS > 0);
  }

  @Test
  public void expandsScreenshotHeightWhenCardsStackAtNarrowWidths() {
    assertEquals(
        1640, HeadlessBrowserReportScreenshotService.estimateViewportHeightForCards(4, 600));
    assertEquals(
        880, HeadlessBrowserReportScreenshotService.estimateViewportHeightForCards(4, 1400));
  }
}
