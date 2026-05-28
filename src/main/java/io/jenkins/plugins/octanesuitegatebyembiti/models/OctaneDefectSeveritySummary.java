package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OctaneDefectSeveritySummary implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String EMPTY_COLOR = "#AEAFB1";
  private static final String CLOSED_COLOR = "#5A5B5B";
  private static final String ALL_CLOSED_COLOR = "#7BE5B3";

  private final int critical;
  private final int veryHigh;
  private final int high;
  private final int medium;
  private final int low;
  private final int unspecified;
  private final int closed;

  private OctaneDefectSeveritySummary(
      int critical, int veryHigh, int high, int medium, int low, int unspecified, int closed) {
    this.critical = Math.max(0, critical);
    this.veryHigh = Math.max(0, veryHigh);
    this.high = Math.max(0, high);
    this.medium = Math.max(0, medium);
    this.low = Math.max(0, low);
    this.unspecified = Math.max(0, unspecified);
    this.closed = Math.max(0, closed);
  }

  public static OctaneDefectSeveritySummary empty() {
    return new OctaneDefectSeveritySummary(0, 0, 0, 0, 0, 0, 0);
  }

  public static OctaneDefectSeveritySummary fromDefects(List<DefectRecord> defects) {
    if (defects == null || defects.isEmpty()) {
      return empty();
    }

    int critical = 0;
    int veryHigh = 0;
    int high = 0;
    int medium = 0;
    int low = 0;
    int unspecified = 0;
    int closed = 0;
    Set<String> seenDefectIds = new LinkedHashSet<>();
    for (DefectRecord defect : defects) {
      String key = defectKey(defect);
      if (!seenDefectIds.add(key)) {
        continue;
      }
      if (!defect.isOpen()) {
        closed++;
        continue;
      }

      Severity severity = Severity.from(defect);
      if (severity == Severity.CRITICAL) {
        critical++;
      } else if (severity == Severity.VERY_HIGH) {
        veryHigh++;
      } else if (severity == Severity.HIGH) {
        high++;
      } else if (severity == Severity.MEDIUM) {
        medium++;
      } else if (severity == Severity.LOW) {
        low++;
      } else {
        unspecified++;
      }
    }
    return new OctaneDefectSeveritySummary(
        critical, veryHigh, high, medium, low, unspecified, closed);
  }

  public int getCritical() {
    return critical;
  }

  public int getVeryHigh() {
    return veryHigh;
  }

  public int getHigh() {
    return high;
  }

  public int getMedium() {
    return medium;
  }

  public int getLow() {
    return low;
  }

  public int getUnspecified() {
    return unspecified;
  }

  public int getClosed() {
    return closed;
  }

  public int getOpenTotal() {
    return critical + veryHigh + high + medium + low + unspecified;
  }

  public int getTotal() {
    return getOpenTotal() + closed;
  }

  public boolean isVisible() {
    return getTotal() > 0;
  }

  public boolean isAllClosed() {
    return closed > 0 && getOpenTotal() == 0;
  }

  public List<Bucket> getBuckets() {
    List<Bucket> buckets = new ArrayList<>();
    buckets.add(
        new Bucket("Critical", "Critical severity", critical, colorFor(critical, "#9D1D34")));
    buckets.add(
        new Bucket("Very High", "Very High severity", veryHigh, colorFor(veryHigh, "#D1334C")));
    buckets.add(new Bucket("High", "High severity", high, colorFor(high, "#ED8D25")));
    buckets.add(new Bucket("Medium", "Medium severity", medium, colorFor(medium, "#FFD700")));
    buckets.add(new Bucket("Low", "Low severity", low, colorFor(low, "#ACAF4B")));
    buckets.add(
        new Bucket(
            "Unspecified", "Unspecified severity", unspecified, colorFor(unspecified, "#D4D59F")));
    buckets.add(new Bucket("Closed", "Closed Issues", closed, closedColor()));
    return buckets;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("critical", critical);
    values.put("veryHigh", veryHigh);
    values.put("high", high);
    values.put("medium", medium);
    values.put("low", low);
    values.put("unspecified", unspecified);
    values.put("closed", closed);
    values.put("openTotal", getOpenTotal());
    values.put("total", getTotal());
    values.put("allClosed", isAllClosed());
    List<Map<String, Object>> bucketValues = new ArrayList<>();
    for (Bucket bucket : getBuckets()) {
      bucketValues.add(bucket.toMap());
    }
    values.put("buckets", bucketValues);
    return values;
  }

  private static String defectKey(DefectRecord defect) {
    if (!Util.isBlank(defect.getId())) {
      return defect.getId();
    }
    return defect.getName()
        + "|"
        + defect.getSeverity()
        + "|"
        + defect.getPriority()
        + "|"
        + defect.getRunId()
        + "|"
        + defect.getTestId();
  }

  private static String colorFor(int count, String activeColor) {
    return count > 0 ? activeColor : EMPTY_COLOR;
  }

  private String closedColor() {
    if (closed <= 0) {
      return EMPTY_COLOR;
    }
    return isAllClosed() ? ALL_CLOSED_COLOR : CLOSED_COLOR;
  }

  public static final class Bucket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String label;
    private final String tooltip;
    private final int count;
    private final String color;

    private Bucket(String label, String tooltip, int count, String color) {
      this.label = label;
      this.tooltip = tooltip;
      this.count = count;
      this.color = color;
    }

    public String getLabel() {
      return label;
    }

    public int getCount() {
      return count;
    }

    public String getTooltip() {
      return tooltip;
    }

    public String getColor() {
      return color;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("label", label);
      values.put("tooltip", tooltip);
      values.put("count", count);
      values.put("color", color);
      return values;
    }
  }

  private enum Severity {
    CRITICAL,
    VERY_HIGH,
    HIGH,
    MEDIUM,
    LOW,
    UNSPECIFIED;

    private static Severity from(DefectRecord defect) {
      String signal =
          (defect.getSeverity() + " " + defect.getPriority()).toLowerCase(Locale.ENGLISH);
      if (signal.contains("critical") || signal.contains("blocker") || signal.contains("urgent")) {
        return CRITICAL;
      }
      if (signal.contains("very high") || signal.contains("very_high")) {
        return VERY_HIGH;
      }
      if (signal.contains("high")) {
        return HIGH;
      }
      if (signal.contains("medium") || signal.contains("major")) {
        return MEDIUM;
      }
      if (signal.contains("low") || signal.contains("minor")) {
        return LOW;
      }
      return UNSPECIFIED;
    }
  }
}
