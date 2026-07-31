package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class StatusClassifier implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final String DEFAULT_PASSED_STATUSES = "passed,list_node.run_native_status.passed";
  public static final String DEFAULT_FAILED_STATUSES =
      "failed,blocked,error,list_node.run_native_status.failed";
  public static final String DEFAULT_NEUTRAL_STATUSES =
      "skipped,not_completed,list_node.run_native_status.skipped";
  public static final String DEFAULT_RUNNING_STATUSES =
      "planned,in_progress,running,list_node.run_native_status.planned";

  private final Set<String> passedStatuses;
  private final Set<String> failedStatuses;
  private final Set<String> neutralStatuses;
  private final Set<String> runningStatuses;

  public StatusClassifier(
      String passedStatuses,
      String failedStatuses,
      String neutralStatuses,
      String runningStatuses) {
    this.passedStatuses = new HashSet<>(Util.splitCsv(passedStatuses));
    this.failedStatuses = new HashSet<>(Util.splitCsv(failedStatuses));
    this.neutralStatuses = new HashSet<>(Util.splitCsv(neutralStatuses));
    this.runningStatuses = new HashSet<>(Util.splitCsv(runningStatuses));
  }

  Outcome classify(String status) {
    String normalized = Util.normalizeStatus(status);
    Outcome configured = configuredOutcome(normalized);
    return configured == null ? inferredOutcome(normalized) : configured;
  }

  private Outcome configuredOutcome(String normalized) {
    if (matches(passedStatuses, normalized)) {
      return Outcome.PASSED;
    }
    if (isBlocked(normalized)) {
      return Outcome.BLOCKED;
    }
    if (matches(failedStatuses, normalized)) {
      return Outcome.FAILED;
    }
    if (matches(neutralStatuses, normalized)) {
      return Outcome.NEUTRAL;
    }
    return matches(runningStatuses, normalized) ? Outcome.RUNNING : null;
  }

  private Outcome inferredOutcome(String normalized) {
    if (normalized.contains("pass")) {
      return Outcome.PASSED;
    }
    if (normalized.contains("fail") || normalized.contains("error")) {
      return Outcome.FAILED;
    }
    return normalized.contains("skip") || normalized.contains("not_completed")
        ? Outcome.NEUTRAL
        : Outcome.RUNNING;
  }

  private boolean matches(Set<String> candidates, String normalizedStatus) {
    if (candidates.contains(normalizedStatus)) {
      return true;
    }
    int dot = normalizedStatus.lastIndexOf('.');
    return dot >= 0 && candidates.contains(normalizedStatus.substring(dot + 1));
  }

  private boolean isBlocked(String normalizedStatus) {
    return normalizedStatus.contains("blocked");
  }

  enum Outcome {
    PASSED,
    FAILED,
    BLOCKED,
    NEUTRAL,
    RUNNING
  }
}
