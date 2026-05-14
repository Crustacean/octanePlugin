package io.jenkins.plugins.octanesuitegatebyembiti.entities;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;

public class RunRecord implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String id;
  private final String name;
  private final String status;

  public RunRecord(String id, String name, String status) {
    this.id = Util.trimToEmpty(id);
    this.name = Util.trimToEmpty(name);
    this.status = Util.trimToEmpty(status);
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
}
