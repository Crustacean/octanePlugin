package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final Path CLUSTER_MAPPING = Path.of("examples/clusters.json");
  private static final Pattern YAML_KEY = Pattern.compile("(?m)^([A-Z][A-Z0-9_]*):");

  @Test
  void yamlTemplateAndPipelineLoaderStaySynchronized() throws IOException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    String yaml = Files.readString(YAML_TEMPLATE, StandardCharsets.UTF_8);
    Set<String> yamlKeys = yamlKeys(yaml);

    assertTrue(jenkinsfile.contains("stage('Load Configuration')"));
    assertTrue(jenkinsfile.contains("fileExists(paramsFile)"));
    assertTrue(jenkinsfile.contains("readYaml(file: paramsFile)"));
    assertTrue(jenkinsfile.contains("readJSON(file: clustersFile)"));
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
  void serverIdResolvesWorkspaceFromClusterMappingWithoutSecrets() throws IOException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    String yaml = Files.readString(YAML_TEMPLATE, StandardCharsets.UTF_8);
    Set<String> yamlKeys = yamlKeys(yaml);
    Map<?, ?> clusters =
        new ObjectMapper()
            .readValue(Files.readString(CLUSTER_MAPPING, StandardCharsets.UTF_8), Map.class);
    Map<?, ?> production = (Map<?, ?>) clusters.get("octane-prod");

    assertTrue(yamlKeys.contains("OCTANE_SERVER_ID"));
    assertFalse(yamlKeys.contains("OCTANE_SHARED_SPACE_ID"));
    assertFalse(yamlKeys.contains("OCTANE_WORKSPACE_ID"));
    assertTrue(yamlKeys.contains("OCTANE_REGRESSION_SUITE_RUN_ID"));
    assertTrue(yamlKeys.contains("OCTANE_CRITICAL_SUITE_RUN_ID"));
    assertTrue(yamlKeys.contains("OCTANE_DEFINED_SCOPE"));

    assertEquals("1001", production.get("sharedSpaceId"));
    assertEquals("2002", production.get("workspaceId"));

    assertTrue(jenkinsfile.contains("serverId: env.OCTANE_SERVER_ID"));
    assertTrue(jenkinsfile.contains("loadedClusters[serverId]"));
    assertTrue(jenkinsfile.contains("env.OCTANE_SHARED_SPACE_ID = mappedSharedSpaceId"));
    assertTrue(jenkinsfile.contains("env.OCTANE_WORKSPACE_ID = mappedWorkspaceId"));
    assertTrue(jenkinsfile.contains("suiteRunId: suiteRunSource"));
    assertTrue(jenkinsfile.contains("criteria: env.OCTANE_CRITERIA"));
    assertTrue(jenkinsfile.contains("pollIntervalSeconds: pollIntervalSeconds"));
    assertFalse(yaml.toLowerCase().contains("client_secret:"));
    assertFalse(yaml.toLowerCase().contains("password:"));
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
