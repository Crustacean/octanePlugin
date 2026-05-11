package io.jenkins.plugins.octanesuitegatebyembiti;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class GateResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String suiteRunId;
  private final String criteria;
  private final boolean passed;
  private final boolean terminal;
  private final GateMetrics metrics;
  private final Map<String, GateMetrics> scopedMetrics;
  private final Instant polledAt;

  GateResult(
      String suiteRunId,
      String criteria,
      boolean passed,
      boolean terminal,
      GateMetrics metrics,
      Map<String, GateMetrics> scopedMetrics,
      Instant polledAt) {
    this.suiteRunId = suiteRunId;
    this.criteria = criteria;
    this.passed = passed;
    this.terminal = terminal;
    this.metrics = metrics;
    this.scopedMetrics = new LinkedHashMap<>(scopedMetrics);
    this.polledAt = polledAt;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public boolean isPassed() {
    return passed;
  }

  public boolean isTerminal() {
    return terminal;
  }

  public GateMetrics getMetrics() {
    return metrics;
  }

  public Map<String, Object> toPipelineMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("suiteRunId", suiteRunId);
    result.put("criteria", criteria);
    result.put("passed", passed);
    result.put("terminal", terminal);
    result.put("polledAt", polledAt.toString());
    result.put("metrics", metrics.toMap());

    Map<String, Object> scopes = new LinkedHashMap<>();
    for (Map.Entry<String, GateMetrics> entry : scopedMetrics.entrySet()) {
      scopes.put(entry.getKey(), entry.getValue().toMap());
    }
    result.put("scopes", scopes);
    return result;
  }
}
