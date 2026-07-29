package io.jenkins.plugins.octanesuitegatebyembiti.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class Util {
  private static final int MAX_LOG_VALUE_LENGTH = 2_048;

  private Util() {}

  public static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  public static String trimTrailingSlash(String value) {
    String trimmed = trimToEmpty(value);
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  public static List<String> splitCsv(String value) {
    String trimmed = trimToEmpty(value);
    if (trimmed.isEmpty()) {
      return Collections.emptyList();
    }

    String[] parts = trimmed.split(",");
    List<String> values = new ArrayList<>();
    for (String part : parts) {
      String item = normalizeStatus(part);
      if (!item.isEmpty()) {
        values.add(item);
      }
    }
    return values;
  }

  public static List<String> splitIdList(String value) {
    String trimmed = trimToEmpty(value);
    if (trimmed.isEmpty()) {
      return Collections.emptyList();
    }

    LinkedHashSet<String> values = new LinkedHashSet<>();
    for (String part : trimmed.split("[,\\s]+")) {
      String item = trimToEmpty(part);
      if (!item.isEmpty()) {
        values.add(item);
      }
    }
    return new ArrayList<>(values);
  }

  public static String normalizeStatus(String value) {
    return trimToEmpty(value).toLowerCase().replace('-', '_').replace(' ', '_');
  }

  public static boolean isBlank(String value) {
    return trimToEmpty(value).isEmpty();
  }

  /** Keeps untrusted values on one bounded Jenkins console line. */
  public static String forLog(String value) {
    String source = value == null ? "" : value;
    StringBuilder safe = new StringBuilder(Math.min(source.length(), MAX_LOG_VALUE_LENGTH));
    for (int index = 0; index < source.length() && safe.length() < MAX_LOG_VALUE_LENGTH; index++) {
      char character = source.charAt(index);
      safe.append(Character.isISOControl(character) ? ' ' : character);
    }
    if (source.length() > MAX_LOG_VALUE_LENGTH) {
      safe.append("...");
    }
    return safe.toString();
  }
}
