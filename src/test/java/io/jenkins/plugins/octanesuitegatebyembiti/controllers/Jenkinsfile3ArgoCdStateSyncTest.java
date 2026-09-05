package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Jenkinsfile3ArgoCdStateSyncTest {
  private static final Path JENKINSFILE = Path.of("examples/Jenkinsfile3");
  private static final String BYPASS_MESSAGE =
      "INFO: ArgoCD GitOps state sync bypassed. Repository variables are undefined.";
  private static final String ACCESS_WARNING =
      "WARN: ArgoCD state sync failed. Invalid Git credentials or repository access denied.";

  @TempDir Path temporaryDirectory;

  @Test
  void undefinedRepositoryVariablesBypassAllGitAndCredentialSteps() throws Exception {
    PipelineHarness harness = new PipelineHarness(temporaryDirectory.resolve("workspace"));
    Script script = harness.parse();

    Object result =
        invokeSync(
            script,
            Map.of("repoUrl", "", "stateFilePath", "deploy/octane-gate.yaml"),
            "PASS",
            "SUCCESS");

    assertEquals(false, result);
    assertEquals(List.of(BYPASS_MESSAGE), harness.logs);
    assertEquals(0, harness.credentialCalls.get());
    assertEquals(0, harness.shellCalls.get());
  }

  @Test
  void validConfigurationCreatesCommitsAndPushesStructuredPassState() throws Exception {
    Path remote = createBareRemote(temporaryDirectory);
    PipelineHarness harness = new PipelineHarness(temporaryDirectory.resolve("workspace"));
    Script script = harness.parse();

    Object result =
        invokeSync(
            script,
            Map.of(
                "repoUrl", remote.toUri().toString(),
                "branch", "main",
                "stateFilePath", "environments/qa/octane-gate.yaml",
                "credentialId", "argocd-git"),
            "pass",
            "SUCCESS");

    assertEquals(true, result, harness.logs.toString());
    assertEquals(1, harness.credentialCalls.get(), harness.logs.toString());
    assertEquals("argocd-git", harness.boundCredentialId.get());
    assertEquals(1, harness.shellCalls.get());
    assertTrue(harness.logs.contains("INFO: ArgoCD GitOps state synchronized with status PASS."));
    assertTrue(
        harness.shellScripts.stream().noneMatch(scriptText -> scriptText.contains("secret")));

    Path inspection = temporaryDirectory.resolve("inspection");
    runCommand(
        temporaryDirectory,
        Map.of(),
        "git",
        "clone",
        "--quiet",
        "--branch",
        "main",
        remote.toString(),
        inspection.toString());
    assertEquals(
        "gate_status: \"PASS\"\n",
        Files.readString(
            inspection.resolve("environments/qa/octane-gate.yaml"), StandardCharsets.UTF_8));
    assertEquals(
        "Update Octane gate status to PASS",
        runCommand(inspection, Map.of(), "git", "log", "-1", "--pretty=%s").trim());
  }

  @Test
  void deniedGitAccessLogsWarningAndDoesNotFailPipeline() throws Exception {
    PipelineHarness harness = new PipelineHarness(temporaryDirectory.resolve("workspace"));
    harness.shellFailure = new IllegalStateException("git push denied");
    Script script = harness.parse();

    Object result =
        invokeSync(
            script,
            Map.of(
                "repoUrl", "https://git.example.test/argocd/state.git",
                "branch", "main",
                "stateFilePath", "octane/gate.yaml",
                "credentialId", "expired-token"),
            "FAIL",
            "FAILURE");

    assertEquals(false, result);
    assertEquals(1, harness.credentialCalls.get());
    assertEquals(1, harness.shellCalls.get());
    assertEquals(ACCESS_WARNING, harness.logs.get(harness.logs.size() - 1));
  }

  @Test
  void normalizesAllDocumentedStatusesAndRejectsUnsafeStatePaths() throws Exception {
    Script script = new GroovyShell().parse(Files.readString(JENKINSFILE));

    assertEquals("PASS", normalizedStatus(script, "passed", "FAILURE"));
    assertEquals("FAIL", normalizedStatus(script, "failure", "SUCCESS"));
    assertEquals("WARNING", normalizedStatus(script, "unstable", "SUCCESS"));
    assertEquals("CANCELLED", normalizedStatus(script, "aborted", "SUCCESS"));
    assertEquals("ERROR", normalizedStatus(script, "", "UNKNOWN"));
    assertEquals(
        "gate_status: \"WARNING\"\n",
        script.invokeMethod("argoCdStateDocument", new Object[] {"warning", "SUCCESS"}).toString());
    assertEquals(
        "config/octane.yaml",
        script.invokeMethod(
            "normalizedArgoCdStateFilePath", new Object[] {" config\\octane.yaml "}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            script.invokeMethod("normalizedArgoCdStateFilePath", new Object[] {"../outside.yaml"}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            script.invokeMethod(
                "normalizedArgoCdStateFilePath", new Object[] {"/absolute/state.yaml"}));
  }

  @Test
  void syncStageImmediatelyFollowsGateAndUsesCredentialBinding() throws IOException {
    String jenkinsfile = Files.readString(JENKINSFILE, StandardCharsets.UTF_8);
    int gateStage = jenkinsfile.indexOf("stage('Wait For Octane Suite')");
    int syncStage = jenkinsfile.indexOf("stage('Update ArgoCD GitOps State')");
    int emailStage = jenkinsfile.indexOf("stage('Email Octane Report')");

    assertTrue(gateStage >= 0);
    assertTrue(syncStage > gateStage);
    assertTrue(emailStage > syncStage);
    assertTrue(jenkinsfile.contains("'$class': 'UsernamePasswordMultiBinding'"));
    assertTrue(jenkinsfile.contains("GIT_ASKPASS"));
    assertTrue(jenkinsfile.contains("set +x"));
    assertFalse(jenkinsfile.contains("https://$ARGOCD_GIT_USERNAME:$ARGOCD_GIT_PASSWORD@"));
  }

  private static Object invokeSync(
      Script script, Map<String, String> configuration, String gateStatus, String buildResult) {
    return script.invokeMethod(
        "updateArgoCdGitOpsState", new Object[] {configuration, gateStatus, buildResult});
  }

  private static Object normalizedStatus(Script script, String status, String buildResult) {
    return script.invokeMethod("normalizedArgoCdGateStatus", new Object[] {status, buildResult});
  }

  private static Path createBareRemote(Path root) throws Exception {
    Path remote = root.resolve("remote.git");
    Path seed = root.resolve("seed");
    runCommand(
        root,
        Map.of(),
        "git",
        "init",
        "--quiet",
        "--bare",
        "--initial-branch=main",
        remote.toString());
    Files.createDirectories(seed);
    runCommand(seed, Map.of(), "git", "init", "--quiet", "--initial-branch=main");
    Files.writeString(seed.resolve("README.md"), "# ArgoCD state\n", StandardCharsets.UTF_8);
    runCommand(seed, Map.of(), "git", "add", "README.md");
    runCommand(
        seed,
        Map.of(),
        "git",
        "-c",
        "user.name=Test",
        "-c",
        "user.email=test@example.invalid",
        "commit",
        "--quiet",
        "-m",
        "Initialize state repository");
    runCommand(seed, Map.of(), "git", "remote", "add", "origin", remote.toString());
    runCommand(seed, Map.of(), "git", "push", "--quiet", "origin", "main");
    return remote;
  }

  private static String runCommand(
      Path directory, Map<String, String> environment, String... command) throws Exception {
    ProcessBuilder processBuilder =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
    processBuilder.environment().putAll(environment);
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException(
          String.join(" ", command) + " failed with exit code " + exitCode + ": " + output);
    }
    return output;
  }

  private static void recreateDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (Stream<Path> paths = Files.walk(directory)) {
        for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
          Files.deleteIfExists(path);
        }
      }
    }
    Files.createDirectories(directory);
  }

  private static final class PipelineHarness {
    private final Path workspace;
    private final Binding binding = new Binding();
    private final AtomicReference<Path> currentDirectory = new AtomicReference<>();
    private final Map<String, String> environment = new HashMap<>();
    private final List<String> logs = new ArrayList<>();
    private final List<String> shellScripts = new ArrayList<>();
    private final AtomicInteger credentialCalls = new AtomicInteger();
    private final AtomicInteger shellCalls = new AtomicInteger();
    private final AtomicReference<String> boundCredentialId = new AtomicReference<>();
    private RuntimeException shellFailure;

    private PipelineHarness(Path workspace) throws IOException {
      this.workspace = workspace;
      recreateDirectory(workspace);
      currentDirectory.set(workspace);
      bindPipelineSteps();
    }

    private Script parse() throws IOException {
      CompilerConfiguration configuration = new CompilerConfiguration();
      configuration.setScriptBaseClass(PipelineScript.class.getName());
      return new GroovyShell(binding, configuration)
          .parse(Files.readString(JENKINSFILE, StandardCharsets.UTF_8));
    }

    private void bindPipelineSteps() {
      binding.setVariable(
          "echo",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              logs.add(arguments[0].toString());
              return null;
            }
          });
      binding.setVariable(
          "usernamePassword",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              return arguments[0];
            }
          });
      binding.setVariable(
          "withCredentials",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              credentialCalls.incrementAndGet();
              @SuppressWarnings("unchecked")
              Map<String, Object> credential =
                  (Map<String, Object>) ((List<?>) arguments[0]).get(0);
              boundCredentialId.set(credential.get("credentialsId").toString());
              environment.put("ARGOCD_GIT_USERNAME", "git-user");
              environment.put("ARGOCD_GIT_PASSWORD", "secret");
              try {
                return ((Closure<?>) arguments[1]).call();
              } finally {
                environment.remove("ARGOCD_GIT_USERNAME");
                environment.remove("ARGOCD_GIT_PASSWORD");
              }
            }
          });
      binding.setVariable(
          "dir",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              Path previous = currentDirectory.get();
              Path requested = workspace.resolve(arguments[0].toString());
              try {
                Files.createDirectories(requested);
                currentDirectory.set(requested);
                return ((Closure<?>) arguments[1]).call();
              } catch (IOException exception) {
                throw new IllegalStateException(exception);
              } finally {
                currentDirectory.set(previous);
              }
            }
          });
      binding.setVariable(
          "deleteDir",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              try {
                recreateDirectory(currentDirectory.get());
                return null;
              } catch (IOException exception) {
                throw new IllegalStateException(exception);
              }
            }
          });
      binding.setVariable(
          "withEnv",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              List<?> entries = (List<?>) arguments[0];
              Map<String, String> previous = new LinkedHashMap<>();
              for (Object entryValue : entries) {
                String entry = entryValue.toString();
                int separator = entry.indexOf('=');
                String key = entry.substring(0, separator);
                previous.put(key, environment.put(key, entry.substring(separator + 1)));
              }
              try {
                return ((Closure<?>) arguments[1]).call();
              } finally {
                for (Map.Entry<String, String> entry : previous.entrySet()) {
                  if (entry.getValue() == null) {
                    environment.remove(entry.getKey());
                  } else {
                    environment.put(entry.getKey(), entry.getValue());
                  }
                }
              }
            }
          });
      binding.setVariable(
          "sh",
          new PipelineClosure() {
            @Override
            public Object doCall(Object... arguments) {
              shellCalls.incrementAndGet();
              @SuppressWarnings("unchecked")
              Map<String, Object> options = (Map<String, Object>) arguments[0];
              String shellScript = options.get("script").toString();
              shellScripts.add(shellScript);
              if (shellFailure != null) {
                throw shellFailure;
              }
              try {
                return runCommand(
                    currentDirectory.get(), environment, "/bin/sh", "-c", shellScript);
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            }
          });
    }
  }

  private abstract static class PipelineClosure extends Closure<Object> {
    private PipelineClosure() {
      super(null);
    }

    public abstract Object doCall(Object... arguments);
  }

  public abstract static class PipelineScript extends Script {
    public Object echo(Object message) {
      return callStep("echo", message);
    }

    public Object usernamePassword(Map<?, ?> arguments) {
      return callStep("usernamePassword", arguments);
    }

    public Object withCredentials(List<?> bindings, Closure<?> body) {
      return callStep("withCredentials", bindings, body);
    }

    public Object dir(String path, Closure<?> body) {
      return callStep("dir", path, body);
    }

    public Object deleteDir() {
      return callStep("deleteDir");
    }

    public Object withEnv(List<String> values, Closure<?> body) {
      return callStep("withEnv", values, body);
    }

    public Object sh(Map<?, ?> arguments) {
      return callStep("sh", arguments);
    }

    private Object callStep(String name, Object... arguments) {
      Object step = getBinding().getVariable(name);
      if (step instanceof Closure<?> closure) {
        return closure.call(arguments);
      }
      throw new IllegalStateException("Pipeline step is not bound: " + name);
    }
  }
}
