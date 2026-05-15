package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;
import java.util.Locale;

public class OctaneGateStatusCount implements Serializable {
  private static final long serialVersionUID = 1L;

  private final OctaneGateStatusBucket bucket;
  private final int count;
  private final double percentage;

  OctaneGateStatusCount(OctaneGateStatusBucket bucket, int count, int total) {
    this.bucket = bucket;
    this.count = count;
    this.percentage = total == 0 ? 0.0 : count * 100.0 / total;
  }

  public OctaneGateStatusBucket getBucket() {
    return bucket;
  }

  public String getLabel() {
    return bucket.getLabel();
  }

  public String getColor() {
    return bucket.getColor();
  }

  public int getCount() {
    return count;
  }

  public double getPercentage() {
    return percentage;
  }

  public String getFormattedPercentage() {
    return String.format(Locale.ROOT, "%.2f", percentage);
  }

  public String getTitle() {
    return getLabel() + ": " + count + " (" + getFormattedPercentage() + "%)";
  }

  public String getWidthStyle() {
    return "width: " + getFormattedPercentage() + "%; background: " + getColor() + ";";
  }

  public String getHeightStyle() {
    return "height: " + getFormattedPercentage() + "%; background: " + getColor() + ";";
  }
}
