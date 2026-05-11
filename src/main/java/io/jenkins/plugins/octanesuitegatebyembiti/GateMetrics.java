package io.jenkins.plugins.octanesuitegatebyembiti;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GateMetrics implements Serializable {
  private static final long serialVersionUID = 1L;

  private final int total;
  private final int executed;
  private final int passed;
  private final int failed;
  private final int skipped;
  private final int running;

  GateMetrics(int total, int executed, int passed, int failed, int skipped, int running) {
    this.total = total;
    this.executed = executed;
    this.passed = passed;
    this.failed = failed;
    this.skipped = skipped;
    this.running = running;
  }

  static GateMetrics fromRuns(List<RunRecord> runs, StatusClassifier classifier) {
    int passed = 0;
    int failed = 0;
    int skipped = 0;
    int running = 0;

    for (RunRecord run : runs) {
      StatusClassifier.Outcome outcome = classifier.classify(run.getStatus());
      if (outcome == StatusClassifier.Outcome.PASSED) {
        passed++;
      } else if (outcome == StatusClassifier.Outcome.FAILED) {
        failed++;
      } else if (outcome == StatusClassifier.Outcome.NEUTRAL) {
        skipped++;
      } else {
        running++;
      }
    }

    int total = runs.size();
    int executed = passed + failed + skipped;
    return new GateMetrics(total, executed, passed, failed, skipped, running);
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
    return total == 0 ? 0.0 : percentage(executed, total);
  }

  public double getPassRate() {
    return executed == 0 ? 0.0 : percentage(passed, executed);
  }

  public double getFailRate() {
    return executed == 0 ? 0.0 : percentage(failed, executed);
  }

  public boolean isTerminal() {
    return running == 0;
  }

  double value(String metricName) {
    String normalized = normalizeMetricName(metricName);
    switch (normalized) {
      case "total":
        return total;
      case "executed":
        return executed;
      case "passed":
        return passed;
      case "failed":
        return failed;
      case "skipped":
        return skipped;
      case "running":
        return running;
      case "executionrate":
        return getExecutionRate();
      case "passrate":
        return getPassRate();
      case "failrate":
        return getFailRate();
      default:
        throw new CriteriaException("Unknown metric: " + metricName);
    }
  }

  Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("total", total);
    values.put("executed", executed);
    values.put("passed", passed);
    values.put("failed", failed);
    values.put("skipped", skipped);
    values.put("running", running);
    values.put("executionRate", getExecutionRate());
    values.put("passRate", getPassRate());
    values.put("failRate", getFailRate());
    return values;
  }

  private static double percentage(int numerator, int denominator) {
    return numerator * 100.0 / denominator;
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
