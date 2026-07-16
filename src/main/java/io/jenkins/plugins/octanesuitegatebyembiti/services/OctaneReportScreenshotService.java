package io.jenkins.plugins.octanesuitegatebyembiti.services;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.actions.OctaneGateReportAction;
import java.io.IOException;

public interface OctaneReportScreenshotService {
  OctaneReportScreenshot capture(
      OctaneGateReportAction action,
      FilePath workspace,
      EnvVars envVars,
      Launcher launcher,
      TaskListener listener,
      String browserPath,
      int viewportWidth,
      String theme)
      throws IOException, InterruptedException;
}
