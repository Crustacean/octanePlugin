package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import java.util.List;
import org.junit.Test;

public class OctaneDefectLedgerTest {

  @Test
  public void replacesEarlierDefectStateWhenKnownDefectIsRefreshed() {
    OctaneDefectLedger ledger = new OctaneDefectLedger();

    ledger.merge(List.of(defect("901", "High", "opened")));
    ledger.merge(List.of(defect("901", "High", "closed")));

    OctaneDefectSeveritySummary summary =
        OctaneDefectSeveritySummary.fromDefects(ledger.getDefects());

    assertEquals(List.of("901"), ledger.getDefectIds());
    assertEquals(1, ledger.getDefects().size());
    assertFalse(ledger.getDefects().get(0).isOpen());
    assertEquals(0, summary.getOpenTotal());
    assertEquals(1, summary.getClosed());
  }

  @Test
  public void skipsBlankDefectIds() {
    OctaneDefectLedger ledger = new OctaneDefectLedger();

    ledger.merge(List.of(defect("", "High", "opened"), defect("902", "Critical", "opened")));

    assertEquals(List.of("902"), ledger.getDefectIds());
  }

  private DefectRecord defect(String id, String severity, String phase) {
    return new DefectRecord(id, "Defect " + id, severity, "", phase, "run", "test", "", "");
  }
}
