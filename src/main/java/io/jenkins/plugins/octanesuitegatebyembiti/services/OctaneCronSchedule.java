package io.jenkins.plugins.octanesuitegatebyembiti.services;

import hudson.scheduler.CronTab;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

final class OctaneCronSchedule implements OctaneProgressEmailScheduler.Schedule {
  private static final Map<Integer, String> DAYS_OF_WEEK =
      Map.of(
          0, "Sunday",
          1, "Monday",
          2, "Tuesday",
          3, "Wednesday",
          4, "Thursday",
          5, "Friday",
          6, "Saturday",
          7, "Sunday");

  private final String expression;
  private final CronTab cronTab;
  private final String description;

  OctaneCronSchedule(String expression) {
    this.expression = normalize(expression);
    if (this.expression.isEmpty()) {
      throw new IllegalArgumentException("Cron expression must not be blank.");
    }
    String[] fields = this.expression.split("\\s+");
    if (fields.length != 5) {
      throw new IllegalArgumentException("Cron expression must contain exactly five fields.");
    }
    try {
      cronTab = new CronTab(this.expression);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid cron expression: " + this.expression, e);
    }
    description = describe(fields);
  }

  @Override
  public String expression() {
    return expression;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public Instant nextAfter(Instant after, Instant lastDelivery, Duration minimumInterval) {
    Instant lowerBound = after.plusMillis(1L);
    if (lastDelivery != null) {
      Instant throttled = lastDelivery.plus(minimumInterval);
      if (throttled.isAfter(lowerBound)) {
        lowerBound = throttled;
      }
    }
    Calendar next = cronTab.ceil(lowerBound.toEpochMilli());
    return next.toInstant();
  }

  private String normalize(String value) {
    return Util.trimToEmpty(value).replaceAll("\\s+", " ");
  }

  private String describe(String[] fields) {
    String minute = fields[0];
    String hour = fields[1];
    String dayOfMonth = fields[2];
    String month = fields[3];
    String dayOfWeek = fields[4];

    if (allWildcards(fields)) {
      return "Every minute.";
    }
    if (minute.matches("\\*/\\d+")
        && "*".equals(hour)
        && "*".equals(dayOfMonth)
        && "*".equals(month)
        && "*".equals(dayOfWeek)) {
      int step = Integer.parseInt(minute.substring(2));
      return "Every " + step + " minutes.";
    }
    if (isNumber(minute)
        && hour.matches("\\d+-\\d+/\\d+")
        && "*".equals(dayOfMonth)
        && "*".equals(month)
        && "*".equals(dayOfWeek)) {
      String[] rangeAndStep = hour.split("/");
      String[] range = rangeAndStep[0].split("-");
      int step = Integer.parseInt(rangeAndStep[1]);
      return "At minute "
          + minute
          + " past every "
          + ordinal(step)
          + " hour from "
          + range[0]
          + " through "
          + range[1]
          + ".";
    }
    if (isNumber(minute)
        && "*".equals(hour)
        && "*".equals(dayOfMonth)
        && "*".equals(month)
        && dayOfWeek.matches("\\d-\\d")) {
      String[] range = dayOfWeek.split("-");
      return "At minute "
          + minute
          + " on every day-of-week from "
          + dayName(range[0])
          + " through "
          + dayName(range[1])
          + ".";
    }
    if (isNumber(minute)
        && isNumber(hour)
        && "*".equals(dayOfMonth)
        && "*".equals(month)
        && "*".equals(dayOfWeek)) {
      return String.format(
          Locale.ENGLISH,
          "At %02d:%02d every day.",
          Integer.parseInt(hour),
          Integer.parseInt(minute));
    }
    if (isNumber(minute)
        && "*".equals(hour)
        && "*".equals(dayOfMonth)
        && "*".equals(month)
        && "*".equals(dayOfWeek)) {
      return "At minute " + minute + " past every hour.";
    }
    return "According to cron fields: minute "
        + minute
        + ", hour "
        + hour
        + ", day-of-month "
        + dayOfMonth
        + ", month "
        + month
        + ", and day-of-week "
        + dayOfWeek
        + ".";
  }

  private boolean allWildcards(String[] fields) {
    for (String field : fields) {
      if (!"*".equals(field)) {
        return false;
      }
    }
    return true;
  }

  private boolean isNumber(String value) {
    return value.matches("\\d+");
  }

  private String dayName(String value) {
    return DAYS_OF_WEEK.getOrDefault(Integer.parseInt(value), value);
  }

  private String ordinal(int value) {
    int modulo100 = value % 100;
    if (modulo100 >= 11 && modulo100 <= 13) {
      return value + "th";
    }
    return switch (value % 10) {
      case 1 -> value + "st";
      case 2 -> value + "nd";
      case 3 -> value + "rd";
      default -> value + "th";
    };
  }
}
