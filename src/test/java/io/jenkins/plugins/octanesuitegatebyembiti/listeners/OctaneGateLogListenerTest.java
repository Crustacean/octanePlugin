package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneGateLogListenerTest {
  @Test
  public void logsReadableSuiteRunSummaryAndCompactPollingMetrics() {
    OctaneGateLogListener logListener = new OctaneGateLogListener();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    TaskListener listener = new CapturingTaskListener(output);
    GateRequest request = new GateRequest("octane-prod", "450312,450309");
    request.setCriteria(
        "(regressions.executionRate == 100 AND regressions.passRate >= 95) OR "
            + "(critical.executionRate == 100 AND critical.passRate == 100)");
    OctaneGateScope criticalScope = new OctaneGateScope("critical");
    criticalScope.setSuiteRunId("450306");
    request.setScopes(List.of(criticalScope));

    logListener.logWaiting(listener, request, request.getSuiteRunIds());
    logListener.logPollResult(listener, resultWithCriticalScope());
    logListener.logPollResult(listener, resultWithCriticalScope());

    String log = output.toString(StandardCharsets.UTF_8);
    String lineSeparator = System.lineSeparator();
    String regressionsMetrics =
        "Regressions suite runs: execution 0.00%, pass 0.00%, total 4, executed 0,"
            + " passed 0, failed 0, skipped 0, running 4.";
    String criticalMetrics =
        "Critical suite runs: execution 100.00%, pass 100.00%, total 2, executed 2,"
            + " passed 2, failed 0, skipped 0, running 0.";
    assertTrue(log.contains("Waiting for ALM Octane suite run(s)"));
    assertTrue(log.contains("Regressions suite runs: 450312, 450309"));
    assertTrue(log.contains("Critical suite runs: 450306"));
    assertTrue(log.contains(regressionsMetrics));
    assertTrue(log.contains(criticalMetrics));
    assertTrue(
        log.contains(
            criticalMetrics + lineSeparator + lineSeparator + regressionsMetrics + lineSeparator));
    assertFalse(log.contains("child run statuses"));
    assertFalse(log.contains("suite run IDs 450306 metrics"));
  }

  private GateResult resultWithCriticalScope() {
    return new GateResult(
        "450312,450309",
        "(regressions.executionRate == 100 AND regressions.passRate >= 95) OR "
            + "(critical.executionRate == 100 AND critical.passRate == 100)",
        true,
        false,
        new GateMetrics(4, 0, 0, 0, 0, 4),
        List.of(
            new RunRecord("450313", "global one", "planned"),
            new RunRecord("450314", "global two", "planned"),
            new RunRecord("450310", "global three", "planned"),
            new RunRecord("450311", "global four", "planned")),
        Map.of(
            "450312",
            List.of(
                new RunRecord("450313", "global one", "planned"),
                new RunRecord("450314", "global two", "planned")),
            "450309",
            List.of(
                new RunRecord("450310", "global three", "planned"),
                new RunRecord("450311", "global four", "planned"))),
        Map.of(
            "critical",
            new GateScopeResult(
                "critical",
                "",
                List.of(),
                "450306",
                List.of("450306"),
                new GateMetrics(2, 2, 2, 0, 0, 0),
                List.of(
                    new RunRecord("450307", "critical one", "passed"),
                    new RunRecord("450308", "critical two", "passed")),
                Map.of(
                    "450306",
                    List.of(
                        new RunRecord("450307", "critical one", "passed"),
                        new RunRecord("450308", "critical two", "passed"))))),
        Instant.parse("2026-05-13T00:00:00Z"));
  }

  private static class CapturingTaskListener implements TaskListener {
    private final PrintStream logger;

    CapturingTaskListener(ByteArrayOutputStream output) {
      logger = new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    @Override
    public PrintStream getLogger() {
      return logger;
    }
  }
}
