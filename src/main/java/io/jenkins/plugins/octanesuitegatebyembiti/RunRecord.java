package io.jenkins.plugins.octanesuitegatebyembiti;

import java.io.Serializable;

class RunRecord implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String id;
  private final String name;
  private final String status;

  RunRecord(String id, String name, String status) {
    this.id = Util.trimToEmpty(id);
    this.name = Util.trimToEmpty(name);
    this.status = Util.trimToEmpty(status);
  }

  String getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getStatus() {
    return status;
  }
}
