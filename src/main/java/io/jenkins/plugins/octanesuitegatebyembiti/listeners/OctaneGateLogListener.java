package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class OctaneGateLogListener {
  private static final DateTimeFormatter RECONCILIATION_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.of("Africa/Nairobi"));

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
                Util.forLog(displayScopeName(scope.getName())),
                describeIds(
                    scopeSuiteRunIds.getOrDefault(scope.getName(), scope.getSuiteRunIds())));
      }
    }

    listener.getLogger().println("Criteria: " + Util.forLog(request.getCriteria()));
    for (OctaneGateScope scope : request.getScopes()) {
      if (scope.isQueryScope()) {
        listener
            .getLogger()
            .printf(
                "Query scope '%s': IDs %s, query %s%n",
                Util.forLog(scope.getName()),
                describeIds(scope.getReferencedIds()),
                Util.forLog(scope.getQuery()));
      }
    }
  }

  public void logAvailableCriteriaVariables(TaskListener listener, GateRequest request) {
    PrintStream logger = listener.getLogger();
    logger.println("Available Variables for Custom Criteria Formulas:");
    logger.println("- tests_executed (Integer: Passed + Failed + Blocked across all targets)");
    logger.println("- tests_run (Integer: alias of tests_executed)");
    logger.println(
        "- tests_resolved (Integer: Passed + Failed + Blocked + Skipped across all targets)");
    logger.println("- total_tests (Integer: deduplicated total tests across all targets)");
    logger.println("- execution_percentage (Float: alias of total.executionRate)");
    logger.println("- completion_percentage (Float: alias of total.completionRate)");
    logMetricNamespace(logger, "total", "all targeted tests");
    logMetricNamespace(logger, "regressions", "regression tests");
    for (OctaneGateScope scope : request.getScopes()) {
      logMetricNamespace(
          logger,
          Util.forLog(scope.getName()),
          displayScopeName(Util.forLog(scope.getName())) + " scope tests");
    }
    logger.println("- defects.open (Float: open defects as a percentage of total defects raised)");
    logger.println("- defects.openCount (Integer: open defect count)");
    for (OctaneDefectGroup group : request.getDefectGroups()) {
      logDefectVariable(logger, Util.forLog(group.getName()), "configured defect group");
    }
    for (String defectType : OctaneDefectSeveritySummary.getOpenTypes()) {
      logDefectVariable(logger, defectType, "individual defect severity");
    }
    logger.println("Arithmetic operators: +, -, *, /, and nested parentheses.");
  }

  private void logMetricNamespace(PrintStream logger, String namespace, String description) {
    logger.printf("- %s.total (Integer: total %s)%n", namespace, description);
    logger.printf("- %s.executed (Integer: Passed + Failed + Blocked)%n", namespace);
    logger.printf("- %s.resolved (Integer: Passed + Failed + Blocked + Skipped)%n", namespace);
    logger.printf("- %s.passed (Integer: passed tests)%n", namespace);
    logger.printf("- %s.failed (Integer: failed and blocked tests)%n", namespace);
    logger.printf("- %s.skipped (Integer: skipped tests)%n", namespace);
    logger.printf("- %s.running (Integer: planned or running tests)%n", namespace);
    logger.printf("- %s.executionRate (Float: executed / total * 100)%n", namespace);
    logger.printf("- %s.completionRate (Float: resolved / total * 100)%n", namespace);
    logger.printf("- %s.passRate (Float: passed / executed * 100)%n", namespace);
    logger.printf("- %s.failRate (Float: failed / executed * 100)%n", namespace);
  }

  private void logDefectVariable(PrintStream logger, String name, String description) {
    logger.printf(
        "- defects.%s (Float: %s percentage of total defects raised)%n", name, description);
    logger.printf("- defects.%sCount (Integer: %s count)%n", name, description);
  }

  public void logNoDynamicSuiteRuns(
      TaskListener listener, String label, String releaseName, String sprintName) {
    listener
        .getLogger()
        .printf(
            "[WARNING/AUDIT] No active %s suite runs were found for %s. "
                + "Discovery will continue on every poll. Use Jenkins Abort/Cancel to stop this pipeline.%n",
            Util.forLog(label), dynamicSelection(releaseName, sprintName));
  }

  public void logDynamicSuiteSelector(
      TaskListener listener, String label, String releaseName, String sprintName) {
    listener
        .getLogger()
        .printf(
            "[INFO/AUDIT] %s suite runs use continuous discovery for %s.%n",
            Util.forLog(label), dynamicSelection(releaseName, sprintName));
  }

  private String dynamicSelection(String releaseName, String sprintName) {
    String release = "release '" + Util.forLog(releaseName) + "'";
    return Util.isBlank(sprintName)
        ? release
        : release + " and sprint '" + Util.forLog(sprintName) + "'";
  }

  public void logSuiteRunsAdded(TaskListener listener, String label, List<String> suiteRunIds) {
    listener.getLogger().printf("%s [ADDED]%n", Util.forLog(String.join(",", suiteRunIds)));
  }

  public void logSuiteRunsRemoved(TaskListener listener, String label, List<String> suiteRunIds) {
    listener.getLogger().printf("%s [DELETED]%n", Util.forLog(String.join(",", suiteRunIds)));
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
    listener
        .getLogger()
        .println("FINALIZING: fetching the authoritative final state from ALM Octane.");
  }

  public void logFinalRefreshSkipped(TaskListener listener, IOException e) {
    listener
        .getLogger()
        .println("Skipped final ALM Octane refresh: " + Util.forLog(e.getMessage()));
  }

  public void logFinalReconciliationCompleted(TaskListener listener, Instant completedAt) {
    listener
        .getLogger()
        .println(
            "Final ALM Octane reconciliation completed at "
                + RECONCILIATION_TIME_FORMATTER.format(completedAt)
                + ".");
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
    listener.getLogger().println("Octane Gate Report: " + Util.forLog(reportUrl));
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
    return Util.forLog(String.join(", ", ids));
  }
}
