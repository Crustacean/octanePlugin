package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OctaneTestMetrics implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String NO_BASELINE = "No previous cycle";
  private static final String NEUTRAL = "neutral";
  private static final String POSITIVE = "positive";
  private static final String NEGATIVE = "negative";
  private static final String WARNING = "warning";
  private static final double ACTION_DEGRADATION_PERCENT = 30.0;

  private final List<OctaneTestMetricCard> cards;

  private OctaneTestMetrics(List<OctaneTestMetricCard> cards) {
    this.cards = List.copyOf(cards);
  }

  public static OctaneTestMetrics empty() {
    return new OctaneTestMetrics(
        List.of(
            card(
                "avg-time",
                "Avg. Execution Time",
                "N/A",
                "0 executed tests",
                NO_BASELINE,
                NEUTRAL,
                "timer"),
            card(
                "success-rate",
                "Success Rate",
                "0.0%",
                "0 / 0 passed",
                NO_BASELINE,
                NEUTRAL,
                "chart"),
            card(
                "execution",
                "Execution Completion",
                "0.0%",
                "0 / 0 executed",
                NO_BASELINE,
                NEUTRAL,
                "activity"),
            card(
                "defects",
                "Open Defects",
                "N/A",
                "Risk heat map unavailable",
                NO_BASELINE,
                NEUTRAL,
                "defect")));
  }

  public static OctaneTestMetrics fromSnapshots(
      OctaneGateReportSnapshot current, OctaneGateReportSnapshot previous) {
    if (current == null) {
      return empty();
    }

    List<OctaneTestMetricCard> cards = new ArrayList<>();
    cards.add(avgExecutionTime(current, previous));
    cards.add(successRate(current, previous));
    cards.add(executionCompletion(current, previous));
    cards.add(openDefects(current, previous));
    return new OctaneTestMetrics(cards);
  }

  public List<OctaneTestMetricCard> getCards() {
    return cards;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    List<Map<String, Object>> cardValues = new ArrayList<>();
    for (OctaneTestMetricCard card : cards) {
      cardValues.add(card.toMap());
    }
    values.put("cards", cardValues);
    return values;
  }

  private static OctaneTestMetricCard avgExecutionTime(
      OctaneGateReportSnapshot current, OctaneGateReportSnapshot previous) {
    int executed = current.getExecutedTestCount();
    Long currentAverage = executed == 0 ? null : current.getTestingElapsedMillis() / executed;
    Long previousAverage =
        previous == null || previous.getExecutedTestCount() == 0
            ? null
            : previous.getTestingElapsedMillis() / previous.getExecutedTestCount();
    String value = currentAverage == null ? "N/A" : formatDuration(currentAverage);
    String detail = executed + " executed " + (executed == 1 ? "test" : "tests");
    Trend trend =
        currentAverage == null
            ? Trend.neutral("Awaiting executed tests")
            : durationTrend(currentAverage, previousAverage, false);
    return card(
        "avg-time",
        "Avg. Execution Time",
        value,
        detail,
        trend.text,
        trend.tone,
        "timer",
        0.0,
        sparklinePoints(currentAverage, previousAverage),
        List.of());
  }

  private static OctaneTestMetricCard successRate(
      OctaneGateReportSnapshot current, OctaneGateReportSnapshot previous) {
    int total = current.getProjectTestTotal();
    double currentRate = total == 0 ? 0.0 : current.getPassedTestCount() * 100.0 / total;
    Double previousRate =
        previous == null || previous.getProjectTestTotal() == 0
            ? null
            : previous.getPassedTestCount() * 100.0 / previous.getProjectTestTotal();
    Trend trend = percentTrend(currentRate, previousRate, true);
    return card(
        "success-rate",
        "Success Rate",
        formatPercent(currentRate),
        current.getPassedTestCount() + " / " + total + " passed",
        trend.text,
        trend.tone,
        "chart",
        currentRate,
        "",
        List.of());
  }

  private static OctaneTestMetricCard executionCompletion(
      OctaneGateReportSnapshot current, OctaneGateReportSnapshot previous) {
    int total = current.getProjectTestTotal();
    double currentRate = total == 0 ? 0.0 : current.getExecutedTestCount() * 100.0 / total;
    Double previousRate =
        previous == null || previous.getProjectTestTotal() == 0
            ? null
            : previous.getExecutedTestCount() * 100.0 / previous.getProjectTestTotal();
    Trend trend = percentTrend(currentRate, previousRate, true);
    return card(
        "execution",
        "Execution Completion",
        formatPercent(currentRate),
        current.getExecutedTestCount() + " / " + total + " executed",
        trend.text,
        trend.tone,
        "activity",
        currentRate,
        "",
        List.of());
  }

  private static OctaneTestMetricCard openDefects(
      OctaneGateReportSnapshot current, OctaneGateReportSnapshot previous) {
    int total = current.getProjectTestTotal();
    if (!current.hasDefectMetrics()) {
      return card(
          "defects",
          "Open Defects",
          "N/A",
          "Risk heat map unavailable",
          NO_BASELINE,
          NEUTRAL,
          "defect");
    }
    int currentOpenDefects = current.getOpenDefectCount();
    Integer previousOpenDefects =
        previous == null || !previous.hasDefectMetrics() ? null : previous.getOpenDefectCount();
    double perHundred = total == 0 ? 0.0 : currentOpenDefects * 100.0 / total;
    Trend trend = countTrend(currentOpenDefects, previousOpenDefects, false);
    return card(
        "defects",
        "Open Defects",
        currentOpenDefects + " open",
        String.format(Locale.ROOT, "%.1f per 100 tests", perHundred),
        trend.text,
        trend.tone,
        "defect",
        0.0,
        "",
        defectSegments(current));
  }

  private static OctaneTestMetricCard card(
      String key,
      String title,
      String value,
      String detail,
      String trendText,
      String trendTone,
      String icon) {
    return card(key, title, value, detail, trendText, trendTone, icon, 0.0, "", List.of());
  }

  private static OctaneTestMetricCard card(
      String key,
      String title,
      String value,
      String detail,
      String trendText,
      String trendTone,
      String icon,
      double progressPercent,
      String sparklinePoints,
      List<OctaneTestMetricSegment> segments) {
    return new OctaneTestMetricCard(
        key,
        title,
        value,
        detail,
        trendText,
        trendTone,
        icon,
        progressPercent,
        sparklinePoints,
        segments);
  }

  private static Trend percentTrend(double current, Double previous, boolean higherIsBetter) {
    if (previous == null) {
      return Trend.neutral(NO_BASELINE);
    }
    double delta = current - previous;
    if (Math.abs(delta) < 0.05) {
      return Trend.neutral("No change from last cycle");
    }
    String sign = delta > 0 ? "+" : "";
    String text = sign + formatPercent(delta) + (delta > 0 ? " improvement" : " from last cycle");
    return Trend.of(text, trendTone(current, previous, higherIsBetter));
  }

  private static Trend countTrend(int current, Integer previous, boolean higherIsBetter) {
    if (previous == null) {
      return Trend.neutral(NO_BASELINE);
    }
    int delta = current - previous;
    if (delta == 0) {
      return Trend.neutral("No change from last cycle");
    }
    String sign = delta > 0 ? "+" : "";
    return Trend.of(
        sign + delta + " from last cycle",
        trendTone(current, previous.doubleValue(), higherIsBetter));
  }

  private static Trend durationTrend(long current, Long previous, boolean higherIsBetter) {
    if (previous == null) {
      return Trend.neutral(NO_BASELINE);
    }
    long delta = current - previous;
    if (Math.abs(delta) < 1000L) {
      return Trend.neutral("No change from last cycle");
    }
    String sign = delta > 0 ? "+" : "-";
    return Trend.of(
        sign + formatDuration(Math.abs(delta)) + " from last cycle",
        trendTone(current, previous.doubleValue(), higherIsBetter));
  }

  private static String trendTone(double current, double previous, boolean higherIsBetter) {
    double delta = current - previous;
    boolean good = higherIsBetter ? delta > 0 : delta < 0;
    if (good) {
      return POSITIVE;
    }
    double degradationPercent =
        previous == 0.0 ? 100.0 : Math.abs(delta) * 100.0 / Math.abs(previous);
    return degradationPercent > ACTION_DEGRADATION_PERCENT ? NEGATIVE : WARNING;
  }

  private static String sparklinePoints(Long current, Long previous) {
    if (current == null) {
      return "4,32 20,32 36,32 52,32";
    }
    double start = previous == null ? current.doubleValue() : previous.doubleValue();
    double end = current.doubleValue();
    double maximum = Math.max(1.0, Math.max(start, end));
    StringBuilder points = new StringBuilder();
    for (int index = 0; index < 4; index++) {
      double ratio = index / 3.0;
      double value = start + ((end - start) * ratio);
      int x = 4 + (index * 16);
      int y = (int) Math.round(36.0 - ((value / maximum) * 28.0));
      if (index > 0) {
        points.append(' ');
      }
      points.append(x).append(',').append(Math.max(4, Math.min(36, y)));
    }
    return points.toString();
  }

  private static List<OctaneTestMetricSegment> defectSegments(OctaneGateReportSnapshot snapshot) {
    OctaneDefectSeveritySummary summary = snapshot.getRiskHeatMap().getDefectSeveritySummary();
    int total = summary.getOpenTotal();
    if (total <= 0) {
      return List.of();
    }

    List<SegmentDefinition> definitions = new ArrayList<>();
    Set<String> claimedTypes = new LinkedHashSet<>();
    for (OctaneDefectGroup group : snapshot.getDefectMetrics().getConfiguredGroups()) {
      List<String> groupTypes =
          group.getNormalizedTypes().stream().filter(type -> !claimedTypes.contains(type)).toList();
      if (groupTypes.isEmpty()) {
        continue;
      }
      claimedTypes.addAll(groupTypes);
      definitions.add(
          new SegmentDefinition(
              displayName(group.getName()), groupTypes, highestSeverity(groupTypes)));
    }
    for (String type : OctaneDefectSeveritySummary.getOpenTypes()) {
      String normalized = OctaneDefectSeveritySummary.normalizeOpenType(type);
      if (!claimedTypes.contains(normalized)) {
        definitions.add(
            new SegmentDefinition(
                displayName(type), List.of(normalized), severityRank(normalized)));
      }
    }

    List<OctaneTestMetricSegment> segments = new ArrayList<>();
    for (SegmentDefinition definition : definitions) {
      int count = definition.types.stream().mapToInt(summary::getOpenCount).sum();
      if (count <= 0) {
        continue;
      }
      String label = definition.label + " (" + count + ")";
      String shortLabel = firstLetter(definition.label) + " (" + count + ")";
      segments.add(
          new OctaneTestMetricSegment(
              label,
              shortLabel,
              count,
              count * 100.0 / total,
              severityKey(definition.rank),
              definition.rank));
    }
    segments.sort(
        Comparator.comparingInt(OctaneTestMetricSegment::getSeverityRank)
            .thenComparing(OctaneTestMetricSegment::getLabel, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(segments);
  }

  private static int highestSeverity(List<String> types) {
    return types.stream().mapToInt(OctaneTestMetrics::severityRank).min().orElse(5);
  }

  private static int severityRank(String type) {
    switch (OctaneDefectSeveritySummary.normalizeOpenType(type)) {
      case "critical":
        return 0;
      case "veryhigh":
        return 1;
      case "high":
        return 2;
      case "medium":
        return 3;
      case "low":
        return 4;
      default:
        return 5;
    }
  }

  private static String severityKey(int rank) {
    switch (rank) {
      case 0:
        return "critical";
      case 1:
        return "very-high";
      case 2:
        return "high";
      case 3:
        return "medium";
      case 4:
        return "low";
      default:
        return "unspecified";
    }
  }

  private static String displayName(String value) {
    String normalized =
        value == null
            ? ""
            : value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('-', ' ').replace('_', ' ');
    List<String> words = new ArrayList<>();
    for (String word : normalized.trim().split("\\s+")) {
      if (!word.isEmpty()) {
        words.add(
            word.substring(0, 1).toUpperCase(Locale.ROOT)
                + word.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return String.join(" ", words);
  }

  private static String firstLetter(String value) {
    return value == null || value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(Locale.ROOT);
  }

  private static String formatPercent(double value) {
    return String.format(Locale.ROOT, "%.1f%%", value);
  }

  private static String formatDuration(long millis) {
    Duration duration = Duration.ofMillis(Math.max(0L, millis));
    long minutes = duration.toMinutes();
    long seconds = duration.minusMinutes(minutes).toSeconds();
    if (minutes <= 0) {
      return seconds + "s";
    }
    return minutes + "m " + seconds + "s";
  }

  private static class Trend {
    private final String text;
    private final String tone;

    private Trend(String text, String tone) {
      this.text = text;
      this.tone = tone;
    }

    private static Trend of(String text, String tone) {
      return new Trend(text, tone);
    }

    private static Trend neutral(String text) {
      return new Trend(text, NEUTRAL);
    }
  }

  private static class SegmentDefinition {
    private final String label;
    private final List<String> types;
    private final int rank;

    private SegmentDefinition(String label, List<String> types, int rank) {
      this.label = label;
      this.types = List.copyOf(types);
      this.rank = rank;
    }
  }
}
