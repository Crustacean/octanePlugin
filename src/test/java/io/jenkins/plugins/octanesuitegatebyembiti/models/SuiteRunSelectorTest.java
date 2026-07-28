package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class SuiteRunSelectorTest {
  @Test
  public void parsesNumericSuiteRunListsWithoutChangingExistingBehavior() {
    SuiteRunSelector selector = SuiteRunSelector.parse("1196,1200 1204,1204");

    assertEquals(SuiteRunSelector.Mode.EXPLICIT_IDS, selector.getMode());
    assertEquals(List.of("1196", "1200", "1204"), selector.getExplicitIds());
    assertFalse(selector.isDynamic());
  }

  @Test
  public void parsesReleaseAndSprintWithoutSplittingNamesOnWhitespace() {
    SuiteRunSelector selector = SuiteRunSelector.parse("Release 2.4, Sprint 3");

    assertEquals(SuiteRunSelector.Mode.RELEASE_SPRINT, selector.getMode());
    assertEquals("Release 2.4", selector.getReleaseName());
    assertEquals("Sprint 3", selector.getSprintName());
    assertTrue(selector.isDynamic());
    assertTrue(selector.getExplicitIds().isEmpty());
  }

  @Test
  public void keepsTwoNumericValuesAsExplicitSuiteRunIds() {
    SuiteRunSelector selector = SuiteRunSelector.parse("1196, 1200");

    assertEquals(SuiteRunSelector.Mode.EXPLICIT_IDS, selector.getMode());
    assertEquals(List.of("1196", "1200"), selector.getExplicitIds());
  }

  @Test
  public void rejectsIncompleteAndWildcardReleaseSprintSelectors() {
    assertThrows(IllegalArgumentException.class, () -> SuiteRunSelector.parse("Release 2.4, "));
    assertThrows(
        IllegalArgumentException.class, () -> SuiteRunSelector.parse("Release *, Sprint 3"));
  }
}
