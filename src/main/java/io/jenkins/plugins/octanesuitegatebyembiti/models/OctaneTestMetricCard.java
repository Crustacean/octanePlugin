package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class OctaneTestMetricCard implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String key;
  private final String title;
  private final String value;
  private final String detail;
  private final String trendText;
  private final String trendTone;
  private final String icon;

  public OctaneTestMetricCard(
      String key,
      String title,
      String value,
      String detail,
      String trendText,
      String trendTone,
      String icon) {
    this.key = Util.trimToEmpty(key);
    this.title = Util.trimToEmpty(title);
    this.value = Util.trimToEmpty(value);
    this.detail = Util.trimToEmpty(detail);
    this.trendText = Util.trimToEmpty(trendText);
    this.trendTone = Util.trimToEmpty(trendTone);
    this.icon = Util.trimToEmpty(icon);
  }

  public String getKey() {
    return key;
  }

  public String getTitle() {
    return title;
  }

  public String getValue() {
    return value;
  }

  public String getDetail() {
    return detail;
  }

  public String getTrendText() {
    return trendText;
  }

  public String getTrendTone() {
    return trendTone;
  }

  public String getIcon() {
    return icon;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("key", key);
    values.put("title", title);
    values.put("value", value);
    values.put("detail", detail);
    values.put("trendText", trendText);
    values.put("trendTone", trendTone);
    values.put("icon", icon);
    return values;
  }
}
