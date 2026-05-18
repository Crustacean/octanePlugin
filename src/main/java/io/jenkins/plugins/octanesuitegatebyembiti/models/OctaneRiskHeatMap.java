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
  private final int defectCount;

  private OctaneRiskHeatMap(
      boolean enabled,
      boolean available,
      String message,
      OctaneRiskHeatMapNode root,
      int defectCount) {
    this.enabled = enabled;
    this.available = available;
    this.message = Util.trimToEmpty(message);
    this.root = root;
    this.defectCount = Math.max(0, defectCount);
  }

  public static OctaneRiskHeatMap disabled() {
    return new OctaneRiskHeatMap(false, false, "", null, 0);
  }

  public static OctaneRiskHeatMap waiting() {
    return new OctaneRiskHeatMap(true, false, "Risk heat map will appear after the first poll.", null, 0);
  }

  public static OctaneRiskHeatMap unavailable(String message) {
    return new OctaneRiskHeatMap(true, false, message, null, 0);
  }

  public static OctaneRiskHeatMap empty(String workspaceId) {
    String label = Util.isBlank(workspaceId) ? "Workspace" : "Workspace " + workspaceId;
    OctaneRiskHeatMapNode root =
        new OctaneRiskHeatMapNode("workspace", label, 0, 0, 0, java.util.List.of());
    return new OctaneRiskHeatMap(true, true, "No linked open defects were found.", root, 0);
  }

  public static OctaneRiskHeatMap of(OctaneRiskHeatMapNode root, int defectCount) {
    return new OctaneRiskHeatMap(true, true, "", root, defectCount);
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

  public int getDefectCount() {
    return defectCount;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("enabled", enabled);
    values.put("available", available);
    values.put("message", message);
    values.put("riskScore", getRiskScore());
    values.put("defectCount", defectCount);
    values.put("root", root == null ? null : root.toMap());
    return values;
  }
}
