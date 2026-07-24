package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class OctaneGatePieSliceTest {
  @Test
  public void movesThinSlicesOutsideAndPreservesNormalLabels() {
    List<OctaneGateStatusCount> statuses =
        List.of(
            new OctaneGateStatusCount(OctaneGateStatusBucket.PASSED, 90, 100),
            new OctaneGateStatusCount(OctaneGateStatusBucket.FAILED, 4, 100),
            new OctaneGateStatusCount(OctaneGateStatusBucket.BLOCKED, 3, 100),
            new OctaneGateStatusCount(OctaneGateStatusBucket.SKIPPED, 2, 100),
            new OctaneGateStatusCount(OctaneGateStatusBucket.RUNNING, 1, 100));
    List<OctaneGatePieSlice> slices = slices(statuses);

    assertFalse(slices.get(0).isCallout());
    assertEquals("middle", slices.get(0).getTextAnchor());
    for (OctaneGatePieSlice slice : slices.subList(1, slices.size())) {
      assertTrue(slice.isCallout());
      assertTrue(
          "callout label must sit outside the donut",
          Math.abs(Double.parseDouble(slice.getLabelX()) - 50.0) > 46.0);
      assertEquals(slice.getLabelX(), slice.getLeaderEndX());
      assertEquals(slice.getLabelY(), slice.getLeaderEndY());
      double leaderRadius =
          Math.hypot(
              Double.parseDouble(slice.getLeaderStartX()) - 50.0,
              Double.parseDouble(slice.getLeaderStartY()) - 50.0);
      assertEquals(38.0, leaderRadius, 0.002);
    }
    assertMinimumVerticalGap(slices, "start");
    assertMinimumVerticalGap(slices, "end");
  }

  @Test
  public void movesIntersectingNonThinLabelsToCallouts() {
    OctaneGateStatusCount failed = new OctaneGateStatusCount(OctaneGateStatusBucket.FAILED, 6, 100);
    OctaneGateStatusCount blocked =
        new OctaneGateStatusCount(OctaneGateStatusBucket.BLOCKED, 6, 100);

    List<OctaneGatePieSlice> slices =
        OctaneGatePieSlice.layoutLabels(
            List.of(
                new OctaneGatePieSlice(failed, 0.0, 1.0),
                new OctaneGatePieSlice(blocked, 1.0, 2.0)));

    assertTrue(slices.get(0).isCallout());
    assertTrue(slices.get(1).isCallout());
    assertMinimumVerticalGap(slices, "start");
  }

  @Test
  public void placesThinCalloutsOnTheirNearestSide() {
    OctaneGateStatusCount failed = new OctaneGateStatusCount(OctaneGateStatusBucket.FAILED, 4, 100);
    OctaneGateStatusCount blocked =
        new OctaneGateStatusCount(OctaneGateStatusBucket.BLOCKED, 4, 100);

    List<OctaneGatePieSlice> slices =
        OctaneGatePieSlice.layoutLabels(
            List.of(
                new OctaneGatePieSlice(failed, -10.0, 10.0),
                new OctaneGatePieSlice(blocked, 170.0, 190.0)));

    assertEquals("start", slices.get(0).getTextAnchor());
    assertEquals("end", slices.get(1).getTextAnchor());
  }

  private List<OctaneGatePieSlice> slices(List<OctaneGateStatusCount> statuses) {
    List<OctaneGatePieSlice> slices = new ArrayList<>();
    double angle = -90.0;
    for (OctaneGateStatusCount status : statuses) {
      double endAngle = angle + 360.0 * status.getPercentage() / 100.0;
      slices.add(new OctaneGatePieSlice(status, angle, endAngle));
      angle = endAngle;
    }
    return OctaneGatePieSlice.layoutLabels(slices);
  }

  private void assertMinimumVerticalGap(List<OctaneGatePieSlice> slices, String anchor) {
    List<Double> positions =
        slices.stream()
            .filter(OctaneGatePieSlice::isCallout)
            .filter(slice -> anchor.equals(slice.getTextAnchor()))
            .map(slice -> Double.parseDouble(slice.getLabelY()))
            .sorted(Comparator.naturalOrder())
            .toList();
    for (int index = 1; index < positions.size(); index++) {
      assertTrue(positions.get(index) - positions.get(index - 1) >= 8.0);
    }
  }
}
