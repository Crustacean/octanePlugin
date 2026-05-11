package io.jenkins.plugins.octanesuitegatebyembiti;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

class MetricsContext implements Serializable {
  private static final long serialVersionUID = 1L;

  private final GateMetrics globalMetrics;
  private final Map<String, GateMetrics> scopes;

  MetricsContext(GateMetrics globalMetrics, Map<String, GateMetrics> scopes) {
    this.globalMetrics = globalMetrics;
    this.scopes = new LinkedHashMap<>(scopes);
  }

  double value(String metricReference) {
    String trimmed = Util.trimToEmpty(metricReference);
    int dot = trimmed.indexOf('.');
    if (dot < 0) {
      return globalMetrics.value(trimmed);
    }

    String scope = trimmed.substring(0, dot);
    String metric = trimmed.substring(dot + 1);
    GateMetrics scopedMetrics = scopes.get(scope);
    if (scopedMetrics == null) {
      throw new CriteriaException("Unknown scope: " + scope);
    }
    return scopedMetrics.value(metric);
  }
}
