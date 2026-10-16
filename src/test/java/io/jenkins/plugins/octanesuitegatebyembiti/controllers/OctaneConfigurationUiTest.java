package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;

@WithJenkins
class OctaneConfigurationUiTest {
  @Test
  void credentialSelectorsAreTypedScopedAndPermissionChecked(JenkinsRule jenkins) throws Exception {
    Folder folder = jenkins.createProject(Folder.class, "team");
    folder.addProperty(
        new FolderCredentialsProperty(
            List.of(
                new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL, "team-api", "", "client", "secret"),
                new StringCredentialsImpl(
                    CredentialsScope.GLOBAL,
                    "team-url",
                    "",
                    Secret.fromString("https://secret.invalid")))));
    var job = folder.createProject(FreeStyleProject.class, "gate");
    var sibling = jenkins.createFreeStyleProject("sibling");
    var descriptor = jenkins.jenkins.getDescriptorByType(OctaneSuiteGateStep.DescriptorImpl.class);
    assertTrue(
        descriptor.doFillCredentialsIdItems(job, "").stream()
            .anyMatch(o -> o.value.equals("team-api")));
    assertFalse(
        descriptor.doFillCredentialsIdItems(job, "").stream()
            .anyMatch(o -> o.value.equals("team-url")));
    assertTrue(
        descriptor.doFillBaseUrlItems(job, "").stream().anyMatch(o -> o.value.equals("team-url")));
    assertFalse(
        descriptor.doFillBaseUrlItems(job, "").stream().anyMatch(o -> o.value.equals("team-api")));
    assertEquals(1, descriptor.doFillCredentialsIdItems(sibling, "").size());
    assertEquals(1, descriptor.doFillBaseUrlItems(sibling, "").size());
    assertTrue(
        descriptor.doFillCredentialsIdItems(job, "deleted-id").stream()
            .anyMatch(o -> o.value.equals("deleted-id")));
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckBaseUrl("team-url").kind);
    for (String value :
        List.of("https://octane.example.com", "http://octane.example.com", "", "//host")) {
      assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl(value).kind);
    }

    jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
    jenkins.jenkins.setAuthorizationStrategy(
        new MockAuthorizationStrategy()
            .grant(Jenkins.READ, Item.READ)
            .everywhere()
            .to("reader", "editor")
            .grant(Item.CONFIGURE)
            .onItems(job)
            .to("editor"));
    try (var context = ACL.as2(hudson.model.User.getById("reader", true).impersonate2())) {
      assertThrows(AccessDeniedException.class, () -> descriptor.doFillCredentialsIdItems(job, ""));
      assertThrows(AccessDeniedException.class, () -> descriptor.doFillBaseUrlItems(job, ""));
      assertThrows(
          AccessDeniedException.class, () -> descriptor.doFillCredentialsIdItems(null, ""));
      assertThrows(AccessDeniedException.class, () -> descriptor.doFillBaseUrlItems(null, ""));
    }
    try (var context = ACL.as2(hudson.model.User.getById("editor", true).impersonate2())) {
      assertTrue(
          descriptor.doFillCredentialsIdItems(job, "").stream()
              .anyMatch(o -> o.value.equals("team-api")));
      assertThrows(
          AccessDeniedException.class, () -> descriptor.doFillCredentialsIdItems(sibling, ""));
    }
  }

  @Test
  void repeatableEntriesCanBeReorderedDeletedAndSavedEmpty(JenkinsRule jenkins) throws Exception {
    var job = jenkins.createFreeStyleProject();
    var builder = new OctaneSuiteGateBuilder("1196");
    var first = new OctaneGateScope("critical");
    first.setSuiteRunId("1200");
    var second = new OctaneGateScope("smoke");
    second.setSuiteRunId("1201");
    builder.setScopes(List.of(first, second));
    var major = new OctaneDefectGroup("major");
    major.setTypes("Critical,High");
    var minor = new OctaneDefectGroup("minor");
    minor.setTypes("Low");
    builder.setDefectGroups(List.of(major, minor));
    job.getBuildersList().add(builder);
    try (var client = jenkins.createWebClient()) {
      HtmlPage page = client.getPage(job, "configure");
      for (String field : List.of("scopes", "defectGroups")) {
        assertEquals(
            2,
            page.querySelectorAll(".repeated-chunk[name='" + field + "'] .repeatable-delete")
                .size());
        assertEquals(
            2, page.querySelectorAll(".repeated-chunk[name='" + field + "'] .dd-handle").size());
        page.executeJavaScript(
            "var chunks=document.querySelectorAll(\".repeated-chunk[name='"
                + field
                + "']\");chunks[0].parentNode.insertBefore(chunks[1],chunks[0]);");
      }
      jenkins.submit(page.getFormByName("config"));
      var saved = job.getBuildersList().get(OctaneSuiteGateBuilder.class);
      assertEquals(
          List.of("smoke", "critical"), saved.getScopes().stream().map(s -> s.getName()).toList());
      assertEquals(
          List.of("minor", "major"),
          saved.getDefectGroups().stream().map(g -> g.getName()).toList());
      page = client.getPage(job, "configure");
      for (String field : List.of("scopes", "defectGroups")) {
        for (var button :
            page.querySelectorAll(".repeated-chunk[name='" + field + "'] .repeatable-delete")) {
          ((HtmlButton) button).click();
        }
      }
      jenkins.submit(page.getFormByName("config"));
      saved = job.getBuildersList().get(OctaneSuiteGateBuilder.class);
      assertTrue(saved.getScopes().isEmpty());
      assertTrue(saved.getDefectGroups().isEmpty());
    }
    // The same controls must be present for Pipeline's separate configuration fragment.
    try (var resource =
        OctaneSuiteGateStep.class.getResourceAsStream("OctaneSuiteGateStep/config.jelly")) {
      assertNotNull(resource);
      String jelly = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(jelly.contains("xmlns:c=\"/lib/credentials\""));
      assertEquals(2, jelly.split("<c:select", -1).length - 1);
      assertEquals(2, jelly.split("<f:repeatableDeleteButton", -1).length - 1);
      assertTrue(jelly.contains("header=\"Scope\""));
      assertTrue(jelly.contains("header=\"Defect group\""));
      assertFalse(jelly.contains("placeholder=\"https://"));
    }
  }
}
