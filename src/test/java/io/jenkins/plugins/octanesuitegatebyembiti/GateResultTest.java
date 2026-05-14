package io.jenkins.plugins.octanesuitegatebyembiti;

import static org.junit.Assert.assertEquals;

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
}
