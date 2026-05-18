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

  public RunRecord(String id, String name, String status) {
    this(id, name, status, "");
  }

  public RunRecord(String id, String name, String status, String runByName) {
    this.id = Util.trimToEmpty(id);
    this.name = Util.trimToEmpty(name);
    this.status = Util.trimToEmpty(status);
    this.runByName = Util.trimToEmpty(runByName);
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

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id);
    values.put("name", name);
    values.put("status", status);
    values.put("runByName", runByName);
    return values;
  }
}
