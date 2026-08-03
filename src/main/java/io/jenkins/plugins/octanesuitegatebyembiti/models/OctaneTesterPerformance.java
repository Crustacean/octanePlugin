package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OctaneTesterPerformance implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String email;
  private final int total;
  private final int executed;
  private final int passed;

  private OctaneTesterPerformance(String email, int total, int executed, int passed) {
    this.email = email;
    this.total = Math.max(0, total);
    this.executed = Math.max(0, executed);
    this.passed = Math.max(0, passed);
  }

  public static List<OctaneTesterPerformance> fromResult(
      GateResult result, StatusClassifier classifier) {
    Map<String, TesterAccumulator> testers = new LinkedHashMap<>();
    addResultRuns(testers, "regressions", result.getSuiteRuns(), result.getRuns(), classifier);
    for (Map.Entry<String, GateScopeResult> entry : result.getScopedResults().entrySet()) {
      GateScopeResult scope = entry.getValue();
      if (!scope.isActive()) {
        continue;
      }
      addResultRuns(
          testers, "scope-" + entry.getKey(), scope.getSuiteRuns(), scope.getRuns(), classifier);
    }

    List<OctaneTesterPerformance> performance = new ArrayList<>();
    for (TesterAccumulator tester : testers.values()) {
      performance.add(tester.toPerformance());
    }
    performance.sort(
        Comparator.comparing(
            (OctaneTesterPerformance tester) -> tester.getEmail(), String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(performance);
  }

  public String getEmail() {
    return email;
  }

  public int getTotal() {
    return total;
  }

  public int getExecuted() {
    return executed;
  }

  public int getPassed() {
    return passed;
  }

  public double getExecutionRate() {
    return GateMetrics.executionRate(executed, total);
  }

  public double getPassRate() {
    return GateMetrics.passRate(passed, executed);
  }

  public String getExecutionRateText() {
    return Util.formatCompactPercentage(getExecutionRate());
  }

  public String getPassRateText() {
    return Util.formatCompactPercentage(getPassRate());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("email", email);
    values.put("total", total);
    values.put("executed", executed);
    values.put("passed", passed);
    values.put("executionRate", getExecutionRate());
    values.put("executionRateText", getExecutionRateText());
    values.put("passRate", getPassRate());
    values.put("passRateText", getPassRateText());
    return values;
  }

  private static void addResultRuns(
      Map<String, TesterAccumulator> testers,
      String source,
      Map<String, List<RunRecord>> suiteRuns,
      List<RunRecord> fallbackRuns,
      StatusClassifier classifier) {
    if (suiteRuns.isEmpty()) {
      addRuns(testers, source, "matched-runs", fallbackRuns, classifier);
      return;
    }
    for (Map.Entry<String, List<RunRecord>> entry : suiteRuns.entrySet()) {
      addRuns(testers, source, entry.getKey(), entry.getValue(), classifier);
    }
  }

  private static void addRuns(
      Map<String, TesterAccumulator> testers,
      String source,
      String suiteRunId,
      List<RunRecord> runs,
      StatusClassifier classifier) {
    for (int index = 0; index < runs.size(); index++) {
      RunRecord run = runs.get(index);
      String email = Util.isBlank(run.getAssignedToName()) ? "Unassigned" : run.getAssignedToName();
      String testerKey = email.trim().toLowerCase(Locale.ROOT);
      testers.putIfAbsent(testerKey, new TesterAccumulator(email));
      String runKey =
          Util.isBlank(run.getId())
              ? source + ":" + suiteRunId + ":anonymous-" + index
              : run.getId();
      OctaneGateStatusBucket status =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      testers.get(testerKey).put(runKey, status);
    }
  }

  private static class TesterAccumulator {
    private final String email;
    private final Map<String, OctaneGateStatusBucket> statusesByRunId = new LinkedHashMap<>();

    private TesterAccumulator(String email) {
      this.email = email;
    }

    private void put(String runId, OctaneGateStatusBucket status) {
      statusesByRunId.put(runId, status);
    }

    private OctaneTesterPerformance toPerformance() {
      int executed = 0;
      int passed = 0;
      for (OctaneGateStatusBucket status : statusesByRunId.values()) {
        if (status.isExecuted()) {
          executed++;
        }
        if (status == OctaneGateStatusBucket.PASSED) {
          passed++;
        }
      }
      return new OctaneTesterPerformance(email, statusesByRunId.size(), executed, passed);
    }
  }
}
