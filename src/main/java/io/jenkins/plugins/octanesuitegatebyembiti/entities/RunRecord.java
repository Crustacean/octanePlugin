package io.jenkins.plugins.octanesuitegatebyembiti.entities;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class RunRecord implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String id;
  private final String name;
  private final String status;
  private final String runByName;
  private final String testId;
  private final String testName;
  private final String projectId;
  private final String projectName;

  public RunRecord(String id, String name, String status) {
    this(id, name, status, "");
  }

  public RunRecord(String id, String name, String status, String runByName) {
    this(id, name, status, runByName, "", "", "", "");
  }

  public RunRecord(
      String id,
      String name,
      String status,
      String runByName,
      String testId,
      String testName,
      String projectId,
      String projectName) {
    this.id = Util.trimToEmpty(id);
    this.name = Util.trimToEmpty(name);
    this.status = Util.trimToEmpty(status);
    this.runByName = Util.trimToEmpty(runByName);
    this.testId = Util.trimToEmpty(testId);
    this.testName = Util.trimToEmpty(testName);
    this.projectId = Util.trimToEmpty(projectId);
    this.projectName = Util.trimToEmpty(projectName);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getRunByName() {
    return runByName;
  }

  public String getTestId() {
    return testId;
  }

  public String getTestName() {
    return testName;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProjectName() {
    return projectName;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id);
    values.put("name", name);
    values.put("status", status);
    values.put("runByName", runByName);
    values.put("testId", testId);
    values.put("testName", testName);
    values.put("projectId", projectId);
    values.put("projectName", projectName);
    return values;
  }
}
