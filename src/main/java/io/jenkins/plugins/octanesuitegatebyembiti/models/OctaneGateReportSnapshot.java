package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OctaneGateReportSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;

  private final OctaneGateReportState state;
  private final String message;
  private final String criteria;
  private final String suiteRunId;
  private final int refreshSeconds;
  private final int timeoutSeconds;
  private final String startedAt;
  private final String updatedAt;
  private final List<OctaneGateReportSection> sections;

  private OctaneGateReportSnapshot(
      OctaneGateReportState state,
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt,
      String updatedAt,
      List<OctaneGateReportSection> sections) {
    this.state = state;
    this.message = message;
    this.criteria = criteria;
    this.suiteRunId = suiteRunId;
    this.refreshSeconds = Math.max(1, refreshSeconds);
    this.timeoutSeconds = Math.max(1, timeoutSeconds);
    this.startedAt = startedAt;
    this.updatedAt = updatedAt;
    this.sections = List.copyOf(sections);
  }

  private static int toSeconds(int minutes) {
    return Math.max(1, minutes) * 60;
  }

  public static OctaneGateReportSnapshot empty() {
    String now = Instant.now().toString();
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.WAITING,
        "No Octane gate data yet.",
        "",
        "",
        30,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        now,
        now,
        List.of());
  }

  public static OctaneGateReportSnapshot waiting(GateRequest request, int refreshSeconds) {
    return waiting(request, refreshSeconds, Instant.now().toString());
  }

  public static OctaneGateReportSnapshot waiting(
      GateRequest request, int refreshSeconds, String startedAt) {
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.WAITING,
        "Waiting for ALM Octane polling to start.",
        request.getCriteria(),
        request.getSuiteRunId(),
        refreshSeconds,
        toSeconds(request.getTimeoutMinutes()),
        startedAt,
        Instant.now().toString(),
        List.of());
  }

  public static OctaneGateReportSnapshot fromResult(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier,
      int refreshSeconds) {
    return fromResult(
        state,
        message,
        result,
        classifier,
        refreshSeconds,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        result.getPolledAt().toString());
  }

  public static OctaneGateReportSnapshot fromResult(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt) {
    List<OctaneGateReportSection> sections = new ArrayList<>();
    sections.add(OctaneGateReportSection.global(result, classifier));
    for (GateScopeResult scopeResult : result.getScopedResults().values()) {
      sections.add(OctaneGateReportSection.scoped(scopeResult, classifier));
    }
    return new OctaneGateReportSnapshot(
        state,
        message,
        result.getCriteria(),
        result.getSuiteRunId(),
        refreshSeconds,
        timeoutSeconds,
        startedAt,
        result.getPolledAt().toString(),
        sections);
  }

  public static OctaneGateReportSnapshot error(
      String message, String criteria, String suiteRunId, int refreshSeconds) {
    return error(
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        toSeconds(GateRequest.DEFAULT_TIMEOUT_MINUTES),
        Instant.now().toString());
  }

  public static OctaneGateReportSnapshot error(
      String message,
      String criteria,
      String suiteRunId,
      int refreshSeconds,
      int timeoutSeconds,
      String startedAt) {
    return new OctaneGateReportSnapshot(
        OctaneGateReportState.ERROR,
        message,
        criteria,
        suiteRunId,
        refreshSeconds,
        timeoutSeconds,
        startedAt,
        Instant.now().toString(),
        List.of());
  }

  public OctaneGateReportState getState() {
    return state;
  }

  public String getStateLabel() {
    return state.getLabel();
  }

  public String getMessage() {
    return message;
  }

  public String getCriteria() {
    return criteria;
  }

  public String getSuiteRunId() {
    return suiteRunId;
  }

  public List<String> getSuiteRunIds() {
    return Util.splitIdList(suiteRunId);
  }

  public int getRefreshSeconds() {
    return refreshSeconds;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public String getStartedAt() {
    return startedAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedAtText() {
    return updatedAt;
  }

  public List<OctaneGateReportSection> getSections() {
    return sections;
  }

  public boolean isBuilding() {
    return state.isBuilding();
  }

  public boolean hasSections() {
    return !sections.isEmpty();
  }

  public double getExecutionProgress() {
    int total = 0;
    int executed = 0;
    for (OctaneGateReportSection section : sections) {
      if (isExecutionProgressSection(section)) {
        total += section.getMetrics().getTotal();
        executed += section.getMetrics().getExecuted();
      }
    }
    if (total == 0) {
      return 0.0;
    }
    return executed * 100.0 / total;
  }

  public String getExecutionProgressText() {
    return String.format(Locale.ROOT, "%.0f%%", getExecutionProgress());
  }

  private static boolean isExecutionProgressSection(OctaneGateReportSection section) {
    String source = section.getSource();
    return "global".equalsIgnoreCase(source) || "critical".equalsIgnoreCase(source);
  }
}
