package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.util.List;
import java.util.Map;

public class OctaneGateLogListener {
  public void logWaiting(TaskListener listener, List<String> suiteRunIds) {
    logWaiting(listener, null, suiteRunIds);
  }

  public void logWaiting(TaskListener listener, GateRequest request, List<String> suiteRunIds) {
    listener
        .getLogger()
        .println("Waiting for ALM Octane suite run(s) " + String.join(", ", suiteRunIds) + ".");
    if (request == null) {
      return;
    }

    listener.getLogger().println("Octane gate criteria: " + request.getCriteria());
    if (request.getScopes().isEmpty()) {
      listener.getLogger().println("Octane gate scopes: <none>.");
      return;
    }

    for (OctaneGateScope scope : request.getScopes()) {
      if (scope.isSuiteRunScope()) {
        listener
            .getLogger()
            .printf(
                "Octane gate scope '%s' suite run IDs: %s.%n",
                scope.getName(), describeIds(scope.getSuiteRunIds()));
      } else {
        listener
            .getLogger()
            .printf(
                "Octane gate scope '%s' query IDs: %s. Query: %s%n",
                scope.getName(), describeIds(scope.getReferencedIds()), scope.getQuery());
      }
    }
  }

  public void logPollResult(TaskListener listener, GateResult result) {
    GateMetrics metrics = result.getMetrics();
    listener
        .getLogger()
        .printf(
            "Octane suite run(s) %s global metrics: execution %.2f%%, pass %.2f%%,"
                + " total %d, executed %d, passed %d, failed %d, skipped %d, running %d.%n",
            result.getSuiteRunId(),
            metrics.getExecutionRate(),
            metrics.getPassRate(),
            metrics.getTotal(),
            metrics.getExecuted(),
            metrics.getPassed(),
            metrics.getFailed(),
            metrics.getSkipped(),
            metrics.getRunning());

    for (Map.Entry<String, List<RunRecord>> entry : result.getSuiteRuns().entrySet()) {
      listener
          .getLogger()
          .printf(
              "Octane suite run %s child run statuses: %s%n",
              entry.getKey(), describeRuns(entry.getValue()));
    }

    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      logScopeResult(listener, scopeResult);
    }

    listener
        .getLogger()
        .printf(
            "Octane gate criteria evaluated to %s. Terminal: %s.%n",
            result.isPassed(), result.isTerminal());
  }

  public void logPassed(TaskListener listener) {
    listener.getLogger().println("ALM Octane suite gate passed.");
  }

  private void logScopeResult(TaskListener listener, GateScopeResult scopeResult) {
    GateMetrics scopeMetrics = scopeResult.getMetrics();
    if (scopeResult.isSuiteRunScope()) {
      listener
          .getLogger()
          .printf(
              "Octane scope '%s' suite run IDs %s metrics: execution %.2f%%, pass %.2f%%,"
                  + " total %d, executed %d, passed %d, failed %d, skipped %d, running %d.%n",
              scopeResult.getName(),
              describeIds(scopeResult.getSuiteRunIds()),
              scopeMetrics.getExecutionRate(),
              scopeMetrics.getPassRate(),
              scopeMetrics.getTotal(),
              scopeMetrics.getExecuted(),
              scopeMetrics.getPassed(),
              scopeMetrics.getFailed(),
              scopeMetrics.getSkipped(),
              scopeMetrics.getRunning());
      for (Map.Entry<String, List<RunRecord>> entry : scopeResult.getSuiteRuns().entrySet()) {
        listener
            .getLogger()
            .printf(
                "Octane scope '%s' suite run %s child run statuses: %s%n",
                scopeResult.getName(), entry.getKey(), describeRuns(entry.getValue()));
      }
      return;
    }

    listener
        .getLogger()
        .printf(
            "Octane scope '%s' query IDs %s metrics: execution %.2f%%, pass %.2f%%,"
                + " total %d, executed %d, passed %d, failed %d, skipped %d, running %d.%n",
            scopeResult.getName(),
            describeIds(scopeResult.getQueryIds()),
            scopeMetrics.getExecutionRate(),
            scopeMetrics.getPassRate(),
            scopeMetrics.getTotal(),
            scopeMetrics.getExecuted(),
            scopeMetrics.getPassed(),
            scopeMetrics.getFailed(),
            scopeMetrics.getSkipped(),
            scopeMetrics.getRunning());
    listener
        .getLogger()
        .printf(
            "Octane scope '%s' matched run statuses: %s%n",
            scopeResult.getName(), describeRuns(scopeResult.getRuns()));
  }

  private String describeIds(List<String> ids) {
    if (ids.isEmpty()) {
      return "<none>";
    }
    return String.join(", ", ids);
  }

  private String describeRuns(List<RunRecord> runs) {
    if (runs.isEmpty()) {
      return "<none>";
    }

    StringBuilder builder = new StringBuilder();
    for (RunRecord run : runs) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append(run.getId()).append("=").append(statusOrUnknown(run.getStatus()));
    }
    return builder.toString();
  }

  private String statusOrUnknown(String status) {
    if (status == null || status.isBlank()) {
      return "<unknown>";
    }
    return status;
  }
}
