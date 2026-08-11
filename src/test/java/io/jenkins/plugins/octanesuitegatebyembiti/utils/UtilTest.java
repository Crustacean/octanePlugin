package io.jenkins.plugins.octanesuitegatebyembiti.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UtilTest {
  @Test
  void logValuesAreSingleLineAndBounded() {
    assertEquals("release forged entry", Util.forLog("release\nforged\rentry"));

    String safe = Util.forLog("x".repeat(3_000));
    assertEquals(2_051, safe.length());
    assertTrue(safe.endsWith("..."));
  }

  @Test
  void percentageHelpersAreZeroSafeAndConsistentlyFormatted() {
    assertEquals(50.0, Util.percentage(2, 4));
    assertEquals(0.0, Util.percentage(2, 0));
    assertEquals("50%", Util.formatCompactPercentage(50.0));
    assertEquals("66.7%", Util.formatCompactPercentage(66.666));
    assertEquals("66.67", Util.formatDecimal(66.666, 2));
    assertEquals("66.67%", Util.formatPercentage(66.666, 2));
  }
}
