package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Jenkinsfile3YamlConfigurationTest {
  private static final Path JENKINSFILE = Path.of("examples/Jenkinsfile3");
  private static final Path YAML_TEMPLATE = Path.of("examples/variables.yaml");
  private static final Path SPACES_MAPPING = Path.of("examples/octane_spaces_mapping.json");
  private static final Pattern YAML_KEY = Pattern.compile("(?m)^([A-Z][A-Z0-9_]*):");

  @Test
  void yamlTemplateAndPipelineLoaderStaySynchronized() throws IOException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    String yaml = Files.readString(YAML_TEMPLATE, StandardCharsets.UTF_8);
    Set<String> yamlKeys = yamlKeys(yaml);

    assertTrue(jenkinsfile.contains("stage('Load Configuration')"));
    assertTrue(jenkinsfile.contains("fileExists(paramsFile)"));
    assertTrue(jenkinsfile.contains("readYaml(file: paramsFile)"));
    assertTrue(jenkinsfile.contains("readJSON(file: spacesMappingFile)"));
    assertTrue(jenkinsfile.contains("space.sharedSpaceName?.toString()?.trim()?.toLowerCase()"));
    assertTrue(jenkinsfile.contains("workspace.workspaceName?.toString()?.trim()?.toLowerCase()"));
    assertTrue(jenkinsfile.contains("params.containsKey(key)"));
    assertTrue(jenkinsfile.contains("yamlConfiguration.containsKey(key)"));
    assertTrue(jenkinsfile.contains("selectedValue = env[key]"));
    assertTrue(jenkinsfile.contains("selectedValue instanceof Map"));
    assertTrue(jenkinsfile.contains("selectedValue instanceof Collection"));

    for (String key : yamlKeys) {
      assertTrue(jenkinsfile.contains("'" + key + "'"), () -> key + " is not allow-listed");
    }
  }

  @Test
  void jenkinsfileRemainsValidGroovyAfterConfigurationRefactor()
      throws IOException, CompilationFailedException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);

    new groovy.lang.GroovyShell().parse(jenkinsfile);
  }

  @Test
  void namesAndNumericIdsResolveCanonicalSpaceConfigurationWithoutSecrets() throws IOException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    String yaml = Files.readString(YAML_TEMPLATE, StandardCharsets.UTF_8);
    Set<String> yamlKeys = yamlKeys(yaml);
    Map<?, ?> mapping =
        new ObjectMapper()
            .readValue(Files.readString(SPACES_MAPPING, StandardCharsets.UTF_8), Map.class);
    Collection<?> sharedSpaces = (Collection<?>) mapping.get("shared_spaces");
    Map<?, ?> sharedSpace =
        findBySelector(sharedSpaces, "sharedSpaceName", "sharedSpaceId", "  DEFAULT SHARED SPACE ");
    Map<?, ?> sharedSpaceById =
        findBySelector(sharedSpaces, "sharedSpaceName", "sharedSpaceId", "1001");
    Map<?, ?> workspace =
        findBySelector(
            (Collection<?>) sharedSpace.get("workspaces"),
            "workspaceName",
            "workspaceId",
            "  abbybot mail service ");
    Map<?, ?> workspaceById =
        findBySelector(
            (Collection<?>) sharedSpace.get("workspaces"), "workspaceName", "workspaceId", "5002");

    assertFalse(yamlKeys.contains("OCTANE_SERVER_ID"));
    assertTrue(yamlKeys.contains("OCTANE_SPACES_MAPPING_FILE"));
    assertTrue(yamlKeys.contains("OCTANE_SHARED_SPACE_NAME"));
    assertTrue(yamlKeys.contains("OCTANE_WORKSPACE_NAME"));
    assertFalse(yamlKeys.contains("OCTANE_SHARED_SPACE_ID"));
    assertFalse(yamlKeys.contains("OCTANE_WORKSPACE_ID"));
    assertTrue(yamlKeys.contains("OCTANE_REGRESSION_SUITE_RUN_ID"));
    assertTrue(yamlKeys.contains("OCTANE_CRITICAL_SUITE_RUN_ID"));
    assertTrue(yamlKeys.contains("OCTANE_DEFINED_SCOPE"));

    assertEquals("1001", sharedSpace.get("sharedSpaceId"));
    assertEquals("5002", workspace.get("workspaceId"));
    assertEquals(sharedSpace, sharedSpaceById);
    assertEquals(workspace, workspaceById);

    assertTrue(jenkinsfile.contains("serverId: env.OCTANE_SERVER_ID"));
    assertTrue(jenkinsfile.contains("sharedSpaceSelectorIsId"));
    assertTrue(jenkinsfile.contains("space.sharedSpaceId?.toString()?.trim()"));
    assertTrue(jenkinsfile.contains("workspaceSelectorIsId"));
    assertTrue(jenkinsfile.contains("workspace.workspaceId?.toString()?.trim()"));
    assertTrue(jenkinsfile.contains("env.OCTANE_SERVER_ID = resolvedSharedSpaceName"));
    assertTrue(jenkinsfile.contains("env.OCTANE_SHARED_SPACE_ID = mappedSharedSpaceId"));
    assertTrue(jenkinsfile.contains("env.OCTANE_WORKSPACE_ID = mappedWorkspaceId"));
    assertTrue(
        jenkinsfile.contains(
            "OCTANE_SHARED_SPACE_NAME '${sharedSpaceSelector}' did not match a name or ID"));
    assertTrue(
        jenkinsfile.contains(
            "OCTANE_WORKSPACE_NAME '${workspaceSelector}' did not match a name or ID"));
    assertTrue(jenkinsfile.contains("suiteRunId: suiteRunSource"));
    assertTrue(jenkinsfile.contains("criteria: env.OCTANE_CRITERIA"));
    assertTrue(jenkinsfile.contains("pollIntervalSeconds: pollIntervalSeconds"));
    assertFalse(yaml.toLowerCase().contains("client_secret:"));
    assertFalse(yaml.toLowerCase().contains("password:"));
  }

  private static Map<?, ?> findBySelector(
      Collection<?> values, String nameField, String idField, String requestedValue) {
    String selector = requestedValue.trim();
    boolean selectorIsId = selector.matches("[0-9]{1,18}");
    String normalized = selector.toLowerCase(java.util.Locale.ROOT);
    return values.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(
            value ->
                selectorIsId
                    ? value.get(idField) != null
                        && value.get(idField).toString().trim().equals(selector)
                    : value.get(nameField) != null
                        && value
                            .get(nameField)
                            .toString()
                            .trim()
                            .toLowerCase(java.util.Locale.ROOT)
                            .equals(normalized))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> yamlKeys(String yaml) {
    return matches(yaml, YAML_KEY);
  }

  private static Set<String> matches(String value, Pattern pattern) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = pattern.matcher(value);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }
}
