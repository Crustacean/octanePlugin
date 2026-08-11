package io.jenkins.plugins.octanesuitegatebyembiti.models;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OctaneDefinedScope implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String project;
  private final String owner;

  public OctaneDefinedScope(String project, String owner) {
    this.project = Util.trimToEmpty(project);
    this.owner = defaultOwner(owner);
  }

  public static List<OctaneDefinedScope> parse(String configuredScope) {
    String configured = Util.trimToEmpty(configuredScope);
    if (configured.isEmpty()) {
      return List.of();
    }
    List<OctaneDefinedScope> scopes = new ArrayList<>();
    for (String rawEntry : configured.split(",")) {
      String entry = Util.trimToEmpty(rawEntry);
      if (entry.isEmpty()) {
        continue;
      }
      int separator = entry.lastIndexOf('-');
      if (separator < 0) {
        scopes.add(new OctaneDefinedScope(entry, "-"));
      } else {
        scopes.add(
            new OctaneDefinedScope(
                entry.substring(0, separator), titleCase(entry.substring(separator + 1))));
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

  private static String defaultOwner(String owner) {
    String value = Util.trimToEmpty(owner);
    return value.isEmpty() ? "-" : value;
  }

  private static String titleCase(String value) {
    String trimmed = Util.trimToEmpty(value);
    if (trimmed.isEmpty()) {
      return "-";
    }
    String[] words = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
    StringBuilder result = new StringBuilder(trimmed.length());
    for (String word : words) {
      if (result.length() > 0) {
        result.append(' ');
      }
      boolean capitalize = true;
      for (int index = 0; index < word.length(); index++) {
        char character = word.charAt(index);
        result.append(capitalize ? Character.toUpperCase(character) : character);
        capitalize = character == '\'';
      }
    }
    return result.toString();
  }
}
