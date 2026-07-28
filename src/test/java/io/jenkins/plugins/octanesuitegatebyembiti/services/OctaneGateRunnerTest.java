package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
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
  public void identicalRegressionAndCriticalIdsMatchCriticalOnlyConfiguration() throws Exception {
    GateRequest overlapping = new GateRequest("octane-prod", "75295");
    OctaneGateScope overlappingCritical = new OctaneGateScope("critical");
    overlappingCritical.setSuiteRunId("75295");
    overlapping.setScopes(List.of(overlappingCritical));

    GateRequest criticalOnly = new GateRequest("octane-prod", "");
    OctaneGateScope onlyCritical = new OctaneGateScope("critical");
    onlyCritical.setSuiteRunId("75295");
    criticalOnly.setScopes(List.of(onlyCritical));

    OctaneGateRunner.validateSuiteRunSources(overlapping);
    OctaneGateRunner.validateSuiteRunSources(criticalOnly);
    assertEquals(
        OctaneGateRunner.regressionSuiteRunIdsForCriteria(criticalOnly),
        OctaneGateRunner.regressionSuiteRunIdsForCriteria(overlapping));
    assertTrue(OctaneGateRunner.regressionSuiteRunIdsForCriteria(overlapping).isEmpty());
  }

  @Test
  public void emptyRegressionRequiresCriticalSuiteRunIds() throws Exception {
    GateRequest request = new GateRequest("octane-prod", "");

    try {
      OctaneGateRunner.validateSuiteRunSources(request);
      fail("Expected a critical suite run validation error.");
    } catch (hudson.AbortException e) {
      assertTrue(e.getMessage().contains("critical Octane suite run selection is required"));
    }
  }

  @Test
  public void releaseSprintSelectionKeepsRegressionEvaluationEnabled() {
    GateRequest request = new GateRequest("octane-prod", "Release 2.4, Sprint 3");

    assertTrue(OctaneGateRunner.regressionSelectionEnabled(request));
    assertTrue(OctaneGateRunner.regressionSuiteRunIdsForCriteria(request).isEmpty());
  }

  @Test
  public void identicalReleaseSprintSelectionsAreEvaluatedAsCriticalOnly() {
    GateRequest request = new GateRequest("octane-prod", "Release 2.4, Sprint 3");
    OctaneGateScope critical = new OctaneGateScope("critical");
    critical.setSuiteRunId("Release 2.4, Sprint 3");
    request.setScopes(List.of(critical));

    assertFalse(OctaneGateRunner.regressionSelectionEnabled(request));
  }

  @Test
  public void identicalAndOmittedRegressionIdsProduceTheSameCriticalOnlyResult() throws Exception {
    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    GateRequest overlapping = criticalOnlyRequest("75295");
    GateRequest omitted = criticalOnlyRequest("");

    GateResult overlappingResult = refreshCriticalOnlyResult(runner, overlapping);
    GateResult omittedResult = refreshCriticalOnlyResult(runner, omitted);

    assertTrue(overlappingResult.isPassed());
    assertTrue(omittedResult.isPassed());
    assertTrue(overlappingResult.isTerminal());
    assertTrue(omittedResult.isTerminal());
    assertFalse(overlappingResult.isRegressionEvaluationEnabled());
    assertFalse(omittedResult.isRegressionEvaluationEnabled());
    assertEquals(
        overlappingResult.getScopedMetrics().get("critical").getPassRate(),
        omittedResult.getScopedMetrics().get("critical").getPassRate(),
        0.000001);
    assertEquals(
        overlappingResult.getCriteriaEvaluation().toMap(),
        omittedResult.getCriteriaEvaluation().toMap());
    assertEquals("critical.executionRate == 100", overlappingResult.getCriteria());
    assertEquals(overlappingResult.getCriteria(), omittedResult.getCriteria());
    assertFalse(overlappingResult.getCriteria().contains("regressions."));
  }

  @Test
  public void taskCriteriaScenariosUseOnlyApplicableBuckets() throws Exception {
    String criticalAndDefectCriteria =
        "(critical.executionRate == 100 AND critical.passRate == 100) "
            + "AND (defects.major < 10% AND defects.minor < 20%) "
            + "AND (defects.Unspecified == 0%)";

    GateResult identicalIds =
        refreshTaskCriteriaScenario(
            "1196,1200,1204",
            "(regressions.executionRate == 100 AND regressions.passRate >= 95) "
                + "AND "
                + criticalAndDefectCriteria);
    GateResult omittedRegressionOr =
        refreshTaskCriteriaScenario(
            "",
            "(regressions.executionRate == 100 AND regressions.passRate >= 95) "
                + "OR "
                + criticalAndDefectCriteria);
    GateResult distinctRegression =
        refreshTaskCriteriaScenario("1197,2200,1201", criticalAndDefectCriteria);
    GateResult mixedRegressionBranches =
        refreshTaskCriteriaScenario(
            "",
            "(regressions.executionRate == 100 AND critical.executionRate == 100) "
                + "AND (critical.passRate == 100 OR regressions.passRate >= 95) "
                + "AND (defects.major < 10% AND defects.minor < 20%) "
                + "AND (defects.Unspecified == 0%)");
    GateResult mixedRegressionOrBranches =
        refreshTaskCriteriaScenario(
            "",
            "(regressions.executionRate == 100 AND critical.executionRate == 100) "
                + "OR (critical.passRate == 100 OR regressions.passRate >= 95) "
                + "AND (defects.major < 10% AND defects.minor < 20%) "
                + "AND (defects.Unspecified == 0%)");

    assertCriticalOnlyScenario(identicalIds, criticalAndDefectCriteria);
    assertCriticalOnlyScenario(omittedRegressionOr, criticalAndDefectCriteria);
    assertCriticalOnlyScenario(mixedRegressionBranches, criticalAndDefectCriteria);
    assertCriticalOnlyScenario(
        mixedRegressionOrBranches,
        "(critical.executionRate == 100 OR critical.passRate == 100) "
            + "AND (defects.major < 10% AND defects.minor < 20%) "
            + "AND (defects.Unspecified == 0%)");

    assertTrue(distinctRegression.isRegressionEvaluationEnabled());
    assertTrue(distinctRegression.isPassed());
    assertEquals(criticalAndDefectCriteria, distinctRegression.getCriteria());
    assertNoRegressionComparisons(distinctRegression);
    assertEquals(3, distinctRegression.getSuiteRuns().size());
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
  public void timeoutMinutesExtendedDefaultsToZeroAndRejectsNegativeValues() {
    GateRequest request = new GateRequest("octane-prod", "4501");

    assertEquals(0, request.getTimeoutMinutesExtended());

    request.setTimeoutMinutesExtended(30);
    assertEquals(30, request.getTimeoutMinutesExtended());

    request.setTimeoutMinutesExtended(-5);
    assertEquals(0, request.getTimeoutMinutesExtended());
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
                List.of(new RunRecord("1", "one", "passed"), new RunRecord("2", "two", "passed"))),
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

  @Test
  public void defectCriteriaFetchesDefectsWithoutEnablingHeatMapReport() throws Exception {
    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    GateRequest request = new GateRequest("octane-prod", "4501");
    OctaneDefectGroup major = new OctaneDefectGroup("major");
    major.setTypes("Critical, Very High, High, Unspecified");
    request.setDefectGroups(List.of(major));
    request.setCriteria("defects.MAJOR == 100% AND defects.criticalCount == 1");

    GateResult result =
        runner.refreshPassedResult(
            new DefectAwareOctaneClient(),
            previousPassedResult(request),
            request,
            request.getSuiteRunIds(),
            "1001",
            "2001",
            CriteriaExpression.parse(request.getCriteria()),
            request.createStatusClassifier(),
            taskListener(new ByteArrayOutputStream()),
            new OctaneGateReportPublisher() {});

    assertTrue(result.isPassed());
    assertFalse(result.getRiskHeatMap().isEnabled());
    assertEquals(1.0, result.getDefectMetrics().value("majorCount"), 0.000001);
    assertEquals(100.0, result.getDefectMetrics().value("major"), 0.000001);
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

  private GateRequest criticalOnlyRequest(String regressionSuiteRunId) {
    GateRequest request = new GateRequest("octane-prod", regressionSuiteRunId);
    request.setCriteria("regressions.executionRate == 100 AND critical.executionRate == 100");
    OctaneGateScope critical = new OctaneGateScope("critical");
    critical.setSuiteRunId("75295");
    request.setScopes(List.of(critical));
    return request;
  }

  private GateResult refreshCriticalOnlyResult(OctaneGateRunner runner, GateRequest request)
      throws Exception {
    return runner.refreshPassedResult(
        new FakeOctaneClient(List.of(new RunRecord("1", "critical", "passed"))),
        previousPassedResult(request),
        request,
        OctaneGateRunner.regressionSuiteRunIdsForCriteria(request),
        "1001",
        "2001",
        CriteriaExpression.parse(request.getCriteria()),
        request.createStatusClassifier(),
        taskListener(new ByteArrayOutputStream()),
        new OctaneGateReportPublisher() {});
  }

  private GateResult refreshTaskCriteriaScenario(String regressionSuiteRunIds, String criteria)
      throws Exception {
    GateRequest request = new GateRequest("octane-prod", regressionSuiteRunIds);
    request.setCriteria(criteria);
    OctaneGateScope critical = new OctaneGateScope("critical");
    critical.setSuiteRunId("1196,1200,1204");
    request.setScopes(List.of(critical));
    OctaneDefectGroup major = new OctaneDefectGroup("major");
    major.setTypes("Critical, Very High, High, Unspecified");
    OctaneDefectGroup minor = new OctaneDefectGroup("minor");
    minor.setTypes("Low, Medium");
    request.setDefectGroups(List.of(major, minor));

    OctaneGateRunner runner =
        new OctaneGateRunner(
            Clock.fixed(Instant.parse("2026-05-16T14:00:00Z"), ZoneOffset.UTC),
            new OctaneGateLogListener());
    return runner.refreshPassedResult(
        new NoDefectOctaneClient(),
        previousPassedResult(request),
        request,
        OctaneGateRunner.regressionSuiteRunIdsForCriteria(request),
        "1001",
        "2001",
        CriteriaExpression.parse(criteria),
        request.createStatusClassifier(),
        taskListener(new ByteArrayOutputStream()),
        new OctaneGateReportPublisher() {});
  }

  private void assertCriticalOnlyScenario(GateResult result, String expectedCriteria) {
    assertFalse(result.isRegressionEvaluationEnabled());
    assertTrue(result.isPassed());
    assertEquals(expectedCriteria, result.getCriteria());
    assertNoRegressionComparisons(result);
    assertTrue(result.getSuiteRuns().isEmpty());
  }

  private void assertNoRegressionComparisons(GateResult result) {
    assertEquals(5, result.getCriteriaEvaluation().getComparisons().size());
    assertTrue(
        result.getCriteriaEvaluation().getComparisons().stream()
            .noneMatch(
                comparison ->
                    comparison
                        .getMetricReference()
                        .toLowerCase(java.util.Locale.ROOT)
                        .startsWith("regression")));
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

    @Override
    public Map<String, List<RunRecord>> fetchSuiteChildRuns(
        String sharedSpaceId, String workspaceId, List<String> suiteRunIds) {
      return suiteRunIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> runs));
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

    @Override
    public Map<String, List<RunRecord>> fetchSuiteChildRuns(
        String sharedSpaceId, String workspaceId, List<String> suiteRunIds) throws IOException {
      throw new IOException("Octane not ready");
    }
  }

  private static class DefectAwareOctaneClient extends FakeOctaneClient {
    private final List<DefectRecord> defects =
        List.of(
            new DefectRecord("d1", "Critical defect", "Critical", "", "opened", "1", "", "", ""));

    DefectAwareOctaneClient() {
      super(List.of(new RunRecord("1", "one", "passed")));
    }

    @Override
    public List<DefectRecord> fetchLinkedDefects(
        String sharedSpaceId,
        String workspaceId,
        Map<String, List<RunRecord>> suiteRuns,
        String defectQuery,
        int maxDefects) {
      return defects;
    }

    @Override
    public List<DefectRecord> fetchDefectsByIds(
        String sharedSpaceId, String workspaceId, List<String> defectIds, int maxDefects) {
      return defects;
    }
  }

  private static class NoDefectOctaneClient extends FakeOctaneClient {
    NoDefectOctaneClient() {
      super(List.of(new RunRecord("1", "one", "passed")));
    }

    @Override
    public List<DefectRecord> fetchLinkedDefects(
        String sharedSpaceId,
        String workspaceId,
        Map<String, List<RunRecord>> suiteRuns,
        String defectQuery,
        int maxDefects) {
      return List.of();
    }

    @Override
    public List<DefectRecord> fetchDefectsByIds(
        String sharedSpaceId, String workspaceId, List<String> defectIds, int maxDefects) {
      return List.of();
    }
  }
}
