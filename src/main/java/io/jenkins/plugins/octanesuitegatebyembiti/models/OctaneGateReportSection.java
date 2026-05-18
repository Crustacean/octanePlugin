package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

  public static OctaneGateReportSection regressions(
      GateResult result, StatusClassifier classifier) {
    return fromSuiteRuns(
        "Regressions suite runs",
        "regressions",
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
        groupSuiteRunsByRunBy(chartSuiteRuns).stream()
            .map(group -> group.toChart(classifier))
            .toList();
    int maxSuiteRunTotal =
        suiteRunCharts.stream().mapToInt(OctaneGateSuiteRunChart::getTotal).max().orElse(0);
    List<OctaneGateSuiteRunChart> scaledSuiteRunCharts =
        suiteRunCharts.stream().map(chart -> chart.scaledAgainst(maxSuiteRunTotal)).toList();
    List<RunRecord> reportRuns = runsForTotals(chartSuiteRuns, fallbackRuns);
    return new OctaneGateReportSection(
        name,
        source,
        Util.splitIdList(suiteRunId),
        metrics,
        reportRuns.isEmpty() ? totalsFromMetrics(metrics) : totalsFromRuns(reportRuns, classifier),
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
    if (!suiteRunIds.isEmpty()) {
      return suiteRunIds.size();
    }
    return suiteRuns.size();
  }

  public int getMaxSuiteRunTotal() {
    return suiteRuns.stream().mapToInt(OctaneGateSuiteRunChart::getTotal).max().orElse(0);
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
    if ("regressions".equalsIgnoreCase(source) || "global".equalsIgnoreCase(source)) {
      return "REGRESSION Tests Status Distribution";
    }
    if ("critical".equalsIgnoreCase(source)) {
      return "CRITICAL Tests Status Distribution";
    }
    return name + " status distribution";
  }

  public String getSuiteRunChartTitle() {
    if ("regressions".equalsIgnoreCase(source) || "global".equalsIgnoreCase(source)) {
      return "Testing progress per Tester Suite Runs_REGRESSIONS";
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

  private static List<RunRecord> runsForTotals(
      Map<String, List<RunRecord>> suiteRuns, List<RunRecord> fallbackRuns) {
    if (!fallbackRuns.isEmpty()) {
      return fallbackRuns;
    }
    return suiteRuns.values().stream().flatMap(List::stream).toList();
  }

  private static List<OctaneGateStatusCount> totalsFromRuns(
      List<RunRecord> runs, StatusClassifier classifier) {
    Map<OctaneGateStatusBucket, Integer> counts = OctaneGateSuiteRunChart.emptyCounts();
    for (RunRecord run : runs) {
      OctaneGateStatusBucket bucket =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      counts.put(bucket, counts.get(bucket) + 1);
    }
    return OctaneGateSuiteRunChart.toStatusCounts(counts, runs.size());
  }

  private static List<RunByGroup> groupSuiteRunsByRunBy(Map<String, List<RunRecord>> suiteRuns) {
    Map<String, RunByGroup> groups = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> entry : suiteRuns.entrySet()) {
      if (entry.getValue().isEmpty()) {
        String label = entry.getKey();
        groups.putIfAbsent(groupKey(label), new RunByGroup(label));
        groups.get(groupKey(label)).addSuiteRunId(entry.getKey());
        continue;
      }
      for (RunRecord run : entry.getValue()) {
        String label = runByLabel(run, entry.getKey());
        String key = groupKey(label);
        groups.putIfAbsent(key, new RunByGroup(label));
        groups.get(key).addSuiteRunId(entry.getKey());
        groups.get(key).addRun(run);
      }
    }
    return List.copyOf(groups.values());
  }

  private static String runByLabel(RunRecord run, String suiteRunId) {
    if (!Util.isBlank(run.getRunByName())) {
      return run.getRunByName();
    }
    if (Util.isBlank(suiteRunId)) {
      return "Unassigned";
    }
    return "Unassigned (" + suiteRunId + ")";
  }

  private static String groupKey(String label) {
    return label.trim().toLowerCase(Locale.ROOT);
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

  private static class RunByGroup {
    private final String displayName;
    private final Set<String> suiteRunIds = new LinkedHashSet<>();
    private final List<RunRecord> runs = new ArrayList<>();

    private RunByGroup(String displayName) {
      this.displayName = displayName;
    }

    private void addSuiteRunId(String suiteRunId) {
      if (!Util.isBlank(suiteRunId) && !"matched-runs".equals(suiteRunId)) {
        suiteRunIds.add(suiteRunId);
      }
    }

    private void addRun(RunRecord run) {
      runs.add(run);
    }

    private OctaneGateSuiteRunChart toChart(StatusClassifier classifier) {
      return OctaneGateSuiteRunChart.fromRunByGroup(
          displayName, List.copyOf(suiteRunIds), runs, classifier);
    }
  }
}
