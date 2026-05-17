package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.repositories.OctaneClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class OctaneGateRunnerTest {
  @Test
  public void criticalSuiteRunsOwnOverlappingRegressionIdsForCriteria() {
    GateRequest request = new GateRequest("octane-prod", "450297,450300,450303,450303");
    OctaneGateScope critical = new OctaneGateScope("critical");
    critical.setSuiteRunId("450303,450204,450204");
    request.setScopes(List.of(critical));

    assertEquals(
        List.of("450297", "450300"), OctaneGateRunner.regressionSuiteRunIdsForCriteria(request));
    assertEquals(List.of("450303", "450204"), critical.getSuiteRunIds());
  }

  @Test
  public void nonCriticalSuiteRunScopesDoNotOwnRegressionIds() {
    GateRequest request = new GateRequest("octane-prod", "450297,450300,450303");
    OctaneGateScope smoke = new OctaneGateScope("smoke");
    smoke.setSuiteRunId("450303");
    request.setScopes(List.of(smoke));

    assertEquals(
        List.of("450297", "450300", "450303"),
        OctaneGateRunner.regressionSuiteRunIdsForCriteria(request));
  }

  @Test
  public void refreshPassedResultPublishesFreshMetricsBeforeFinal() throws Exception {
    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setCriteria("100% execution");
    StatusClassifier classifier = request.createStatusClassifier();
    AtomicReference<GateResult> publishedResult = new AtomicReference<>();
    ByteArrayOutputStream log = new ByteArrayOutputStream();

    GateResult refreshedResult =
        runner.refreshPassedResult(
            new FakeOctaneClient(
                List.of(
                    new RunRecord("1", "one", "passed"),
                    new RunRecord("2", "two", "passed"))),
            previousPassedResult(request),
            request,
            request.getSuiteRunIds(),
            "1001",
            "2001",
            CriteriaExpression.parse(request.getCriteria()),
            classifier,
            taskListener(log),
            capturingPublisher(publishedResult));

    assertEquals(2, refreshedResult.getMetrics().getTotal());
    assertEquals(2, refreshedResult.getMetrics().getExecuted());
    assertTrue(refreshedResult.isPassed());
    assertNotNull(publishedResult.get());
    assertEquals(2, publishedResult.get().getMetrics().getTotal());
    assertTrue(log.toString(StandardCharsets.UTF_8).contains("Refreshing ALM Octane suite runs"));
  }

  @Test
  public void refreshPassedResultCanReturnNonPassingFreshMetrics() throws Exception {
    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setCriteria("100% execution");
    StatusClassifier classifier = request.createStatusClassifier();

    GateResult refreshedResult =
        runner.refreshPassedResult(
            new FakeOctaneClient(List.of(new RunRecord("1", "one", "planned"))),
            previousPassedResult(request),
            request,
            request.getSuiteRunIds(),
            "1001",
            "2001",
            CriteriaExpression.parse(request.getCriteria()),
            classifier,
            taskListener(new ByteArrayOutputStream()),
            new OctaneGateReportPublisher() {});

    assertFalse(refreshedResult.isPassed());
    assertEquals(1, refreshedResult.getMetrics().getRunning());
  }

  @Test
  public void refreshPassedResultKeepsPreviousPassWhenFinalRefreshFails() throws Exception {
    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    GateRequest request = new GateRequest("octane-prod", "4501");
    request.setCriteria("100% execution");
    GateResult previousResult = previousPassedResult(request);
    ByteArrayOutputStream log = new ByteArrayOutputStream();

    GateResult refreshedResult =
        runner.refreshPassedResult(
            new FailingOctaneClient(),
            previousResult,
            request,
            request.getSuiteRunIds(),
            "1001",
            "2001",
            CriteriaExpression.parse(request.getCriteria()),
            request.createStatusClassifier(),
            taskListener(log),
            new OctaneGateReportPublisher() {});

    assertSame(previousResult, refreshedResult);
    assertTrue(log.toString(StandardCharsets.UTF_8).contains("Skipped final ALM Octane refresh"));
  }

  private GateResult previousPassedResult(GateRequest request) {
    List<RunRecord> runs = List.of(new RunRecord("1", "one", "passed"));
    return new GateResult(
        request.getSuiteRunId(),
        request.getCriteria(),
        true,
        true,
        new GateMetrics(1, 1, 1, 0, 0, 0),
        runs,
        Map.of("4501", runs),
        Map.of(),
        Instant.parse("2026-05-16T13:59:00Z"));
  }

  private OctaneGateReportPublisher capturingPublisher(AtomicReference<GateResult> result) {
    return new OctaneGateReportPublisher() {
      @Override
      public void onPoll(GateResult pollResult, StatusClassifier classifier) {
        result.set(pollResult);
      }
    };
  }

  private TaskListener taskListener(ByteArrayOutputStream log) {
    return () -> new PrintStream(log, true, StandardCharsets.UTF_8);
  }

  private static class FakeOctaneClient extends OctaneClient {
    private final List<RunRecord> runs;

    FakeOctaneClient(List<RunRecord> runs) {
      super("http://octane.example", "client-id", "client-secret");
      this.runs = runs;
    }

    @Override
    public List<RunRecord> fetchSuiteChildRuns(
        String sharedSpaceId, String workspaceId, String suiteRunId) {
      return runs;
    }
  }

  private static class FailingOctaneClient extends OctaneClient {
    FailingOctaneClient() {
      super("http://octane.example", "client-id", "client-secret");
    }

    @Override
    public List<RunRecord> fetchSuiteChildRuns(
        String sharedSpaceId, String workspaceId, String suiteRunId) throws IOException {
      throw new IOException("Octane not ready");
    }
  }
}
