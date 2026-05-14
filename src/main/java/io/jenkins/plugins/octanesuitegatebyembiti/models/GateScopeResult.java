package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GateScopeResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final String query;
  private final List<String> queryIds;
  private final GateMetrics metrics;
  private final List<RunRecord> runs;

  public GateScopeResult(
      String name, String query, List<String> queryIds, GateMetrics metrics, List<RunRecord> runs) {
    this.name = name;
    this.query = query;
    this.queryIds = List.copyOf(queryIds);
    this.metrics = metrics;
    this.runs = List.copyOf(runs);
  }

  public String getName() {
    return name;
  }

  public String getQuery() {
    return query;
  }

  public List<String> getQueryIds() {
    return queryIds;
  }

  public GateMetrics getMetrics() {
    return metrics;
  }

  public List<RunRecord> getRuns() {
    return runs;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", name);
    values.put("query", query);
    values.put("queryIds", queryIds);
    values.put("metrics", metrics.toMap());
    values.put("runIds", runs.stream().map(RunRecord::getId).toList());

    List<Map<String, Object>> runMaps = new ArrayList<>();
    for (RunRecord run : runs) {
      runMaps.add(run.toMap());
    }
    values.put("runs", runMaps);
    return values;
  }
}
