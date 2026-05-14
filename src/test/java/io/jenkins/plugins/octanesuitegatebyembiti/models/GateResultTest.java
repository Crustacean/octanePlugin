package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;

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
  public void extractsIdsFromOctaneScopeQueries() {
    OctaneGateScope scope =
        new OctaneGateScope("critical", "test EQ {product_areas EQ {id EQ 1004||id=1005}}");

    assertEquals(List.of("1004", "1005"), scope.getReferencedIds());
  }
}
