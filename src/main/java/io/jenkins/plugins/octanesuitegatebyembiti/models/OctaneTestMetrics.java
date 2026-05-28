package io.jenkins.plugins.octanesuitegatebyembiti.models;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OctaneTestMetrics implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String NO_BASELINE = "No previous cycle";
  private static final String NEUTRAL = "neutral";
  private static final String POSITIVE = "positive";
  private static final String NEGATIVE = "negative";

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
    return card("avg-time", "Avg. Execution Time", value, detail, trend.text, trend.tone, "timer");
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
        "chart");
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
        "activity");
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
        "defect");
  }

  private static OctaneTestMetricCard card(
      String key,
      String title,
      String value,
      String detail,
      String trendText,
      String trendTone,
      String icon) {
    return new OctaneTestMetricCard(key, title, value, detail, trendText, trendTone, icon);
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
    return Trend.of(text, tone(delta, higherIsBetter));
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
    return Trend.of(sign + delta + " from last cycle", tone(delta, higherIsBetter));
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
        sign + formatDuration(Math.abs(delta)) + " from last cycle", tone(delta, higherIsBetter));
  }

  private static String tone(double delta, boolean higherIsBetter) {
    boolean good = higherIsBetter ? delta > 0 : delta < 0;
    return good ? POSITIVE : NEGATIVE;
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
}
