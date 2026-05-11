package io.jenkins.plugins.octanesuitegatebyembiti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Util {
  private Util() {}

  static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  static String trimTrailingSlash(String value) {
    String trimmed = trimToEmpty(value);
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  static List<String> splitCsv(String value) {
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

  static String normalizeStatus(String value) {
    return trimToEmpty(value).toLowerCase().replace('-', '_').replace(' ', '_');
  }

  static boolean isBlank(String value) {
    return trimToEmpty(value).isEmpty();
  }
}
