package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.AbortException;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.security.OctaneTestHttpsServer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OctaneFolderCredentialsTest {
  @Test
  void folderCredentialsAreUsedForBothUrlAndApiAndDoNotLeakToSiblings(JenkinsRule jenkins)
      throws Exception {
    var server = OctaneTestHttpsServer.create();
    AtomicReference<String> body = new AtomicReference<>();
    server.createContext(
        "/authentication/sign_in",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    server.start();
    try {
      String url = "https://127.0.0.1:" + server.getAddress().getPort();
      Folder folder = jenkins.createProject(Folder.class, "team");
      Credentials api =
          new UsernamePasswordCredentialsImpl(
              CredentialsScope.GLOBAL, "team-api", "", "team-client", "team-secret");
      folder.addProperty(
          new FolderCredentialsProperty(
              List.of(
                  api,
                  new StringCredentialsImpl(
                      CredentialsScope.GLOBAL, "team-url", "", Secret.fromString(url)))));
      var run = jenkins.buildAndAssertSuccess(folder.createProject(FreeStyleProject.class, "gate"));
      GateRequest request = new GateRequest("space", "1196");
      request.setBaseUrl("team-url");
      request.setCredentialsId("team-api");
      try (var client = new OctaneGateRunner(run).createClient(request)) {
        client.authenticate();
      }
      assertTrue(body.get().contains("\"client_id\":\"team-client\""));
      assertEquals("team-url", request.getBaseUrl());

      // Give the sibling a URL only: its API lookup must not reach into the other folder.
      Folder sibling = jenkins.createProject(Folder.class, "other-team");
      sibling.addProperty(
          new FolderCredentialsProperty(
              List.of(
                  new StringCredentialsImpl(
                      CredentialsScope.GLOBAL, "team-url", "", Secret.fromString(url)))));
      var otherRun =
          jenkins.buildAndAssertSuccess(sibling.createProject(FreeStyleProject.class, "gate"));
      assertThrows(
          AbortException.class, () -> new OctaneGateRunner(otherRun).createClient(request));

      // Preserve the established shared-ID precedence, but resolve it in the run's folder.
      folder.getProperties().remove(FolderCredentialsProperty.class);
      folder.addProperty(
          new FolderCredentialsProperty(
              List.of(
                  api,
                  new UsernamePasswordCredentialsImpl(
                      CredentialsScope.GLOBAL, "octane-api-client", "", "folder-shared", "secret"),
                  new StringCredentialsImpl(
                      CredentialsScope.GLOBAL, "team-url", "", Secret.fromString(url)))));
      SystemCredentialsProvider.getInstance()
          .getCredentials()
          .add(
              new UsernamePasswordCredentialsImpl(
                  CredentialsScope.GLOBAL, "octane-api-client", "", "root-shared", "secret"));
      try (var client = new OctaneGateRunner(run).createClient(request)) {
        client.authenticate();
      }
      assertTrue(body.get().contains("\"client_id\":\"folder-shared\""));
    } finally {
      server.stop(0);
    }
  }
}
