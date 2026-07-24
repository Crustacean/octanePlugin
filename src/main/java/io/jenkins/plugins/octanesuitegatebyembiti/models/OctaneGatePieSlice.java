package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class OctaneGatePieSlice implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double CENTER = 50.0;
  private static final double RADIUS = 46.0;
  private static final double LABEL_RADIUS = 52.0;
  private static final double CALLOUT_ORIGIN_RADIUS = 38.0;
  private static final double CALLOUT_LABEL_X = 104.0;
  private static final double CALLOUT_MIN_Y = -2.0;
  private static final double CALLOUT_MAX_Y = 102.0;
  private static final double CALLOUT_MIN_GAP = 8.0;
  private static final double THIN_SLICE_PERCENTAGE = 5.0;
  private static final double LABEL_HEIGHT = 5.0;
  private static final double LABEL_HORIZONTAL_PADDING = 1.0;

  private final OctaneGateStatusCount status;
  private final String path;
  private final boolean fullCircle;
  private final double middleAngle;
  private final boolean callout;
  private final String labelX;
  private final String labelY;
  private final String leaderStartX;
  private final String leaderStartY;
  private final String leaderEndX;
  private final String leaderEndY;
  private final String textAnchor;

  OctaneGatePieSlice(OctaneGateStatusCount status, double startAngle, double endAngle) {
    this(
        status,
        status.getPercentage() >= 99.999999,
        status.getPercentage() >= 99.999999 ? "" : buildPath(startAngle, endAngle),
        (startAngle + endAngle) / 2.0,
        false,
        point((startAngle + endAngle) / 2.0, LABEL_RADIUS),
        null);
  }

  private OctaneGatePieSlice(
      OctaneGateStatusCount status,
      boolean fullCircle,
      String path,
      double middleAngle,
      boolean callout,
      double[] labelPoint,
      String textAnchor) {
    this.status = status;
    this.fullCircle = fullCircle;
    this.path = path;
    this.middleAngle = middleAngle;
    this.callout = callout;
    this.labelX = format(labelPoint[0]);
    this.labelY = format(labelPoint[1]);
    double[] leaderStart = callout ? point(middleAngle, CALLOUT_ORIGIN_RADIUS) : null;
    this.leaderStartX = leaderStart == null ? "" : format(leaderStart[0]);
    this.leaderStartY = leaderStart == null ? "" : format(leaderStart[1]);
    this.leaderEndX = callout ? this.labelX : "";
    this.leaderEndY = callout ? this.labelY : "";
    this.textAnchor = textAnchor == null ? "middle" : textAnchor;
  }

  static List<OctaneGatePieSlice> layoutLabels(List<OctaneGatePieSlice> slices) {
    if (slices.isEmpty()) {
      return List.of();
    }
    boolean[] callouts = new boolean[slices.size()];
    for (int index = 0; index < slices.size(); index++) {
      callouts[index] = slices.get(index).status.getPercentage() < THIN_SLICE_PERCENTAGE;
    }
    for (int left = 0; left < slices.size(); left++) {
      for (int right = left + 1; right < slices.size(); right++) {
        if (labelsOverlap(slices.get(left), slices.get(right))) {
          callouts[left] = true;
          callouts[right] = true;
        }
      }
    }

    List<LabelPlacement> leftPlacements = new ArrayList<>();
    List<LabelPlacement> rightPlacements = new ArrayList<>();
    for (int index = 0; index < slices.size(); index++) {
      if (!callouts[index]) {
        continue;
      }
      OctaneGatePieSlice slice = slices.get(index);
      double[] desired = point(slice.middleAngle, LABEL_RADIUS);
      LabelPlacement placement = new LabelPlacement(index, desired[1]);
      if (Math.cos(Math.toRadians(slice.middleAngle)) < 0.0) {
        leftPlacements.add(placement);
      } else {
        rightPlacements.add(placement);
      }
    }
    distributeLabels(leftPlacements);
    distributeLabels(rightPlacements);

    OctaneGatePieSlice[] laidOut = slices.toArray(new OctaneGatePieSlice[0]);
    applyPlacements(laidOut, leftPlacements, false);
    applyPlacements(laidOut, rightPlacements, true);
    return List.of(laidOut);
  }

  public String getPath() {
    return path;
  }

  public boolean isFullCircle() {
    return fullCircle;
  }

  public String getColor() {
    return status.getColor();
  }

  public String getLabel() {
    return status.getLabel();
  }

  public int getCount() {
    return status.getCount();
  }

  public String getTitle() {
    return status.getTitle();
  }

  public String getPercentageLabel() {
    return status.getPercentageLabel();
  }

  public String getLabelX() {
    return labelX;
  }

  public String getLabelY() {
    return labelY;
  }

  public boolean isCallout() {
    return callout;
  }

  public String getLeaderStartX() {
    return leaderStartX;
  }

  public String getLeaderStartY() {
    return leaderStartY;
  }

  public String getLeaderEndX() {
    return leaderEndX;
  }

  public String getLeaderEndY() {
    return leaderEndY;
  }

  public String getTextAnchor() {
    return textAnchor == null ? "middle" : textAnchor;
  }

  private static String buildPath(double startAngle, double endAngle) {
    double[] start = point(startAngle, RADIUS);
    double[] end = point(endAngle, RADIUS);
    int largeArc = endAngle - startAngle > 180.0 ? 1 : 0;
    return String.format(
        Locale.ROOT,
        "M %.3f %.3f L %.3f %.3f A %.3f %.3f 0 %d 1 %.3f %.3f Z",
        CENTER,
        CENTER,
        start[0],
        start[1],
        RADIUS,
        RADIUS,
        largeArc,
        end[0],
        end[1]);
  }

  private static boolean labelsOverlap(OctaneGatePieSlice left, OctaneGatePieSlice right) {
    double[] leftPoint = point(left.middleAngle, LABEL_RADIUS);
    double[] rightPoint = point(right.middleAngle, LABEL_RADIUS);
    double leftWidth = estimatedLabelWidth(left.getPercentageLabel());
    double rightWidth = estimatedLabelWidth(right.getPercentageLabel());
    return Math.abs(leftPoint[0] - rightPoint[0])
            < (leftWidth + rightWidth) / 2.0 + LABEL_HORIZONTAL_PADDING
        && Math.abs(leftPoint[1] - rightPoint[1]) < LABEL_HEIGHT;
  }

  private static double estimatedLabelWidth(String value) {
    return Math.max(8.0, value.length() * 2.45);
  }

  private static void distributeLabels(List<LabelPlacement> placements) {
    if (placements.isEmpty()) {
      return;
    }
    placements.sort(Comparator.comparingDouble(LabelPlacement::y));
    double nextY = CALLOUT_MIN_Y;
    for (LabelPlacement placement : placements) {
      placement.y = Math.max(placement.y, nextY);
      nextY = placement.y + CALLOUT_MIN_GAP;
    }
    double overflow = placements.get(placements.size() - 1).y - CALLOUT_MAX_Y;
    if (overflow > 0.0) {
      for (LabelPlacement placement : placements) {
        placement.y -= overflow;
      }
    }
    for (int index = placements.size() - 2; index >= 0; index--) {
      LabelPlacement current = placements.get(index);
      LabelPlacement next = placements.get(index + 1);
      current.y = Math.min(current.y, next.y - CALLOUT_MIN_GAP);
    }
    double underflow = CALLOUT_MIN_Y - placements.get(0).y;
    if (underflow > 0.0) {
      for (LabelPlacement placement : placements) {
        placement.y += underflow;
      }
    }
  }

  private static void applyPlacements(
      OctaneGatePieSlice[] slices, List<LabelPlacement> placements, boolean rightSide) {
    double labelX = rightSide ? CALLOUT_LABEL_X : 100.0 - CALLOUT_LABEL_X;
    String anchor = rightSide ? "start" : "end";
    for (LabelPlacement placement : placements) {
      OctaneGatePieSlice slice = slices[placement.index];
      slices[placement.index] =
          new OctaneGatePieSlice(
              slice.status,
              slice.fullCircle,
              slice.path,
              slice.middleAngle,
              true,
              new double[] {labelX, placement.y},
              anchor);
    }
  }

  private static double[] point(double angle, double radius) {
    double radians = Math.toRadians(angle);
    return new double[] {CENTER + radius * Math.cos(radians), CENTER + radius * Math.sin(radians)};
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static final class LabelPlacement {
    private final int index;
    private double y;

    private LabelPlacement(int index, double y) {
      this.index = index;
      this.y = y;
    }

    private double y() {
      return y;
    }
  }
}
