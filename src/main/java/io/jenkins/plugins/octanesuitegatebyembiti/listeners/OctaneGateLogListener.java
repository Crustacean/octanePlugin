package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class OctaneGateLogListener {
  public void logWaiting(TaskListener listener, List<String> suiteRunIds) {
    logWaiting(listener, null, suiteRunIds);
  }

  public void logLookupContext(TaskListener listener, String sharedSpaceId, String workspaceId) {
    listener
        .getLogger()
        .println(
            "ALM Octane lookup context: shared space "
                + sharedSpaceId
                + ", workspace "
                + workspaceId
                + ".");
  }

  public void logWaiting(TaskListener listener, GateRequest request, List<String> suiteRunIds) {
    logWaiting(listener, request, suiteRunIds, Map.of());
  }

  public void logWaiting(
      TaskListener listener,
      GateRequest request,
      List<String> suiteRunIds,
      Map<String, List<String>> scopeSuiteRunIds) {
    listener.getLogger().println("Waiting for ALM Octane suite run(s)");
    listener.getLogger().println("Regressions suite runs: " + describeIds(suiteRunIds));
    if (request == null) {
      return;
    }

    for (OctaneGateScope scope : request.getScopes()) {
      if (scope.isSuiteRunScope()) {
        listener
            .getLogger()
            .printf(
                "%s suite runs: %s%n",
                displayScopeName(scope.getName()),
                describeIds(
                    scopeSuiteRunIds.getOrDefault(scope.getName(), scope.getSuiteRunIds())));
      }
    }

    listener.getLogger().println("Criteria: " + request.getCriteria());
    for (OctaneGateScope scope : request.getScopes()) {
      if (scope.isQueryScope()) {
        listener
            .getLogger()
            .printf(
                "Query scope '%s': IDs %s, query %s%n",
                scope.getName(), describeIds(scope.getReferencedIds()), scope.getQuery());
      }
    }
  }

  public void logNoDynamicSuiteRuns(
      TaskListener listener, String label, String releaseName, String sprintName) {
    listener
        .getLogger()
        .printf(
            "[WARNING/AUDIT] No active %s suite runs were found for release '%s' and sprint '%s'. "
                + "Discovery will continue on every poll. Use Jenkins Abort/Cancel to stop this pipeline.%n",
            label, releaseName, sprintName);
  }

  public void logDynamicSuiteSelector(
      TaskListener listener, String label, String releaseName, String sprintName) {
    listener
        .getLogger()
        .printf(
            "[INFO/AUDIT] %s suite runs use continuous discovery for release '%s' and sprint '%s'.%n",
            label, releaseName, sprintName);
  }

  public void logSuiteRunsAdded(TaskListener listener, String label, List<String> suiteRunIds) {
    listener.getLogger().printf("%s [ADDED]%n", String.join(",", suiteRunIds));
  }

  public void logSuiteRunsRemoved(TaskListener listener, String label, List<String> suiteRunIds) {
    listener.getLogger().printf("%s [DELETED]%n", String.join(",", suiteRunIds));
  }

  public void logRegressionEvaluationSkipped(TaskListener listener) {
    listener
        .getLogger()
        .println(
            "[INFO/AUDIT] Regression suite-run evaluation is disabled because its selection is "
                + "empty or entirely owned by the critical scope. Skipping regression criteria.");
  }

  public void logPollResult(TaskListener listener, GateResult result) {
    if (result.isRegressionEvaluationEnabled()) {
      logMetrics(listener, "Regressions suite runs", result.getMetrics());
    }
    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      if (scopeResult.isActive() && scopeResult.isSuiteRunScope()) {
        logMetrics(
            listener,
            displayScopeName(scopeResult.getName()) + " suite runs",
            scopeResult.getMetrics());
      } else {
        logMetrics(
            listener,
            displayScopeName(scopeResult.getName()) + " query scope",
            scopeResult.getMetrics());
      }
    }
    listener.getLogger().println();
  }

  public void logFinalRefresh(TaskListener listener) {
    listener.getLogger().println("Refreshing ALM Octane suite runs before completing the gate.");
  }

  public void logFinalRefreshSkipped(TaskListener listener, IOException e) {
    listener.getLogger().println("Skipped final ALM Octane refresh: " + e.getMessage());
  }

  public void logExtendedTimeStarted(TaskListener listener, int timeoutMinutesExtended) {
    listener
        .getLogger()
        .println(
            "Primary Octane timeout elapsed. Continuing in extended time for "
                + timeoutMinutesExtended
                + " minute(s).");
  }

  public void logExtendedTimeExpired(TaskListener listener) {
    listener.getLogger().println("Extended Octane timeout elapsed. Finalizing the gate.");
  }

  public void logManualExitRequested(TaskListener listener) {
    listener
        .getLogger()
        .println("Exit Octane and Continue requested. Finalizing the gate with latest data.");
  }

  public void logPassed(TaskListener listener) {
    listener.getLogger().println("ALM Octane suite gate passed.");
  }

  public void logReportLink(TaskListener listener, String reportUrl) {
    listener.getLogger().println("Octane Gate Report: " + reportUrl);
  }

  private void logMetrics(TaskListener listener, String label, GateMetrics metrics) {
    listener
        .getLogger()
        .printf(
            "%s: execution %.2f%%, pass %.2f%%, total %d, executed %d, passed %d,"
                + " failed %d, skipped %d, running %d.%n",
            label,
            metrics.getExecutionRate(),
            metrics.getPassRate(),
            metrics.getTotal(),
            metrics.getExecuted(),
            metrics.getPassed(),
            metrics.getFailed(),
            metrics.getSkipped(),
            metrics.getRunning());
  }

  private String displayScopeName(String scopeName) {
    if (scopeName == null || scopeName.isBlank()) {
      return "Scope";
    }
    return scopeName.substring(0, 1).toUpperCase() + scopeName.substring(1);
  }

  private String describeIds(List<String> ids) {
    if (ids.isEmpty()) {
      return "<none>";
    }
    return String.join(", ", ids);
  }
}
