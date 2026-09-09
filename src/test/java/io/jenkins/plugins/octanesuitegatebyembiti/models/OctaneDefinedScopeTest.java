package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class OctaneDefinedScopeTest {
  @Test
  public void parsesEntriesAtLastHyphenAndPreservesOwners() {
    List<OctaneDefinedScope> scopes =
        OctaneDefinedScope.parse("ESA - Imelda sanya, Bulk-data-Tony, Digisoc");

    assertEquals(3, scopes.size());
    assertEquals("ESA", scopes.get(0).getProject());
    assertEquals("Imelda sanya", scopes.get(0).getOwner());
    assertEquals("Bulk-data", scopes.get(1).getProject());
    assertEquals("Tony", scopes.get(1).getOwner());
    assertEquals("Digisoc", scopes.get(2).getProject());
    assertEquals("", scopes.get(2).getOwner());
  }

  @Test
  public void preservesQuotedProjectCommasWithoutCreatingAnOwner() {
    OctaneDefinedScope scope =
        OctaneDefinedScope.parse("regressions: \"SMTSL, MMI, LNM, Pochi\"").get(0);

    assertEquals("regressions: \"SMTSL, MMI, LNM, Pochi\"", scope.getProject());
    assertEquals("", scope.getOwner());
  }

  @Test
  public void separatesOwnerAtTheLastUnquotedHyphen() {
    OctaneDefinedScope scope =
        OctaneDefinedScope.parse("regressions: \"SMTSL, MMI, LNM, Pochi\" - james").get(0);

    assertEquals("regressions: \"SMTSL, MMI, LNM, Pochi\"", scope.getProject());
    assertEquals("james", scope.getOwner());
  }

  @Test
  public void preservesAQuotedOwnerAsOneValue() {
    OctaneDefinedScope scope =
        OctaneDefinedScope.parse("security tests - \"Mary, tom, bob\"").get(0);

    assertEquals("security tests", scope.getProject());
    assertEquals("\"Mary, tom, bob\"", scope.getOwner());
  }

  @Test
  public void parsesMixedQuotedAndUnquotedScopeStream() {
    List<OctaneDefinedScope> scopes =
        OctaneDefinedScope.parse(
            "regressions: \"SMTSL, MMI\" - james, secure checkout - tom, "
                + "security-alice, mini apps");

    assertEquals(4, scopes.size());
    assertScope(scopes.get(0), "regressions: \"SMTSL, MMI\"", "james");
    assertScope(scopes.get(1), "secure checkout", "tom");
    assertScope(scopes.get(2), "security", "alice");
    assertScope(scopes.get(3), "mini apps", "");
  }

  @Test
  public void supportsSingleAndBacktickQuotedBlocksAndIgnoresTheirHyphens() {
    List<OctaneDefinedScope> scopes =
        OctaneDefinedScope.parse("'Core, API - v2' - jane, `Mobile, App - beta` - sam");

    assertEquals(2, scopes.size());
    assertScope(scopes.get(0), "'Core, API - v2'", "jane");
    assertScope(scopes.get(1), "`Mobile, App - beta`", "sam");
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
    assertEquals("o'BRIEN", scope.toMap().get("owner"));
  }

  @Test
  public void persistsDefinedScopeAcrossSnapshotUpdatesAndTesterPayloads() {
    GateRequest request = new GateRequest("octane", "1196");
    request.setDefinedScope("Payments - ada lovelace, Digisoc");

    OctaneGateReportSnapshot snapshot =
        OctaneGateReportSnapshot.waiting(request, 30, "2026-08-04T10:00:00Z")
            .withState(OctaneGateReportState.POLLING, "Polling", "2026-08-04T10:01:00Z");

    assertEquals(2, snapshot.getDefinedScope().size());
    assertEquals("ada lovelace", snapshot.getDefinedScope().get(0).getOwner());
    assertEquals(2, ((List<?>) snapshot.getTesterDetails().get("definedScope")).size());
  }

  private void assertScope(OctaneDefinedScope scope, String project, String owner) {
    assertEquals(project, scope.getProject());
    assertEquals(owner, scope.getOwner());
  }
}
