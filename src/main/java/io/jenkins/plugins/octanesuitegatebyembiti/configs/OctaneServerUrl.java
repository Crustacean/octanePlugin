package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.net.URI;
import java.net.URISyntaxException;

/** Validates the administrator-owned ALM Octane network boundary. */
public final class OctaneServerUrl {
  private OctaneServerUrl() {}

  public static String normalize(String value) {
    String candidate = Util.trimTrailingSlash(Util.trimToEmpty(value));
    if (candidate.isEmpty()) {
      throw new IllegalArgumentException("Base URL is required.");
    }
    final URI uri;
    try {
      uri = new URI(candidate);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Base URL is not a valid URI.", e);
    }
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("Base URL must start with http:// or https://.");
    }
    if (Util.isBlank(uri.getHost())) {
      throw new IllegalArgumentException("Base URL must include a host.");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Base URL must not contain embedded credentials.");
    }
    if (uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("Base URL must not contain a query or fragment.");
    }
    return candidate;
  }
}
