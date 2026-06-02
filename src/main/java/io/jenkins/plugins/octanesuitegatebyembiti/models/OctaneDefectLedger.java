package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OctaneDefectLedger {
  private final Map<String, DefectRecord> defectsById = new LinkedHashMap<>();

  public void merge(Collection<DefectRecord> defects) {
    if (defects == null || defects.isEmpty()) {
      return;
    }
    for (DefectRecord defect : defects) {
      if (defect == null || Util.isBlank(defect.getId())) {
        continue;
      }
      defectsById.put(defect.getId(), defect);
    }
  }

  public boolean isEmpty() {
    return defectsById.isEmpty();
  }

  public List<String> getDefectIds() {
    return new ArrayList<>(defectsById.keySet());
  }

  public List<DefectRecord> getDefects() {
    return new ArrayList<>(defectsById.values());
  }
}
