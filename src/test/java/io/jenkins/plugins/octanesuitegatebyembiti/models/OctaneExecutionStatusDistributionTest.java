package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OctaneExecutionStatusDistributionTest {
  @Test
  public void sortsVisibleStatusesByCountAndUsesDashboardColors() {
    OctaneExecutionStatusDistribution distribution =
        OctaneExecutionStatusDistribution.fromStatusCounts(
            List.of(
                status(OctaneGateStatusBucket.PASSED, 6, 20),
                status(OctaneGateStatusBucket.FAILED, 3, 20),
                status(OctaneGateStatusBucket.BLOCKED, 2, 20),
                status(OctaneGateStatusBucket.SKIPPED, 1, 20),
                status(OctaneGateStatusBucket.RUNNING, 8, 20)));

    assertEquals(20, distribution.getTotal());
    assertEquals(5, distribution.getStatusCount());
    assertFalse(distribution.isEmpty());
    assertEquals(
        List.of("Planned", "Passed", "Failed", "Blocked", "Skipped"),
        distribution.getSegments().stream()
            .map(OctaneExecutionStatusDistribution.Segment::getLabel)
            .toList());
    assertEquals(
        List.of("#808080", "#009900", "#990000", "#631919", "#ffb74d"),
        distribution.getSegments().stream()
            .map(OctaneExecutionStatusDistribution.Segment::getColor)
            .toList());
    assertEquals("40.0%", distribution.getSegments().get(0).getPercentageLabel());
    assertTrue(
        distribution.getSegments().stream()
            .allMatch(segment -> segment.getPath().startsWith("M ")));
  }

  @Test
  public void omitsZeroStatusesAndHandlesAnEmptyDistribution() {
    OctaneExecutionStatusDistribution distribution =
        OctaneExecutionStatusDistribution.fromStatusCounts(
            List.of(
                status(OctaneGateStatusBucket.PASSED, 1, 1),
                status(OctaneGateStatusBucket.FAILED, 0, 1)));
    OctaneExecutionStatusDistribution empty =
        OctaneExecutionStatusDistribution.fromStatusCounts(List.of());

    assertEquals(1, distribution.getSegments().size());
    assertEquals(1, distribution.getStatusCount());
    assertEquals("Passed", distribution.getSegments().get(0).getLabel());
    assertTrue(empty.isEmpty());
    assertEquals(0, empty.getTotal());
    assertEquals(0, empty.getStatusCount());
  }

  private static OctaneGateStatusCount status(OctaneGateStatusBucket bucket, int count, int total) {
    return new OctaneGateStatusCount(bucket, count, total);
  }
}
