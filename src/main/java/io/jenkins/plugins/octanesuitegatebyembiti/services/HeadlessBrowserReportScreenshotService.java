package io.jenkins.plugins.octanesuitegatebyembiti.services;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HeadlessBrowserReportScreenshotService implements OctaneReportScreenshotService {
  public static final String REPORT_EMAIL_DIR = ".octane-suite-gate/report-email";
  public static final String SCREENSHOT_FILE_NAME = "octane-report-zone.png";
  public static final String HTML_FILE_NAME = "octane-report-zone.html";
  public static final String ATTACHMENT_PATTERN = REPORT_EMAIL_DIR + "/" + SCREENSHOT_FILE_NAME;

  private static final List<String> BROWSER_CANDIDATES =
      List.of("chromium", "chromium-browser", "google-chrome", "google-chrome-stable");

  private final OctaneReportZoneHtmlRenderer renderer;

  public HeadlessBrowserReportScreenshotService() {
    this(new OctaneReportZoneHtmlRenderer());
  }

  HeadlessBrowserReportScreenshotService(OctaneReportZoneHtmlRenderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public OctaneReportScreenshot capture(
      OctaneGateReportAction action,
      FilePath workspace,
      EnvVars envVars,
      Launcher launcher,
      TaskListener listener,
      String browserPath,
      int viewportWidth)
      throws IOException, InterruptedException {
    FilePath outputDirectory = workspace.child(REPORT_EMAIL_DIR);
    outputDirectory.mkdirs();
    FilePath htmlFile = outputDirectory.child(HTML_FILE_NAME);
    FilePath screenshotFile = outputDirectory.child(SCREENSHOT_FILE_NAME);

    OctaneGateReportSnapshot snapshot = action.getSnapshot();
    htmlFile.write(renderer.render(snapshot), StandardCharsets.UTF_8.name());

    String browser = resolveBrowser(browserPath, envVars, launcher);
    int width = Math.max(320, viewportWidth);
    int height = estimateViewportHeight(snapshot);
    List<String> command = new ArrayList<>();
    command.add(browser);
    command.add("--headless");
    command.add("--disable-gpu");
    command.add("--disable-dev-shm-usage");
    command.add("--hide-scrollbars");
    command.add("--no-first-run");
    command.add("--no-default-browser-check");
    command.add("--window-size=" + width + "," + height);
    command.add("--screenshot=" + screenshotFile.getRemote());
    command.add(toFileUrl(htmlFile.getRemote()));

    listener.getLogger().println("Capturing Octane report-zone screenshot.");
    int exitCode =
        launcher
            .launch()
            .cmds(command)
            .pwd(outputDirectory)
            .stdout(listener)
            .stderr(listener.getLogger())
            .join();
    if (exitCode != 0) {
      throw new AbortException("Headless browser exited with status " + exitCode + ".");
    }
    if (!screenshotFile.exists() || screenshotFile.length() == 0) {
      throw new AbortException("Headless browser did not create " + SCREENSHOT_FILE_NAME + ".");
    }
    return new OctaneReportScreenshot(htmlFile, screenshotFile, ATTACHMENT_PATTERN);
  }

  String resolveBrowser(String configuredBrowserPath, EnvVars envVars, Launcher launcher)
      throws IOException, InterruptedException {
    String configured = Util.trimToEmpty(configuredBrowserPath);
    if (!configured.isEmpty()) {
      if (canRun(configured, launcher)) {
        return configured;
      }
      throw new AbortException("Configured browserPath could not be executed: " + configured);
    }

    String envBrowser = envVars == null ? "" : Util.trimToEmpty(envVars.get("CHROME_BIN"));
    if (!envBrowser.isEmpty() && canRun(envBrowser, launcher)) {
      return envBrowser;
    }

    for (String candidate : BROWSER_CANDIDATES) {
      if (canRun(candidate, launcher)) {
        return candidate;
      }
    }
    throw new AbortException(
        "Chrome or Chromium was not found on this Jenkins agent. "
            + "Install it or pass browserPath to octaneEmailReport.");
  }

  String toFileUrl(String remotePath) throws IOException {
    String normalized = remotePath.replace('\\', '/');
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    try {
      return "file://" + new URI(null, null, normalized, null).toASCIIString();
    } catch (URISyntaxException e) {
      throw new IOException("Unable to build file URL for " + remotePath, e);
    }
  }

  private boolean canRun(String command, Launcher launcher) throws InterruptedException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      int exitCode =
          launcher
              .launch()
              .cmds(command, "--version")
              .quiet(true)
              .stdout(output)
              .stderr(output)
              .join();
      return exitCode == 0;
    } catch (IOException e) {
      return false;
    }
  }

  private int estimateViewportHeight(OctaneGateReportSnapshot snapshot) {
    int cardCount = snapshot.hasReportSections() ? snapshot.getReportSections().size() * 2 : 1;
    int rows = Math.max(1, (cardCount + 1) / 2);
    return Math.max(800, 120 + rows * 380);
  }
}
