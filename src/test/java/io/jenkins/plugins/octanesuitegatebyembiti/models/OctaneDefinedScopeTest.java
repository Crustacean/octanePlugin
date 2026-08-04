package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OctaneDefinedScopeTest {
  @Test
  public void parsesEntriesAtLastHyphenAndTitleCasesOwners() {
    List<OctaneDefinedScope> scopes =
        OctaneDefinedScope.parse("ESA - Imelda sanya, Bulk-data-Tony, Digisoc");

    assertEquals(3, scopes.size());
    assertEquals("ESA", scopes.get(0).getProject());
    assertEquals("Imelda Sanya", scopes.get(0).getOwner());
    assertEquals("Bulk-data", scopes.get(1).getProject());
    assertEquals("Tony", scopes.get(1).getOwner());
    assertEquals("Digisoc", scopes.get(2).getProject());
    assertEquals("-", scopes.get(2).getOwner());
  }

  @Test
  public void ignoresEmptyEntriesAndReturnsAnImmutableEmptyListForBlankInput() {
    assertTrue(OctaneDefinedScope.parse(null).isEmpty());
    assertTrue(OctaneDefinedScope.parse("  ").isEmpty());
    assertEquals(1, OctaneDefinedScope.parse(", Payments - ada lovelace, ").size());
  }

  @Test
  public void exposesEscapableMapValuesForClientRendering() {
    OctaneDefinedScope scope = OctaneDefinedScope.parse("Core <API> - o'BRIEN").get(0);

    assertEquals("Core <API>", scope.toMap().get("project"));
    assertEquals("O'Brien", scope.toMap().get("owner"));
  }

  @Test
  public void persistsDefinedScopeAcrossSnapshotUpdatesAndTesterPayloads() {
    GateRequest request = new GateRequest("octane", "1196");
    request.setDefinedScope("Payments - ada lovelace, Digisoc");

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.waiting(request, 30, "2026-08-04T10:00:00Z")
            .withState(OctaneGateReportState.POLLING, "Polling", "2026-08-04T10:01:00Z");

    assertEquals(2, snapshot.getDefinedScope().size());
    assertEquals("Ada Lovelace", snapshot.getDefinedScope().get(0).getOwner());
    assertEquals(2, ((List<?>) snapshot.getTesterDetails().get("definedScope")).size());
  }
}
