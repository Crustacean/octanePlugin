package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;

import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.util.List;
import org.junit.Test;

public class OctaneGateRunnerTest {
  @Test
  public void criticalSuiteRunsOwnOverlappingGlobalIdsForCriteria() {
    GateRequest request = new GateRequest("octane-prod", "450297,450300,450303,450303");
    OctaneGateScope critical = new OctaneGateScope("critical");
    critical.setSuiteRunId("450303,450204,450204");
    request.setScopes(List.of(critical));

    assertEquals(
        List.of("450297", "450300"), OctaneGateRunner.globalSuiteRunIdsForCriteria(request));
    assertEquals(List.of("450303", "450204"), critical.getSuiteRunIds());
  }

  @Test
  public void nonCriticalSuiteRunScopesDoNotOwnGlobalIds() {
    GateRequest request = new GateRequest("octane-prod", "450297,450300,450303");
    OctaneGateScope smoke = new OctaneGateScope("smoke");
    smoke.setSuiteRunId("450303");
    request.setScopes(List.of(smoke));

    assertEquals(
        List.of("450297", "450300", "450303"),
        OctaneGateRunner.globalSuiteRunIdsForCriteria(request));
  }
}
