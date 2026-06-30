package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;

import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OctaneDefectTrendTest {
  private static final String STARTED_AT = "2026-06-30T08:00:00Z";

  @Test
  public void startsAtZeroAndAppendsRaisedAndClosedTotals() {
    OctaneDefectTrend trend =
        OctaneDefectTrend.start(STARTED_AT, 120_000L)
            .append("2026-06-30T08:00:30Z", heatMapWithOpenAndClosedDefects());

    assertEquals(2, trend.getPoints().size());
    assertEquals(30_000L, trend.getPoints().get(1).getElapsedMillis());
    assertEquals(3, trend.getOpenedTotal());
    assertEquals(1, trend.getClosedTotal());
  }

  @Test
  public void replacesSameTimestampAndClampsToConfiguredDuration() {
    OctaneDefectTrend trend =
        OctaneDefectTrend.start(STARTED_AT, 60_000L)
            .append(20_000L, 1, 0)
            .append(20_000L, 2, 1)
            .append(90_000L, 4, 2);

    assertEquals(3, trend.getPoints().size());
    assertEquals(2, trend.getPoints().get(1).getOpened());
    assertEquals(60_000L, trend.getPoints().get(2).getElapsedMillis());
  }

  @Test
  public void unavailablePollCarriesForwardLastKnownTotals() {
    OctaneDefectTrend trend =
        OctaneDefectTrend.start(STARTED_AT, 120_000L)
            .append(15_000L, 5, 2)
            .append(
                "2026-06-30T08:00:45Z",
                OctaneRiskHeatMap.unavailable("Octane did not return defect data."));

    assertEquals(5, trend.getOpenedTotal());
    assertEquals(2, trend.getClosedTotal());
    assertEquals(45_000L, trend.getPoints().get(2).getElapsedMillis());
  }

  @Test
  public void exposesColorsAndSerializablePointData() {
    Map<String, Object> values =
        OctaneDefectTrend.start(STARTED_AT, 60_000L).append(30_000L, 2, 1).toMap();

    assertEquals("#ff6361", values.get("openedColor"));
    assertEquals("#7BE5B3", values.get("closedColor"));
    assertEquals(2, values.get("openedTotal"));
    assertEquals(1, values.get("closedTotal"));
    assertEquals(2, ((List<?>) values.get("points")).size());
  }

  private OctaneRiskHeatMap heatMapWithOpenAndClosedDefects() {
    List<DefectRecord> defects =
        List.of(
            new DefectRecord("1", "Open critical", "critical", "", "new", "1", "1", "", ""),
            new DefectRecord("2", "Open high", "high", "", "opened", "2", "2", "", ""),
            new DefectRecord("3", "Closed", "medium", "", "closed", "3", "3", "", ""));
    return OctaneRiskHeatMap.of(
        new OctaneRiskHeatMapNode("workspace", "Workspace", 80, 3, 3, List.of()),
        3,
        3,
        0,
        1,
        OctaneDefectSeveritySummary.fromDefects(defects));
  }
}
