package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.services.CriteriaException;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetricsContext implements Serializable {
  private static final long serialVersionUID = 1L;

  private final GateMetrics regressionMetrics;
  private final Map<String, GateMetrics> scopes;

  public MetricsContext(GateMetrics regressionMetrics, Map<String, GateMetrics> scopes) {
    this.regressionMetrics = regressionMetrics;
    this.scopes = new LinkedHashMap<>(scopes);
  }

  public double value(String metricReference) {
    String trimmed = Util.trimToEmpty(metricReference);
    int dot = trimmed.indexOf('.');
    if (dot < 0) {
      return regressionMetrics.value(trimmed);
    }

    String scope = trimmed.substring(0, dot);
    String metric = trimmed.substring(dot + 1);
    if ("regressions".equalsIgnoreCase(scope) || "regression".equalsIgnoreCase(scope)) {
      return regressionMetrics.value(metric);
    }

    GateMetrics scopedMetrics = scopes.get(scope);
    if (scopedMetrics == null) {
      throw new CriteriaException("Unknown scope: " + scope);
    }
    return scopedMetrics.value(metric);
  }
}
