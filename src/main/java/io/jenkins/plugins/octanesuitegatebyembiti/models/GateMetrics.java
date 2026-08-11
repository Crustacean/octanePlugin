package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.services.CriteriaException;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public class GateMetrics implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Map<String, ToDoubleFunction<GateMetrics>> METRIC_VALUES =
      Map.ofEntries(
          Map.entry("total", metrics -> metrics.total),
          Map.entry("executed", metrics -> metrics.getExecuted()),
          Map.entry("passed", metrics -> metrics.passed),
          Map.entry("failed", metrics -> metrics.failed),
          Map.entry("skipped", metrics -> metrics.skipped),
          Map.entry("running", metrics -> metrics.running),
          Map.entry("executionrate", metrics -> metrics.getExecutionRate()),
          Map.entry("passrate", metrics -> metrics.getPassRate()),
          Map.entry("failrate", metrics -> metrics.getFailRate()));

  private final int total;
  private final int passed;
  private final int failed;
  private final int skipped;
  private final int running;

  public GateMetrics(
      int total, int ignoredExecuted, int passed, int failed, int skipped, int running) {
    this.total = total;
    this.passed = passed;
    this.failed = failed;
    this.skipped = skipped;
    this.running = running;
  }

  public static GateMetrics fromRuns(List<RunRecord> runs, StatusClassifier classifier) {
    int passed = 0;
    int failed = 0;
    int skipped = 0;
    int running = 0;

    for (RunRecord run : runs) {
      StatusClassifier.Outcome outcome = classifier.classify(run.getStatus());
      if (outcome == StatusClassifier.Outcome.PASSED) {
        passed++;
      } else if (outcome == StatusClassifier.Outcome.FAILED
          || outcome == StatusClassifier.Outcome.BLOCKED) {
        failed++;
      } else if (outcome == StatusClassifier.Outcome.NEUTRAL) {
        skipped++;
      } else {
        running++;
      }
    }

    int total = runs.size();
    int executed = executedCount(passed, failed, 0);
    return new GateMetrics(total, executed, passed, failed, skipped, running);
  }

  public int getTotal() {
    return total;
  }

  public int getExecuted() {
    // Failed includes both failed and blocked outcomes. Skipped and planned/running tests do not
    // participate in execution or pass-rate calculations.
    return executedCount(passed, failed, 0);
  }

  public int getPassed() {
    return passed;
  }

  public int getFailed() {
    return failed;
  }

  public int getSkipped() {
    return skipped;
  }

  public int getRunning() {
    return running;
  }

  public double getExecutionRate() {
    return executionRate(getExecuted(), total);
  }

  public double getPassRate() {
    return passRate(passed, getExecuted());
  }

  public double getFailRate() {
    return Util.percentage(Math.max(0, failed), getExecuted());
  }

  public boolean isTerminal() {
    return running == 0;
  }

  double value(String metricName) {
    String normalized = normalizeMetricName(metricName);
    ToDoubleFunction<GateMetrics> metric = METRIC_VALUES.get(normalized);
    if (metric == null) {
      throw new CriteriaException("Unknown metric: " + metricName);
    }
    return metric.applyAsDouble(this);
  }

  public static boolean isPercentageMetric(String metricName) {
    String normalized = normalizeMetricName(metricName);
    return "executionrate".equals(normalized)
        || "passrate".equals(normalized)
        || "failrate".equals(normalized);
  }

  Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("total", total);
    values.put("executed", getExecuted());
    values.put("passed", passed);
    values.put("failed", failed);
    values.put("skipped", skipped);
    values.put("running", running);
    values.put("executionRate", getExecutionRate());
    values.put("passRate", getPassRate());
    values.put("failRate", getFailRate());
    return values;
  }

  public static int executedCount(int passed, int failed, int blocked) {
    return Math.max(0, passed) + Math.max(0, failed) + Math.max(0, blocked);
  }

  public static double executionRate(int executed, int total) {
    return Util.percentage(executed, total);
  }

  public static double passRate(int passed, int executed) {
    return Util.percentage(Math.max(0, passed), executed);
  }

  static String normalizeMetricName(String metricName) {
    String normalized = Util.trimToEmpty(metricName).replace("_", "").replace("-", "");
    normalized = normalized.toLowerCase();
    if ("execution".equals(normalized) || "executions".equals(normalized)) {
      return "executionrate";
    }
    if ("pass".equals(normalized) || "passes".equals(normalized)) {
      return "passrate";
    }
    if ("fail".equals(normalized) || "fails".equals(normalized)) {
      return "failrate";
    }
    return normalized;
  }
}
