package io.jenkins.plugins.octanesuitegatebyembiti.models;

public enum OctaneGateStatusBucket {
  PASSED("Passed", "passed", "var(--octane-status-passed)", "#30D158"),
  FAILED("Failed", "failed", "var(--octane-status-failed)", "#FF453A"),
  BLOCKED("Blocked", "blocked", "var(--octane-status-blocked)", "#FF9F0A"),
  SKIPPED("Skipped", "skipped", "var(--octane-status-skipped)", "#BF5AF2"),
  RUNNING("Running", "running", "var(--octane-status-no-run)", "#8E8E93");

  private final String label;
  private final String dataKey;
  private final String color;
  private final String tooltipColor;

  OctaneGateStatusBucket(String label, String dataKey, String color, String tooltipColor) {
    this.label = label;
    this.dataKey = dataKey;
    this.color = color;
    this.tooltipColor = tooltipColor;
  }

  public String getLabel() {
    return label;
  }

  public String getDataKey() {
    return dataKey;
  }

  public String getColor() {
    return color;
  }

  public String getTooltipColor() {
    return tooltipColor;
  }

  static OctaneGateStatusBucket fromOutcome(StatusClassifier.Outcome outcome) {
    if (outcome == StatusClassifier.Outcome.PASSED) {
      return PASSED;
    }
    if (outcome == StatusClassifier.Outcome.FAILED) {
      return FAILED;
    }
    if (outcome == StatusClassifier.Outcome.BLOCKED) {
      return BLOCKED;
    }
    if (outcome == StatusClassifier.Outcome.NEUTRAL) {
      return SKIPPED;
    }
    return RUNNING;
  }
}
