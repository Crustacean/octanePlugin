package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GateResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String suiteRunId;
  private final String criteria;
  private final boolean passed;
  private final boolean terminal;
  private final GateMetrics metrics;
  private final List<RunRecord> runs;
  private final Map<String, List<RunRecord>> suiteRuns;
  private final Map<String, GateScopeResult> scopedResults;
  private final OctaneRiskHeatMap riskHeatMap;
  private final DefectCriteriaMetrics defectMetrics;
  private final Instant polledAt;

  public GateResult(
      String suiteRunId,
      String criteria,
      boolean passed,
      boolean terminal,
      GateMetrics metrics,
      Map<String, GateMetrics> scopedMetrics,
      Instant polledAt) {
    this(
        suiteRunId,
        criteria,
        passed,
        terminal,
        metrics,
        List.of(),
        Map.of(),
        toScopeResults(scopedMetrics),
        OctaneRiskHeatMap.disabled(),
        polledAt);
  }

  public GateResult(
      String suiteRunId,
      String criteria,
      boolean passed,
      boolean terminal,
      GateMetrics metrics,
      List<RunRecord> runs,
      Map<String, List<RunRecord>> suiteRuns,
      Map<String, GateScopeResult> scopedResults,
      Instant polledAt) {
    this(
        suiteRunId,
        criteria,
        passed,
        terminal,
        metrics,
        runs,
        suiteRuns,
        scopedResults,
        OctaneRiskHeatMap.disabled(),
        polledAt);
  }

  public GateResult(
      String suiteRunId,
      String criteria,
      boolean passed,
      boolean terminal,
      GateMetrics metrics,
      List<RunRecord> runs,
      Map<String, List<RunRecord>> suiteRuns,
      Map<String, GateScopeResult> scopedResults,
      OctaneRiskHeatMap riskHeatMap,
      Instant polledAt) {
    this(
        suiteRunId,
        criteria,
        passed,
        terminal,
        metrics,
        runs,
        suiteRuns,
        scopedResults,
        riskHeatMap,
        new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of()),
        polledAt);
  }

  public GateResult(
      String suiteRunId,
      String criteria,
      boolean passed,
      boolean terminal,
      GateMetrics metrics,
      List<RunRecord> runs,
      Map<String, List<RunRecord>> suiteRuns,
      Map<String, GateScopeResult> scopedResults,
      OctaneRiskHeatMap riskHeatMap,
      DefectCriteriaMetrics defectMetrics,
      Instant polledAt) {
    this.suiteRunId = suiteRunId;
    this.criteria = criteria;
    this.passed = passed;
    this.terminal = terminal;
    this.metrics = metrics;
    this.runs = List.copyOf(runs);
    this.suiteRuns = copySuiteRuns(suiteRuns);
    this.scopedResults = new LinkedHashMap<>(scopedResults);
    this.riskHeatMap = riskHeatMap == null ? OctaneRiskHeatMap.disabled() : riskHeatMap;
    this.defectMetrics =
        defectMetrics == null
            ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of())
            : defectMetrics;
    this.polledAt = polledAt;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public String getCriteria() {
    return criteria;
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

  public List<RunRecord> getRuns() {
    return runs;
  }

  public Map<String, List<RunRecord>> getSuiteRuns() {
    return copySuiteRuns(suiteRuns);
  }

  public Map<String, GateMetrics> getScopedMetrics() {
    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    for (Map.Entry<String, GateScopeResult> entry : scopedResults.entrySet()) {
      scopedMetrics.put(entry.getKey(), entry.getValue().getMetrics());
    }
    return scopedMetrics;
  }

  public Map<String, GateScopeResult> getScopedResults() {
    return new LinkedHashMap<>(scopedResults);
  }

  public Instant getPolledAt() {
    return polledAt;
  }

  public OctaneRiskHeatMap getRiskHeatMap() {
    return riskHeatMap;
  }

  public DefectCriteriaMetrics getDefectMetrics() {
    return defectMetrics == null
        ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of())
        : defectMetrics;
  }

  public Map<String, Object> toPipelineMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("suiteRunId", suiteRunId);
    result.put("suiteRunIds", List.copyOf(Util.splitIdList(suiteRunId)));
    result.put("criteria", criteria);
    result.put("passed", passed);
    result.put("terminal", terminal);
    result.put("polledAt", polledAt.toString());
    result.put("metrics", metrics.toMap());
    result.put("regressions", metrics.toMap());

    Map<String, Object> scopes = new LinkedHashMap<>();
    Map<String, Object> scopeDetails = new LinkedHashMap<>();
    for (Map.Entry<String, GateScopeResult> entry : scopedResults.entrySet()) {
      scopes.put(entry.getKey(), entry.getValue().getMetrics().toMap());
      scopeDetails.put(entry.getKey(), entry.getValue().toMap());
    }
    result.put("scopes", scopes);
    result.put("scopeDetails", scopeDetails);
    result.put("runs", toRunMaps(runs));
    result.put("suiteRuns", toSuiteRunMaps(suiteRuns));
    result.put("riskHeatMap", riskHeatMap.toMap());
    result.put("defects", getDefectMetrics().toMap());
    return result;
  }

  private static Map<String, GateScopeResult> toScopeResults(
      Map<String, GateMetrics> scopedMetrics) {
    Map<String, GateScopeResult> scopedResults = new LinkedHashMap<>();
    for (Map.Entry<String, GateMetrics> entry : scopedMetrics.entrySet()) {
      scopedResults.put(
          entry.getKey(),
          new GateScopeResult(entry.getKey(), "", List.of(), entry.getValue(), List.of()));
    }
    return scopedResults;
  }

  private static Map<String, List<RunRecord>> copySuiteRuns(
      Map<String, List<RunRecord>> suiteRuns) {
    Map<String, List<RunRecord>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> entry : suiteRuns.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return copy;
  }

  private static List<Map<String, Object>> toRunMaps(List<RunRecord> runs) {
    List<Map<String, Object>> runMaps = new ArrayList<>();
    for (RunRecord run : runs) {
      runMaps.add(run.toMap());
    }
    return runMaps;
  }

  private static Map<String, Object> toSuiteRunMaps(Map<String, List<RunRecord>> suiteRuns) {
    Map<String, Object> suiteRunMaps = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> entry : suiteRuns.entrySet()) {
      suiteRunMaps.put(entry.getKey(), toRunMaps(entry.getValue()));
    }
    return suiteRunMaps;
  }
}
