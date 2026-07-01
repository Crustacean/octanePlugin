package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        labels(distribution.getSegments()));
    assertEquals(
        List.of("#808080", "#009900", "#990000", "#631919", "#ffb74d"),
        colors(distribution.getSegments()));
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

  private static List<String> labels(List<OctaneExecutionStatusDistribution.Segment> segments) {
    List<String> labels = new ArrayList<>(segments.size());
    for (OctaneExecutionStatusDistribution.Segment segment : segments) {
      labels.add(Objects.requireNonNull(segment).getLabel());
    }
    return labels;
  }

  private static List<String> colors(List<OctaneExecutionStatusDistribution.Segment> segments) {
    List<String> colors = new ArrayList<>(segments.size());
    for (OctaneExecutionStatusDistribution.Segment segment : segments) {
      colors.add(Objects.requireNonNull(segment).getColor());
    }
    return colors;
  }
}
