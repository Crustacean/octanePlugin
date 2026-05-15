package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;

public enum OctaneGateReportState implements Serializable {
  WAITING("Waiting"),
  POLLING("Polling"),
  PASSED("Passed"),
  FAILED("Failed"),
  UNSTABLE("Unstable"),
  TIMED_OUT("Timed out"),
  ERROR("Error");

  private final String label;

  OctaneGateReportState(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public boolean isBuilding() {
    return this == WAITING || this == POLLING;
  }
}
