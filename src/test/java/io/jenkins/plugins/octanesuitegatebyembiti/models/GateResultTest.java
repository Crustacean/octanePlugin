package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GateResultTest {
  @Test
  public void exposesSuiteRunIdsInPipelineMap() {
    GateResult result =
        new GateResult(
            "1196,1200",
            "100% pass",
            true,
            true,
            new GateMetrics(1, 1, 1, 0, 0, 0),
            Map.of(),
            Instant.parse("2026-05-13T00:00:00Z"));

    assertEquals(List.of("1196", "1200"), result.toPipelineMap().get("suiteRunIds"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void keepsScopedMetricsCompatibleAndAddsScopeDetails() {
    GateResult result =
        new GateResult(
            "1196,1200",
            "critical.passRate == 100",
            true,
            true,
            new GateMetrics(2, 2, 1, 1, 0, 0),
            List.of(
                new RunRecord("101", "normal one", "passed"),
                new RunRecord("102", "normal two", "failed")),
            Map.of(
                "1196",
                List.of(new RunRecord("101", "normal one", "passed")),
                "1200",
                List.of(new RunRecord("102", "normal two", "failed"))),
            Map.of(
                "critical",
                new GateScopeResult(
                    "critical",
                    "test={((product_areas={id=1004||id=1005}))}",
                    List.of("1004", "1005"),
                    new GateMetrics(1, 1, 1, 0, 0, 0),
                    List.of(new RunRecord("101", "critical one", "passed")))),
            Instant.parse("2026-05-13T00:00:00Z"));

    Map<String, Object> pipelineMap = result.toPipelineMap();
    Map<String, Object> scopes = (Map<String, Object>) pipelineMap.get("scopes");
    Map<String, Object> criticalMetrics = (Map<String, Object>) scopes.get("critical");
    assertEquals(100.0, criticalMetrics.get("passRate"));

    Map<String, Object> scopeDetails = (Map<String, Object>) pipelineMap.get("scopeDetails");
    Map<String, Object> criticalDetails = (Map<String, Object>) scopeDetails.get("critical");
    assertEquals(List.of("1004", "1005"), criticalDetails.get("queryIds"));
    assertEquals(List.of("101"), criticalDetails.get("runIds"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void exposesSuiteRunBackedScopeDetails() {
    GateResult result =
        new GateResult(
            "450297,450300,450303",
            "critical.passRate == 100",
            true,
            true,
            new GateMetrics(4, 4, 4, 0, 0, 0),
            List.of(
                new RunRecord("450298", "normal one", "passed"),
                new RunRecord("450299", "normal two", "passed"),
                new RunRecord("450301", "normal three", "passed"),
                new RunRecord("450304", "overlap child", "passed")),
            Map.of(
                "450297",
                List.of(new RunRecord("450298", "normal one", "passed")),
                "450300",
                List.of(
                    new RunRecord("450299", "normal two", "passed"),
                    new RunRecord("450301", "normal three", "passed")),
                "450303",
                List.of(new RunRecord("450304", "overlap child", "passed"))),
            Map.of(
                "critical",
                new GateScopeResult(
                    "critical",
                    "",
                    List.of(),
                    "450303,450204",
                    List.of("450303", "450204"),
                    new GateMetrics(2, 2, 2, 0, 0, 0),
                    List.of(
                        new RunRecord("450304", "overlap child", "passed"),
                        new RunRecord("450205", "critical child", "passed")),
                    Map.of(
                        "450303",
                        List.of(new RunRecord("450304", "overlap child", "passed")),
                        "450204",
                        List.of(new RunRecord("450205", "critical child", "passed"))))),
            Instant.parse("2026-05-13T00:00:00Z"));

    Map<String, Object> pipelineMap = result.toPipelineMap();
    Map<String, Object> scopes = (Map<String, Object>) pipelineMap.get("scopes");
    Map<String, Object> criticalMetrics = (Map<String, Object>) scopes.get("critical");
    assertEquals(100.0, criticalMetrics.get("passRate"));

    Map<String, Object> scopeDetails = (Map<String, Object>) pipelineMap.get("scopeDetails");
    Map<String, Object> criticalDetails = (Map<String, Object>) scopeDetails.get("critical");
    assertEquals(List.of("450303", "450204"), criticalDetails.get("suiteRunIds"));
    assertEquals(List.of("450304", "450205"), criticalDetails.get("runIds"));

    Map<String, Object> criticalSuiteRuns = (Map<String, Object>) criticalDetails.get("suiteRuns");
    assertTrue(criticalSuiteRuns.containsKey("450303"));
    assertTrue(criticalSuiteRuns.containsKey("450204"));
  }

  @Test
  public void extractsIdsFromOctaneScopeQueries() {
    OctaneGateScope scope =
        new OctaneGateScope("critical", "test EQ {product_areas EQ {id EQ 1004||id=1005}}");

    assertEquals(List.of("1004", "1005"), scope.getReferencedIds());
  }

  @Test
  public void parsesSuiteRunIdsFromSuiteRunBackedScopes() {
    OctaneGateScope scope = new OctaneGateScope("critical");
    scope.setSuiteRunId("450303, 450204 450303");

    assertEquals(List.of("450303", "450204"), scope.getSuiteRunIds());
    assertTrue(scope.isSuiteRunScope());
  }
}
