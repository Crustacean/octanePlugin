package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OctaneServerUrlTest {
  @Test
  void normalizesConfiguredOctaneUrl() {
    assertEquals(
        "https://octane.example.test/api",
        OctaneServerUrl.normalize(" https://octane.example.test/api/ "));
  }

  @Test
  void rejectsEmbeddedCredentialsQueriesAndFragments() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OctaneServerUrl.normalize("https://user:secret@octane.example.test"));
    assertThrows(
        IllegalArgumentException.class,
        () -> OctaneServerUrl.normalize("https://octane.example.test?target=internal"));
    assertThrows(
        IllegalArgumentException.class,
        () -> OctaneServerUrl.normalize("https://octane.example.test/#fragment"));
  }

  @Test
  void rejectsNonHttpAndHostlessUrls() {
    assertThrows(
        IllegalArgumentException.class, () -> OctaneServerUrl.normalize("file:///etc/passwd"));
    assertThrows(IllegalArgumentException.class, () -> OctaneServerUrl.normalize("https:///api"));
  }
}
