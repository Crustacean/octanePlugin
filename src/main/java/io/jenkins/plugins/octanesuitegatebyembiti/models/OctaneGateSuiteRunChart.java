package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OctaneGateSuiteRunChart implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final List<OctaneGateStatusBucket> DOMINANT_STATUS_PRIORITY =
      List.of(
          OctaneGateStatusBucket.FAILED,
          OctaneGateStatusBucket.BLOCKED,
          OctaneGateStatusBucket.PASSED,
          OctaneGateStatusBucket.SKIPPED,
          OctaneGateStatusBucket.RUNNING);

  private final String suiteRunId;
  private final String displayName;
  private final List<String> suiteRunIds;
  private final int total;
  private final int maxTotal;
  private final List<OctaneGateStatusCount> statuses;
  private final int issueStatusesTotal;
  private final int openIssues;
  private final int closedIssues;

  private OctaneGateSuiteRunChart(
      String suiteRunId,
      String displayName,
      List<String> suiteRunIds,
      int total,
      int maxTotal,
      List<OctaneGateStatusCount> statuses,
      int issueStatusesTotal,
      int openIssues,
      int closedIssues) {
    this.suiteRunId = suiteRunId;
    this.displayName = displayName;
    this.suiteRunIds = List.copyOf(suiteRunIds);
    this.total = total;
    this.maxTotal = maxTotal;
    this.statuses = List.copyOf(statuses);
    this.issueStatusesTotal = Math.max(0, issueStatusesTotal);
    this.openIssues = Math.max(0, openIssues);
    this.closedIssues = Math.max(0, closedIssues);
  }

  static OctaneGateSuiteRunChart fromRuns(
      String suiteRunId, List<RunRecord> runs, StatusClassifier classifier) {
    return fromRuns(suiteRunId, runs, List.of(), classifier);
  }

  static OctaneGateSuiteRunChart fromRuns(
      String suiteRunId,
      List<RunRecord> runs,
      List<DefectRecord> defects,
      StatusClassifier classifier) {
    Map<OctaneGateStatusBucket, Integer> counts = emptyCounts();
    for (RunRecord run : runs) {
      OctaneGateStatusBucket bucket =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      counts.put(bucket, counts.get(bucket) + 1);
    }
    DefectWorkload defectWorkload = defectWorkload(runs, defects);
    return new OctaneGateSuiteRunChart(
        suiteRunId,
        suiteRunId,
        List.of(suiteRunId),
        runs.size(),
        runs.size(),
        toStatusCounts(counts, runs.size()),
        issueStatusesTotal(counts),
        defectWorkload.openIssues,
        defectWorkload.closedIssues);
  }

  static OctaneGateSuiteRunChart fromRunByGroup(
      String displayName,
      List<String> suiteRunIds,
      List<RunRecord> runs,
      StatusClassifier classifier) {
    return fromRunByGroup(displayName, suiteRunIds, runs, List.of(), classifier);
  }

  static OctaneGateSuiteRunChart fromRunByGroup(
      String displayName,
      List<String> suiteRunIds,
      List<RunRecord> runs,
      List<DefectRecord> defects,
      StatusClassifier classifier) {
    Map<OctaneGateStatusBucket, Integer> counts = emptyCounts();
    for (RunRecord run : runs) {
      OctaneGateStatusBucket bucket =
          OctaneGateStatusBucket.fromOutcome(classifier.classify(run.getStatus()));
      counts.put(bucket, counts.get(bucket) + 1);
    }
    DefectWorkload defectWorkload = defectWorkload(runs, defects);
    return new OctaneGateSuiteRunChart(
        groupKey(displayName, suiteRunIds),
        displayName,
        suiteRunIds,
        runs.size(),
        runs.size(),
        toStatusCounts(counts, runs.size()),
        issueStatusesTotal(counts),
        defectWorkload.openIssues,
        defectWorkload.closedIssues);
  }

  OctaneGateSuiteRunChart scaledAgainst(int maxTotal) {
    return new OctaneGateSuiteRunChart(
        suiteRunId,
        displayName,
        suiteRunIds,
        total,
        maxTotal,
        statuses,
        issueStatusesTotal,
        openIssues,
        closedIssues);
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

  public String getDominantStatusLabel() {
    OctaneGateStatusCount dominantStatus = getDominantStatus();
    return dominantStatus == null ? "" : dominantStatus.getLabel();
  }

  public String getDominantStatusColor() {
    OctaneGateStatusCount dominantStatus = getDominantStatus();
    return dominantStatus == null ? "" : dominantStatus.getTooltipColor();
  }

  public int getDominantStatusCount() {
    OctaneGateStatusCount dominantStatus = getDominantStatus();
    return dominantStatus == null ? 0 : dominantStatus.getCount();
  }

  public int getPassedCount() {
    return getStatusCount(OctaneGateStatusBucket.PASSED);
  }

  public String getPassedTooltipColor() {
    return OctaneGateStatusBucket.PASSED.getTooltipColor();
  }

  public int getFailedCount() {
    return getStatusCount(OctaneGateStatusBucket.FAILED);
  }

  public String getFailedTooltipColor() {
    return OctaneGateStatusBucket.FAILED.getTooltipColor();
  }

  public int getBlockedCount() {
    return getStatusCount(OctaneGateStatusBucket.BLOCKED);
  }

  public String getBlockedTooltipColor() {
    return OctaneGateStatusBucket.BLOCKED.getTooltipColor();
  }

  public int getSkippedCount() {
    return getStatusCount(OctaneGateStatusBucket.SKIPPED);
  }

  public String getSkippedTooltipColor() {
    return OctaneGateStatusBucket.SKIPPED.getTooltipColor();
  }

  public int getRunningCount() {
    return getStatusCount(OctaneGateStatusBucket.RUNNING);
  }

  public String getRunningTooltipColor() {
    return OctaneGateStatusBucket.RUNNING.getTooltipColor();
  }

  public int getOpenIssues() {
    return openIssues;
  }

  public int getClosedIssues() {
    return closedIssues;
  }

  public int getIssueStatusesTotal() {
    return issueStatusesTotal;
  }

  public boolean isShowOpenIssuesRow() {
    return openIssues + closedIssues > 0;
  }

  public boolean isShowAwaitingRetestRow() {
    return closedIssues > 0 && openIssues < issueStatusesTotal;
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

  private OctaneGateStatusCount getDominantStatus() {
    if (total <= 0) {
      return null;
    }
    int largestCount = 0;
    for (OctaneGateStatusCount status : statuses) {
      largestCount = Math.max(largestCount, status.getCount());
    }
    if (largestCount <= 0) {
      return null;
    }
    for (OctaneGateStatusBucket bucket : DOMINANT_STATUS_PRIORITY) {
      for (OctaneGateStatusCount status : statuses) {
        if (status.getBucket() == bucket && status.getCount() == largestCount) {
          return status;
        }
      }
    }
    return null;
  }

  private int getStatusCount(OctaneGateStatusBucket bucket) {
    for (OctaneGateStatusCount status : statuses) {
      if (status.getBucket() == bucket) {
        return status.getCount();
      }
    }
    return 0;
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

  private static int issueStatusesTotal(Map<OctaneGateStatusBucket, Integer> counts) {
    return counts.get(OctaneGateStatusBucket.FAILED)
        + counts.get(OctaneGateStatusBucket.BLOCKED)
        + counts.get(OctaneGateStatusBucket.SKIPPED);
  }

  private static DefectWorkload defectWorkload(List<RunRecord> runs, List<DefectRecord> defects) {
    if (runs == null || runs.isEmpty() || defects == null || defects.isEmpty()) {
      return new DefectWorkload(0, 0);
    }
    Set<String> runIds = new LinkedHashSet<>();
    Set<String> testIds = new LinkedHashSet<>();
    for (RunRecord run : runs) {
      if (!Util.isBlank(run.getId())) {
        runIds.add(run.getId());
      }
      if (!Util.isBlank(run.getTestId())) {
        testIds.add(run.getTestId());
      }
    }

    Set<String> seenDefects = new LinkedHashSet<>();
    int open = 0;
    int closed = 0;
    for (DefectRecord defect : defects) {
      if (defect == null) {
        continue;
      }
      if (!isLinkedToRuns(defect, runIds, testIds) || !seenDefects.add(defectKey(defect))) {
        continue;
      }
      if (defect.isOpen()) {
        open++;
      } else {
        closed++;
      }
    }
    return new DefectWorkload(open, closed);
  }

  private static boolean isLinkedToRuns(
      DefectRecord defect, Set<String> runIds, Set<String> testIds) {
    if (!Util.isBlank(defect.getRunId()) && runIds.contains(defect.getRunId())) {
      return true;
    }
    return !Util.isBlank(defect.getTestId()) && testIds.contains(defect.getTestId());
  }

  private static String defectKey(DefectRecord defect) {
    if (!Util.isBlank(defect.getId())) {
      return defect.getId();
    }
    return defect.getName()
        + "|"
        + defect.getSeverity()
        + "|"
        + defect.getPriority()
        + "|"
        + defect.getPhase()
        + "|"
        + defect.getRunId()
        + "|"
        + defect.getTestId();
  }

  private record DefectWorkload(int openIssues, int closedIssues) {}
}
