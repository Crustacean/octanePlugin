package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OctaneGateReportSection implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final String source;
  private final List<String> suiteRunIds;
  private final GateMetrics metrics;
  private final List<OctaneGateStatusCount> totals;
  private final List<OctaneGatePieSlice> pieSlices;
  private final List<OctaneGateSuiteRunChart> suiteRuns;

  private OctaneGateReportSection(
      String name,
      String source,
      List<String> suiteRunIds,
      GateMetrics metrics,
      List<OctaneGateStatusCount> totals,
      List<OctaneGateSuiteRunChart> suiteRuns) {
    this.name = name;
    this.source = source;
    this.suiteRunIds = List.copyOf(suiteRunIds);
    this.metrics = metrics;
    this.totals = List.copyOf(totals);
    this.pieSlices = buildPieSlices(totals);
    this.suiteRuns = List.copyOf(suiteRuns);
  }

  public static OctaneGateReportSection global(GateResult result, StatusClassifier classifier) {
    return fromSuiteRuns(
        "Global suite runs",
        "global",
        result.getSuiteRunId(),
        result.getMetrics(),
        result.getSuiteRuns(),
        result.getRuns(),
        classifier);
  }

  public static OctaneGateReportSection scoped(
      GateScopeResult scopeResult, StatusClassifier classifier) {
    String label =
        displayScopeName(scopeResult.getName())
            + (scopeResult.isSuiteRunScope() ? " suite runs" : " query scope");
    String suiteRunId = scopeResult.isSuiteRunScope() ? scopeResult.getSuiteRunId() : "";
    return fromSuiteRuns(
        label,
        scopeResult.getName(),
        suiteRunId,
        scopeResult.getMetrics(),
        scopeResult.getSuiteRuns(),
        scopeResult.getRuns(),
        classifier);
  }

  private static OctaneGateReportSection fromSuiteRuns(
      String name,
      String source,
      String suiteRunId,
      GateMetrics metrics,
      Map<String, List<RunRecord>> suiteRuns,
      List<RunRecord> fallbackRuns,
      StatusClassifier classifier) {
    Map<String, List<RunRecord>> chartSuiteRuns = new LinkedHashMap<>(suiteRuns);
    if (chartSuiteRuns.isEmpty() && !fallbackRuns.isEmpty()) {
      chartSuiteRuns.put("matched-runs", fallbackRuns);
    }

    List<OctaneGateSuiteRunChart> suiteRunCharts =
        chartSuiteRuns.entrySet().stream()
            .map(
                entry ->
                    OctaneGateSuiteRunChart.fromRuns(entry.getKey(), entry.getValue(), classifier))
            .toList();
    int maxSuiteRunTotal =
        suiteRunCharts.stream().mapToInt(OctaneGateSuiteRunChart::getTotal).max().orElse(0);
    List<OctaneGateSuiteRunChart> scaledSuiteRunCharts =
        suiteRunCharts.stream().map(chart -> chart.scaledAgainst(maxSuiteRunTotal)).toList();
    return new OctaneGateReportSection(
        name,
        source,
        Util.splitIdList(suiteRunId),
        metrics,
        totalsFromMetrics(metrics),
        scaledSuiteRunCharts);
  }

  public String getName() {
    return name;
  }

  public String getSource() {
    return source;
  }

  public List<String> getSuiteRunIds() {
    return suiteRunIds;
  }

  public GateMetrics getMetrics() {
    return metrics;
  }

  public List<OctaneGateStatusCount> getTotals() {
    return totals;
  }

  public List<OctaneGatePieSlice> getPieSlices() {
    return pieSlices;
  }

  public List<OctaneGateSuiteRunChart> getSuiteRuns() {
    return suiteRuns;
  }

  public int getSuiteRunCount() {
    return suiteRuns.size();
  }

  public boolean isEmpty() {
    return metrics.getTotal() == 0;
  }

  public boolean isNoRuns() {
    return isEmpty();
  }

  public String getSuiteRunLabel() {
    if (suiteRunIds.isEmpty()) {
      return "<none>";
    }
    return String.join(", ", suiteRunIds);
  }

  public String getStatusDistributionTitle() {
    if ("global".equalsIgnoreCase(source)) {
      return "Grouped Status Distribution";
    }
    if ("critical".equalsIgnoreCase(source)) {
      return "Grouped Status Distribution_CRITICAL RUNs";
    }
    return name + " status distribution";
  }

  public String getSuiteRunChartTitle() {
    if ("global".equalsIgnoreCase(source)) {
      return "Testing progress per Tester Suite Runs";
    }
    if ("critical".equalsIgnoreCase(source)) {
      return "Testing progress per Tester Suite Runs_CRITICAL";
    }
    return name + " by suite run";
  }

  private static List<OctaneGateStatusCount> totalsFromMetrics(GateMetrics metrics) {
    Map<OctaneGateStatusBucket, Integer> counts = OctaneGateSuiteRunChart.emptyCounts();
    counts.put(OctaneGateStatusBucket.PASSED, metrics.getPassed());
    counts.put(OctaneGateStatusBucket.FAILED, metrics.getFailed());
    counts.put(OctaneGateStatusBucket.SKIPPED, metrics.getSkipped());
    counts.put(OctaneGateStatusBucket.RUNNING, metrics.getRunning());
    return OctaneGateSuiteRunChart.toStatusCounts(counts, metrics.getTotal());
  }

  private static List<OctaneGatePieSlice> buildPieSlices(List<OctaneGateStatusCount> totals) {
    int total = totals.stream().mapToInt(OctaneGateStatusCount::getCount).sum();
    if (total == 0) {
      return List.of();
    }

    double angle = -90.0;
    ArrayList<OctaneGatePieSlice> slices = new ArrayList<>();
    for (OctaneGateStatusCount status : totals) {
      if (status.getCount() == 0) {
        continue;
      }
      double nextAngle = angle + 360.0 * status.getCount() / total;
      slices.add(new OctaneGatePieSlice(status, angle, nextAngle));
      angle = nextAngle;
    }
    return slices;
  }

  private static String displayScopeName(String scopeName) {
    if (scopeName == null || scopeName.isBlank()) {
      return "Scope";
    }
    return scopeName.substring(0, 1).toUpperCase() + scopeName.substring(1);
  }
}
