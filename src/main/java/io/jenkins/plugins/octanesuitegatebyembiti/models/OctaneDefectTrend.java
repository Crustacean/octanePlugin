package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OctaneDefectTrend implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final int MAX_POINTS = 2000;

  public static final String OPENED_COLOR = "#ff6361";
  public static final String CLOSED_COLOR = "#7BE5B3";

  private final String startedAt;
  private final long durationMillis;
  private final List<Point> points;

  private OctaneDefectTrend(String startedAt, long durationMillis, List<Point> points) {
    this.startedAt = startedAt;
    this.durationMillis = Math.max(1L, durationMillis);
    this.points = List.copyOf(points);
  }

  public static OctaneDefectTrend start(String startedAt, long durationMillis) {
    return new OctaneDefectTrend(startedAt, durationMillis, List.of(new Point(0L, 0, 0)));
  }

  public OctaneDefectTrend append(String updatedAt, OctaneRiskHeatMap heatMap) {
    Point latest = latestPoint();
    int opened = latest.getOpened();
    int closed = latest.getClosed();
    if (heatMap != null && heatMap.isEnabled() && heatMap.isAvailable()) {
      OctaneDefectSeveritySummary summary = heatMap.getDefectSeveritySummary();
      opened = summary.getTotal();
      closed = summary.getClosed();
    }
    return append(elapsedMillis(updatedAt), opened, closed);
  }

  public OctaneDefectTrend append(long elapsedMillis, int opened, int closed) {
    long safeElapsed = Math.max(0L, Math.min(durationMillis, elapsedMillis));
    Point point = new Point(safeElapsed, opened, closed);
    List<Point> updatedPoints = new ArrayList<>(points);
    if (!updatedPoints.isEmpty()
        && updatedPoints.get(updatedPoints.size() - 1).getElapsedMillis() == safeElapsed) {
      updatedPoints.set(updatedPoints.size() - 1, point);
    } else {
      updatedPoints.add(point);
    }
    while (updatedPoints.size() > MAX_POINTS) {
      updatedPoints.remove(1);
    }
    return new OctaneDefectTrend(startedAt, durationMillis, updatedPoints);
  }

  public String getStartedAt() {
    return startedAt;
  }

  public long getDurationMillis() {
    return durationMillis;
  }

  public List<Point> getPoints() {
    return points;
  }

  public int getOpenedTotal() {
    return latestPoint().getOpened();
  }

  public int getClosedTotal() {
    return latestPoint().getClosed();
  }

  public int getMaximumCount() {
    int maximum = 0;
    for (Point point : points) {
      maximum = Math.max(maximum, Math.max(point.getOpened(), point.getClosed()));
    }
    return maximum;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("startedAt", startedAt);
    values.put("durationMillis", durationMillis);
    values.put("openedTotal", getOpenedTotal());
    values.put("closedTotal", getClosedTotal());
    values.put("openedColor", OPENED_COLOR);
    values.put("closedColor", CLOSED_COLOR);
    List<Map<String, Object>> pointValues = new ArrayList<>();
    for (Point point : points) {
      pointValues.add(point.toMap());
    }
    values.put("points", pointValues);
    return values;
  }

  private Point latestPoint() {
    return points.isEmpty() ? new Point(0L, 0, 0) : points.get(points.size() - 1);
  }

  private long elapsedMillis(String updatedAt) {
    try {
      return Duration.between(Instant.parse(startedAt), Instant.parse(updatedAt)).toMillis();
    } catch (RuntimeException e) {
      return latestPoint().getElapsedMillis();
    }
  }

  public static final class Point implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long elapsedMillis;
    private final int opened;
    private final int closed;

    private Point(long elapsedMillis, int opened, int closed) {
      this.elapsedMillis = Math.max(0L, elapsedMillis);
      this.opened = Math.max(0, opened);
      this.closed = Math.max(0, closed);
    }

    public long getElapsedMillis() {
      return elapsedMillis;
    }

    public int getOpened() {
      return opened;
    }

    public int getClosed() {
      return closed;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("elapsedMillis", elapsedMillis);
      values.put("opened", opened);
      values.put("closed", closed);
      return values;
    }
  }
}
