package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
}
