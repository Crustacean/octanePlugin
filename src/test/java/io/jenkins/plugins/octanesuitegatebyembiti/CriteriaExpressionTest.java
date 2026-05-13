package io.jenkins.plugins.octanesuitegatebyembiti;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CriteriaExpressionTest {
  private final StatusClassifier classifier =
      new StatusClassifier(
          StatusClassifier.DEFAULT_PASSED_STATUSES,
          StatusClassifier.DEFAULT_FAILED_STATUSES,
          StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
          StatusClassifier.DEFAULT_RUNNING_STATUSES);

  @Test
  public void evaluatesShorthandExecutionAndPassThresholds() {
    MetricsContext context =
        context(
            List.of(
                new RunRecord("1", "first", "passed"),
                new RunRecord("2", "second", "passed"),
                new RunRecord("3", "third", "failed"),
                new RunRecord("4", "fourth", "skipped")));

    assertTrue(CriteriaExpression.parse("100% execution AND 50% pass").evaluate(context));
    assertFalse(CriteriaExpression.parse("100% execution AND 90% pass").evaluate(context));
  }

  @Test
  public void supportsParenthesesOrAndScopedMetrics() {
    Map<String, GateMetrics> scopes = new LinkedHashMap<>();
    scopes.put(
        "payments",
        GateMetrics.fromRuns(
            List.of(
                new RunRecord("1", "first", "passed"),
                new RunRecord("2", "second", "passed")),
            classifier));
    MetricsContext context =
        new MetricsContext(
            GateMetrics.fromRuns(
                List.of(
                    new RunRecord("1", "first", "passed"),
                    new RunRecord("2", "second", "failed")),
                classifier),
            scopes);

    assertTrue(
        CriteriaExpression.parse("(100% execution AND 100% pass) OR payments.pass == 100%")
            .evaluate(context));
    assertFalse(
        CriteriaExpression.parse("100% execution AND (100% pass OR payments.fail > 0%)")
            .evaluate(context));
  }

  @Test
  public void criteriaReadsCombinedScopeMetrics() {
    Map<String, GateMetrics> scopes = new LinkedHashMap<>();
    scopes.put(
        "payments",
        GateMetrics.fromRuns(
            List.of(
                new RunRecord("101", "product area 1004 run", "passed"),
                new RunRecord("102", "product area 1005 run", "failed")),
            classifier));
    MetricsContext context =
        new MetricsContext(GateMetrics.fromRuns(List.of(), classifier), scopes);

    assertTrue(CriteriaExpression.parse("payments.total == 2").evaluate(context));
    assertTrue(CriteriaExpression.parse("payments.passRate == 50").evaluate(context));
  }

  @Test(expected = CriteriaException.class)
  public void rejectsUnknownMetrics() {
    CriteriaExpression.parse("unknownMetric >= 10").evaluate(context(List.of()));
  }

  @Test(expected = CriteriaException.class)
  public void rejectsBadSyntax() {
    CriteriaExpression.parse("passRate >=");
  }

  @Test
  public void handlesZeroRunRates() {
    MetricsContext context = context(List.of());

    assertTrue(CriteriaExpression.parse("executionRate == 0 AND passRate == 0").evaluate(context));
    assertFalse(CriteriaExpression.parse("1% pass").evaluate(context));
  }

  private MetricsContext context(List<RunRecord> runs) {
    return new MetricsContext(GateMetrics.fromRuns(runs, classifier), Map.of());
  }
}
