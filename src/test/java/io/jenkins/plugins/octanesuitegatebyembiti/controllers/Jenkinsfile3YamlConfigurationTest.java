package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  private static final Path YAML_TEMPLATE =
      Path.of("src/test/resources/jenkinsfile3-variables.yaml");
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
    assertTrue(jenkinsfile.contains("jobParameters.containsKey(key)"));
    assertTrue(jenkinsfile.contains("yamlConfiguration.containsKey(key)"));
    assertTrue(jenkinsfile.contains("return yamlConfiguration.get(key)"));
    assertTrue(jenkinsfile.contains("return jobParameters.get(key)"));
    assertTrue(jenkinsfile.contains("return configurationDefaults.get(key)"));
    assertTrue(jenkinsfile.contains("bootstrappedConfigurationJson(env)"));
    assertTrue(jenkinsfile.contains("OCTANE_BOOTSTRAP_CONFIGURATION_JSON"));
    assertTrue(jenkinsfile.contains("readJSON(text: transportedConfigurationJson)"));
    assertTrue(jenkinsfile.contains("validated variables.yaml from the dir2 bootstrap"));
    assertTrue(
        jenkinsfile.contains(
            "(automated) QE OCTANE GATE REPORT Job#${env.BUILD_NUMBER} | {{EAT_DATE}} | "
                + "${env.OCTANE_PROJECT_NAME}"));
    assertFalse(jenkinsfile.contains("return yamlConfiguration[key]"));
    assertFalse(jenkinsfile.contains("return jobParameters[key]"));
    assertFalse(jenkinsfile.contains("return environment[key]"));
    assertTrue(jenkinsfile.contains("env.setProperty(key,"));
    assertTrue(jenkinsfile.contains("env.getProperty(key)"));
    assertFalse(jenkinsfile.contains("env[key]"));
    assertFalse(jenkinsfile.contains("env[it]"));
    assertTrue(
        jenkinsfile.contains(
            "selectConfigurationValue(key, params, yamlConfiguration, configurationDefaults)"));
    assertTrue(jenkinsfile.contains("resolvePipelineSourcePath(paramsFile, env)"));
    assertTrue(
        jenkinsfile.contains("resolvePipelineSourcePath(env.OCTANE_SPACES_MAPPING_FILE, env)"));
    assertTrue(jenkinsfile.contains("selectedValue instanceof Map"));
    assertTrue(jenkinsfile.contains("selectedValue instanceof Collection"));
    String declarativeEnvironment =
        jenkinsfile.substring(
            jenkinsfile.indexOf("  environment {"), jenkinsfile.indexOf("  stages {"));
    assertTrue(declarativeEnvironment.contains("PARAMS_FILE = 'variables.yaml'"));
    assertFalse(declarativeEnvironment.contains("OCTANE_SHARED_SPACE_NAME"));
    assertFalse(declarativeEnvironment.contains("OCTANE_CRITICAL_SUITE_RUN_ID"));

    for (String key : yamlKeys) {
      assertTrue(jenkinsfile.contains("'" + key + "'"), () -> key + " is not allow-listed");
    }
  }

  @Test
  void yamlValuesOverrideJobParametersAndJenkinsfileDefaults()
      throws IOException, CompilationFailedException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    String yaml = Files.readString(YAML_TEMPLATE, StandardCharsets.UTF_8);
    groovy.lang.Script script = new groovy.lang.GroovyShell().parse(jenkinsfile);
    String key = "OCTANE_TIMEOUT_MINUTES";
    String jenkinsfileDefault = configurationDefault(script, key);
    String yamlValue = yamlScalar(yaml, key);

    assertEquals("120", jenkinsfileDefault);
    assertEquals("5", yamlValue);
    assertEquals(
        yamlValue,
        selectedValue(
            script, key, Map.of(), Map.of(key, yamlValue), Map.of(key, jenkinsfileDefault)));
    assertEquals(
        yamlValue,
        selectedValue(
            script,
            key,
            Map.of(key, "30"),
            Map.of(key, yamlValue),
            Map.of(key, jenkinsfileDefault)));
    assertEquals(
        "30",
        selectedValue(script, key, Map.of(key, "30"), Map.of(), Map.of(key, jenkinsfileDefault)));
    assertEquals(
        jenkinsfileDefault,
        selectedValue(script, key, Map.of(), Map.of(), Map.of(key, jenkinsfileDefault)));
  }

  @Test
  void jenkinsfileRemainsValidGroovyAfterConfigurationRefactor()
      throws IOException, CompilationFailedException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);

    new groovy.lang.GroovyShell().parse(jenkinsfile);
  }

  @Test
  void copiedConfigurationPathsResolveAgainstTargetRepositoryDirectory()
      throws IOException, CompilationFailedException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    groovy.lang.Script script = new groovy.lang.GroovyShell().parse(jenkinsfile);

    assertEquals(
        "/workspace/dir1/variables.yaml",
        resolvedPath(
            script, "variables.yaml", Map.of("OCTANE_PIPELINE_SOURCE_DIR", "/workspace/dir1")));
    assertEquals(
        "/workspace/dir1/examples/octane_spaces_mapping.json",
        resolvedPath(
            script,
            "examples/octane_spaces_mapping.json",
            Map.of("OCTANE_PIPELINE_SOURCE_DIR", "/workspace/dir1")));
    assertEquals(
        "/external/variables.yaml",
        resolvedPath(
            script,
            "/external/variables.yaml",
            Map.of("OCTANE_PIPELINE_SOURCE_DIR", "/workspace/dir1")));
    assertEquals(
        "C:\\config\\variables.yaml",
        resolvedPath(
            script,
            "C:\\config\\variables.yaml",
            Map.of("OCTANE_PIPELINE_SOURCE_DIR", "/workspace/dir1")));
    assertEquals("variables.yaml", resolvedPath(script, "variables.yaml", Map.of()));
  }

  @Test
  void validatedBootstrapConfigurationEnvelopeSurvivesDeclarativeEnvironmentDefaults()
      throws IOException, CompilationFailedException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    groovy.lang.Script script = new groovy.lang.GroovyShell().parse(jenkinsfile);
    groovy.util.Expando transportedEnvironment = new groovy.util.Expando();
    String transportedJson =
        """
        {"OCTANE_SHARED_SPACE_NAME":"Default Shared Space",\
        "OCTANE_WORKSPACE_NAME":"Abbybot Mail Service",\
        "OCTANE_CRITICAL_SUITE_RUN_ID":"76645"}
        """
            .strip();
    transportedEnvironment.setProperty("OCTANE_BOOTSTRAP_CONFIGURATION_JSON", transportedJson);

    Object result =
        script.invokeMethod("bootstrappedConfigurationJson", new Object[] {transportedEnvironment});

    assertEquals(transportedJson, result);
    Map<?, ?> configuration = new ObjectMapper().readValue(result.toString(), Map.class);
    assertEquals("Default Shared Space", configuration.get("OCTANE_SHARED_SPACE_NAME"));
    assertEquals("Abbybot Mail Service", configuration.get("OCTANE_WORKSPACE_NAME"));
    assertEquals("76645", configuration.get("OCTANE_CRITICAL_SUITE_RUN_ID"));
    assertNull(
        script.invokeMethod(
            "bootstrappedConfigurationJson", new Object[] {new groovy.util.Expando()}));
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
    assertTrue(
        jenkinsfile.contains(
            "'OCTANE_SPACES_MAPPING_FILE': 'examples/octane_spaces_mapping.json'"));
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
    assertTrue(yamlKeys.contains("OCTANE_CRITICAL_GRAPHS_TITLE"));
    assertTrue(yamlKeys.contains("OCTANE_REGRESSION_GRAPHS_TITLE"));
    assertTrue(yamlKeys.contains("OCTANE_INTERVAL_EMAIL_IS_IMPORTANT"));
    assertTrue(yamlKeys.contains("OCTANE_FINAL_EMAIL_IS_IMPORTANT"));
    assertTrue(
        jenkinsfile.contains("important: env.OCTANE_INTERVAL_EMAIL_IS_IMPORTANT.toBoolean()"));
    assertTrue(jenkinsfile.contains("important: env.OCTANE_FINAL_EMAIL_IS_IMPORTANT.toBoolean()"));
    assertFalse(yaml.toLowerCase().contains("client_secret:"));
    assertFalse(yaml.toLowerCase().contains("password:"));
  }

  @Test
  void emailImportanceFlagsAcceptSupportedBooleanVariantsAndRejectInvalidValues()
      throws IOException, CompilationFailedException {
    groovy.lang.Script script =
        new groovy.lang.GroovyShell().parse(Files.readString(JENKINSFILE, StandardCharsets.UTF_8));

    for (Object enabled : new Object[] {true, 1, "TRUE", "True", " true "}) {
      assertEquals(
          true,
          script.invokeMethod(
              "emailImportanceValue",
              new Object[] {"OCTANE_INTERVAL_EMAIL_IS_IMPORTANT", enabled}));
    }
    for (Object disabled :
        new Object[] {false, 0, "FALSE", "False", "undefined", "null", null, ""}) {
      assertEquals(
          false,
          script.invokeMethod(
              "emailImportanceValue", new Object[] {"OCTANE_FINAL_EMAIL_IS_IMPORTANT", disabled}));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            script.invokeMethod(
                "emailImportanceValue",
                new Object[] {"OCTANE_FINAL_EMAIL_IS_IMPORTANT", "urgent"}));
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

  private static Object selectedValue(
      groovy.lang.Script script,
      String key,
      Map<String, String> parameters,
      Map<String, String> yaml,
      Map<String, String> environment) {
    return script.invokeMethod(
        "selectConfigurationValue", new Object[] {key, parameters, yaml, environment});
  }

  private static Object resolvedPath(
      groovy.lang.Script script, String path, Map<String, String> environment) {
    groovy.util.Expando environmentGlobal = new groovy.util.Expando();
    environment.forEach(environmentGlobal::setProperty);
    return script.invokeMethod("resolvePipelineSourcePath", new Object[] {path, environmentGlobal});
  }

  private static String configurationDefault(groovy.lang.Script script, String key) {
    Object result = script.invokeMethod("pipelineConfigurationDefaults", new Object[0]);
    assertTrue(result instanceof Map, "pipelineConfigurationDefaults must return a map");
    Object value = ((Map<?, ?>) result).get(key);
    assertTrue(value != null, () -> key + " has no Jenkinsfile default");
    return value.toString();
  }

  private static String yamlScalar(String yaml, String key) {
    Pattern pattern =
        Pattern.compile(
            "(?m)^" + Pattern.quote(key) + ":\\s*[\\\"']?([^\\\"'\\r\\n#]+?)[\\\"']?\\s*$");
    Matcher matcher = pattern.matcher(yaml);
    assertTrue(matcher.find(), () -> key + " has no YAML value");
    return matcher.group(1).trim();
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
