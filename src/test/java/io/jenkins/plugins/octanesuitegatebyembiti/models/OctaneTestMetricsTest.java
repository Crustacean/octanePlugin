package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneTestMetricsTest {
  private final StatusClassifier classifier =
      new StatusClassifier(
          StatusClassifier.DEFAULT_PASSED_STATUSES,
          StatusClassifier.DEFAULT_FAILED_STATUSES,
          StatusClassifier.DEFAULT_NEUTRAL_STATUSES,
          StatusClassifier.DEFAULT_RUNNING_STATUSES);

  @Test
  public void groupsOpenDefectsWithoutDoubleCountingAndSortsByHighestSeverity() {
    OctaneDefectGroup major = group("major", "Critical, Very High, High");
    OctaneDefectGroup minor = group("minor", "Medium, Low");
    List<DefectRecord> defects =
        List.of(
            defect("1", "critical"),
            defect("2", "critical"),
            defect("3", "high"),
            defect("4", "medium"),
            defect("5", "medium"),
            defect("6", "low"),
            defect("7", ""));

    OctaneGateReportSnapshot snapshot =
        snapshot(new GateMetrics(10, 8, 6, 2, 0, 2), defects, List.of(major, minor), "00:08:00Z");
    OctaneTestMetricCard card = metric(snapshot, "defects");

    assertEquals(3, card.getSegments().size());
    assertEquals(List.of("Major (3)", "Minor (3)", "Unspecified (1)"), labels(card));
    assertEquals(List.of("critical", "medium", "unspecified"), severityKeys(card));
    assertEquals(
        100.0,
        card.getSegments().stream()
            .mapToDouble((OctaneTestMetricSegment segment) -> segment.getPercentage())
            .sum(),
        0.001);
    assertEquals("M (3)", card.getSegments().get(0).getShortLabel());
  }

  @Test
  public void assignsWarningAndActionPillsFromRelativeDegradation() {
    OctaneGateReportSnapshot previous =
        snapshot(new GateMetrics(100, 100, 90, 10, 0, 0), defects(10), List.of(), "00:10:00Z");
    OctaneGateReportSnapshot current =
        snapshot(new GateMetrics(100, 80, 40, 40, 0, 20), defects(12), List.of(), "00:12:00Z")
            .withCalculatedTestMetrics(previous);

    assertEquals("negative", metric(current, "success-rate").getTrendTone());
    assertEquals("warning", metric(current, "execution").getTrendTone());
    assertEquals("warning", metric(current, "defects").getTrendTone());
  }

  @Test
  public void rendererIncludesEveryContextualVisualizationAndResponsiveLabels() {
    OctaneDefectGroup major = group("major", "Critical, High");
    OctaneGateReportSnapshot snapshot =
        snapshot(
            new GateMetrics(10, 8, 6, 2, 0, 2),
            List.of(defect("1", "critical"), defect("2", "high")),
            List.of(major),
            "00:08:00Z");

    String html = snapshot.getTestMetricsHtml();

    assertTrue(html.contains("octane-test-metric-sparkline"));
    assertTrue(html.contains("octane-test-metric-gauge-fill"));
    assertTrue(html.contains("octane-test-metric-progress"));
    assertTrue(html.contains("data-test-metric-segment=\"true\""));
    assertTrue(html.contains("data-full-label=\"Major (2)\""));
    assertTrue(html.contains("data-short-label=\"M (2)\""));
    assertTrue(html.contains("octane-test-metric-defect-color-critical"));
  }

  private OctaneGateReportSnapshot snapshot(
      GateMetrics metrics,
      List<DefectRecord> defects,
      List<OctaneDefectGroup> groups,
      String elapsedTime) {
    OctaneDefectSeveritySummary summary = OctaneDefectSeveritySummary.fromDefects(defects);
    OctaneRiskHeatMap heatMap =
        OctaneRiskHeatMap.of(
            new OctaneRiskHeatMapNode("project", "Project", 50, 10, 5, List.of()),
            defects.size(),
            defects.size(),
            0,
            0,
            summary);
    GateResult result =
        new GateResult(
            "1001",
            "regressions.executionRate >= 0",
            false,
            false,
            metrics,
            List.of(),
            Map.of(),
            Map.of(),
            heatMap,
            new DefectCriteriaMetrics(summary, groups),
            Instant.parse("2026-07-27T" + elapsedTime));
    return OctaneGateReportSnapshot.fromResult(
        OctaneGateReportState.POLLING,
        "Polling",
        result,
        classifier,
        30,
        3600,
        "2026-07-27T00:00:00Z");
  }

  private List<DefectRecord> defects(int count) {
    List<DefectRecord> values = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      values.add(defect(String.valueOf(index + 1), "critical"));
    }
    return values;
  }

  private DefectRecord defect(String id, String severity) {
    return new DefectRecord(id, "Defect " + id, severity, "", "new", id, id, "project", "Project");
  }

  private OctaneDefectGroup group(String name, String types) {
    OctaneDefectGroup group = new OctaneDefectGroup(name);
    group.setTypes(types);
    return group;
  }

  private OctaneTestMetricCard metric(OctaneGateReportSnapshot snapshot, String key) {
    return snapshot.getTestMetrics().getCards().stream()
        .filter(card -> key.equals(card.getKey()))
        .findFirst()
        .orElseThrow();
  }

  private List<String> labels(OctaneTestMetricCard card) {
    return card.getSegments().stream()
        .map((OctaneTestMetricSegment segment) -> segment.getLabel())
        .toList();
  }

  private List<String> severityKeys(OctaneTestMetricCard card) {
    return card.getSegments().stream()
        .map((OctaneTestMetricSegment segment) -> segment.getSeverityKey())
        .toList();
  }
}
