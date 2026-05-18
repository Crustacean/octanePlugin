package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OctaneGateSuiteRunChart implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String suiteRunId;
  private final String displayName;
  private final List<String> suiteRunIds;
  private final int total;
  private final int maxTotal;
  private final List<OctaneGateStatusCount> statuses;

  private OctaneGateSuiteRunChart(
      String suiteRunId,
      String displayName,
      List<String> suiteRunIds,
      int total,
      int maxTotal,
      List<OctaneGateStatusCount> statuses) {
    this.suiteRunId = suiteRunId;
    this.displayName = displayName;
    this.suiteRunIds = List.copyOf(suiteRunIds);
    this.total = total;
    this.maxTotal = maxTotal;
    this.statuses = List.copyOf(statuses);
  }

  static OctaneGateSuiteRunChart fromRuns(
      String suiteRunId, List<RunRecord> runs, StatusClassifier classifier) {
    Map<OctaneGateStatusBucket, Integer> counts = emptyCounts();
    for (RunRecord run : runs) {
      OctaneGateStatusBucket bucket =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      counts.put(bucket, counts.get(bucket) + 1);
    }
    return new OctaneGateSuiteRunChart(
        suiteRunId,
        suiteRunId,
        List.of(suiteRunId),
        runs.size(),
        runs.size(),
        toStatusCounts(counts, runs.size()));
  }

  static OctaneGateSuiteRunChart fromRunByGroup(
      String displayName,
      List<String> suiteRunIds,
      List<RunRecord> runs,
      StatusClassifier classifier) {
    Map<OctaneGateStatusBucket, Integer> counts = emptyCounts();
    for (RunRecord run : runs) {
      OctaneGateStatusBucket bucket =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      counts.put(bucket, counts.get(bucket) + 1);
    }
    return new OctaneGateSuiteRunChart(
        groupKey(displayName, suiteRunIds),
        displayName,
        suiteRunIds,
        runs.size(),
        runs.size(),
        toStatusCounts(counts, runs.size()));
  }

  OctaneGateSuiteRunChart scaledAgainst(int maxTotal) {
    return new OctaneGateSuiteRunChart(
        suiteRunId, displayName, suiteRunIds, total, maxTotal, statuses);
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAxisLabel() {
    return compactRunByName(displayName);
  }

  public List<String> getSuiteRunIds() {
    return suiteRunIds;
  }

  public String getTitle() {
    if (suiteRunIds.isEmpty()) {
      return displayName;
    }
    return displayName + " (suite runs: " + String.join(", ", suiteRunIds) + ")";
  }

  public int getTotal() {
    return total;
  }

  public List<OctaneGateStatusCount> getStatuses() {
    return statuses;
  }

  public String getBarHeightStyle() {
    if (maxTotal <= 0 || total <= 0) {
      return "height: 0%;";
    }
    double percentage = total * 100.0 / maxTotal;
    return String.format(Locale.ROOT, "height: %.2f%%;", percentage);
  }

  public boolean isEmpty() {
    return total == 0;
  }

  private static String groupKey(String displayName, List<String> suiteRunIds) {
    if (suiteRunIds.isEmpty()) {
      return displayName;
    }
    return displayName + ":" + String.join(",", suiteRunIds);
  }

  private static String compactRunByName(String value) {
    String trimmed = value == null ? "" : value.trim();
    int atIndex = trimmed.indexOf('@');
    String label = atIndex > 0 ? trimmed.substring(0, atIndex) : trimmed;
    return label.toLowerCase(Locale.ROOT);
  }

  static Map<OctaneGateStatusBucket, Integer> emptyCounts() {
    Map<OctaneGateStatusBucket, Integer> counts = new EnumMap<>(OctaneGateStatusBucket.class);
    for (OctaneGateStatusBucket bucket : OctaneGateStatusBucket.values()) {
      counts.put(bucket, 0);
    }
    return counts;
  }

  static List<OctaneGateStatusCount> toStatusCounts(
      Map<OctaneGateStatusBucket, Integer> counts, int total) {
    return List.of(
        new OctaneGateStatusCount(
            OctaneGateStatusBucket.PASSED, counts.get(OctaneGateStatusBucket.PASSED), total),
        new OctaneGateStatusCount(
            OctaneGateStatusBucket.FAILED, counts.get(OctaneGateStatusBucket.FAILED), total),
        new OctaneGateStatusCount(
            OctaneGateStatusBucket.BLOCKED, counts.get(OctaneGateStatusBucket.BLOCKED), total),
        new OctaneGateStatusCount(
            OctaneGateStatusBucket.SKIPPED, counts.get(OctaneGateStatusBucket.SKIPPED), total),
        new OctaneGateStatusCount(
            OctaneGateStatusBucket.RUNNING, counts.get(OctaneGateStatusBucket.RUNNING), total));
  }
}
