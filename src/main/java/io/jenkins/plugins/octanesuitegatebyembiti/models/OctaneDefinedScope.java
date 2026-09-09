package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OctaneDefinedScope implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String project;
  private final String owner;

  public OctaneDefinedScope(String project, String owner) {
    this.project = Util.trimToEmpty(project);
    this.owner = Util.trimToEmpty(owner);
  }

  public static List<OctaneDefinedScope> parse(String configuredScope) {
    String configured = Util.trimToEmpty(configuredScope);
    if (configured.isEmpty()) {
      return List.of();
    }
    List<OctaneDefinedScope> scopes = new ArrayList<>();
    for (String rawEntry : splitOutsideQuotes(configured, ',')) {
      String entry = Util.trimToEmpty(rawEntry);
      if (entry.isEmpty()) {
        continue;
      }
      int separator = lastIndexOutsideQuotes(entry, '-');
      if (separator < 0) {
        scopes.add(new OctaneDefinedScope(entry, ""));
      } else {
        scopes.add(
            new OctaneDefinedScope(entry.substring(0, separator), entry.substring(separator + 1)));
      }
    }
    return List.copyOf(scopes);
  }

  public String getProject() {
    return project;
  }

  public String getOwner() {
    return owner;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("project", project);
    values.put("owner", owner);
    return values;
  }

  private static List<String> splitOutsideQuotes(String value, char separator) {
    List<String> values = new ArrayList<>();
    int start = 0;
    char activeQuote = 0;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (activeQuote != 0) {
        if (character == activeQuote && !isEscaped(value, index)) {
          activeQuote = 0;
        }
        continue;
      }
      if (isQuote(character) && isQuoteBlockStart(value, index, character)) {
        activeQuote = character;
      } else if (character == separator) {
        values.add(value.substring(start, index));
        start = index + 1;
      }
    }
    values.add(value.substring(start));
    return values;
  }

  private static int lastIndexOutsideQuotes(String value, char separator) {
    int lastSeparator = -1;
    char activeQuote = 0;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (activeQuote != 0) {
        if (character == activeQuote && !isEscaped(value, index)) {
          activeQuote = 0;
        }
        continue;
      }
      if (isQuote(character) && isQuoteBlockStart(value, index, character)) {
        activeQuote = character;
      } else if (character == separator) {
        lastSeparator = index;
      }
    }
    return lastSeparator;
  }

  private static boolean isQuoteBlockStart(String value, int index, char quote) {
    if (quote == '\'' && index > 0 && Character.isLetterOrDigit(value.charAt(index - 1))) {
      return false;
    }
    for (int candidate = index + 1; candidate < value.length(); candidate++) {
      if (value.charAt(candidate) == quote && !isEscaped(value, candidate)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isQuote(char character) {
    return character == '"' || character == '\'' || character == '`';
  }

  private static boolean isEscaped(String value, int index) {
    int backslashes = 0;
    for (int candidate = index - 1;
        candidate >= 0 && value.charAt(candidate) == '\\';
        candidate--) {
      backslashes++;
    }
    return backslashes % 2 != 0;
  }
}
