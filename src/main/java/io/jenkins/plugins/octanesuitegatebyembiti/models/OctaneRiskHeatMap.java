package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class OctaneRiskHeatMap implements Serializable {
  private static final long serialVersionUID = 1L;

  private final boolean enabled;
  private final boolean available;
  private final String message;
  private final OctaneRiskHeatMapNode root;
  private final int fetchedDefectCount;
  private final int linkedDefectCount;
  private final int unlinkedOpenDefectCount;
  private final int ignoredClosedDefectCount;

  private OctaneRiskHeatMap(
      boolean enabled,
      boolean available,
      String message,
      OctaneRiskHeatMapNode root,
      int fetchedDefectCount,
      int linkedDefectCount,
      int unlinkedOpenDefectCount,
      int ignoredClosedDefectCount) {
    this.enabled = enabled;
    this.available = available;
    this.message = Util.trimToEmpty(message);
    this.root = root;
    this.fetchedDefectCount = Math.max(0, fetchedDefectCount);
    this.linkedDefectCount = Math.max(0, linkedDefectCount);
    this.unlinkedOpenDefectCount = Math.max(0, unlinkedOpenDefectCount);
    this.ignoredClosedDefectCount = Math.max(0, ignoredClosedDefectCount);
  }

  public static OctaneRiskHeatMap disabled() {
    return new OctaneRiskHeatMap(false, false, "", null, 0, 0, 0, 0);
  }

  public static OctaneRiskHeatMap waiting() {
    return new OctaneRiskHeatMap(
        true, false, "Risk heat map will appear after the first poll.", null, 0, 0, 0, 0);
  }

  public static OctaneRiskHeatMap unavailable(String message) {
    return new OctaneRiskHeatMap(true, false, message, null, 0, 0, 0, 0);
  }

  public static OctaneRiskHeatMap empty(String workspaceId) {
    String label = Util.isBlank(workspaceId) ? "Workspace" : "Workspace " + workspaceId;
    OctaneRiskHeatMapNode root =
        new OctaneRiskHeatMapNode("workspace", label, 0, 0, 0, java.util.List.of());
    return new OctaneRiskHeatMap(
        true, true, "No linked open defects were found.", root, 0, 0, 0, 0);
  }

  public static OctaneRiskHeatMap of(
      OctaneRiskHeatMapNode root,
      int fetchedDefectCount,
      int linkedDefectCount,
      int unlinkedOpenDefectCount,
      int ignoredClosedDefectCount) {
    return new OctaneRiskHeatMap(
        true,
        true,
        "",
        root,
        fetchedDefectCount,
        linkedDefectCount,
        unlinkedOpenDefectCount,
        ignoredClosedDefectCount);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isAvailable() {
    return available;
  }

  public String getMessage() {
    return message;
  }

  public OctaneRiskHeatMapNode getRoot() {
    return root;
  }

  public int getRiskScore() {
    return root == null ? 0 : root.getRiskScore();
  }

  public int getFetchedDefectCount() {
    return fetchedDefectCount;
  }

  public int getLinkedDefectCount() {
    return linkedDefectCount;
  }

  public int getUnlinkedOpenDefectCount() {
    return unlinkedOpenDefectCount;
  }

  public int getIgnoredClosedDefectCount() {
    return ignoredClosedDefectCount;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("enabled", enabled);
    values.put("available", available);
    values.put("message", message);
    values.put("riskScore", getRiskScore());
    values.put("fetchedDefectCount", fetchedDefectCount);
    values.put("linkedDefectCount", linkedDefectCount);
    values.put("unlinkedOpenDefectCount", unlinkedOpenDefectCount);
    values.put("ignoredClosedDefectCount", ignoredClosedDefectCount);
    values.put("root", root == null ? null : root.toMap());
    return values;
  }
}
