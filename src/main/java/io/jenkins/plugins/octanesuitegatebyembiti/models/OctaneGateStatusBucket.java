package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;

public enum OctaneGateStatusBucket implements Serializable {
  PASSED("Passed", "#78c679"),
  FAILED("Failed", "#ff6361"),
  BLOCKED("Blocked", "#631919"),
  SKIPPED("Skipped", "#ffb74d"),
  RUNNING("Running", "#778899");

  private final String label;
  private final String color;

  OctaneGateStatusBucket(String label, String color) {
    this.label = label;
    this.color = color;
  }

  public String getLabel() {
    return label;
  }

  public String getColor() {
    return color;
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
