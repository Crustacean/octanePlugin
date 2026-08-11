package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GateMetricsTest {
  @Test
  void centralizesExecutedCountAndRateCalculations() {
    assertEquals(4, GateMetrics.executedCount(2, 1, 1));
    assertEquals(80.0, GateMetrics.executionRate(4, 5));
    assertEquals(50.0, GateMetrics.passRate(2, 4));
  }

  @Test
  void ratesDefaultToZeroWithoutADenominator() {
    assertEquals(0.0, GateMetrics.executionRate(4, 0));
    assertEquals(0.0, GateMetrics.passRate(2, 0));
  }
}
