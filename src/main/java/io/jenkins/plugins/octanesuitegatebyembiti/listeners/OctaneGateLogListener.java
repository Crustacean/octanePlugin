package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.io.IOException;
import java.util.List;

public class OctaneGateLogListener {
  public void logWaiting(TaskListener listener, List<String> suiteRunIds) {
    logWaiting(listener, null, suiteRunIds);
  }

  public void logWaiting(TaskListener listener, GateRequest request, List<String> suiteRunIds) {
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
                displayScopeName(scope.getName()), describeIds(scope.getSuiteRunIds()));
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

  public void logPollResult(TaskListener listener, GateResult result) {
    logMetrics(listener, "Regressions suite runs", result.getMetrics());
    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      if (scopeResult.isSuiteRunScope()) {
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
