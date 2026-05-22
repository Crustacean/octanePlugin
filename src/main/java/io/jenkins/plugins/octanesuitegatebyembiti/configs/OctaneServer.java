package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class OctaneServer implements Describable<OctaneServer>, Serializable {
  private static final long serialVersionUID = 1L;

  private final String serverId;
  private final String baseUrl;
  private final String credentialsId;

  @DataBoundConstructor
  public OctaneServer(String serverId, String baseUrl, String credentialsId) {
    this.serverId = Util.trimToEmpty(serverId);
    this.baseUrl = Util.trimTrailingSlash(Util.trimToEmpty(baseUrl));
    this.credentialsId = Util.trimToEmpty(credentialsId);
  }

  public String getServerId() {
    return serverId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  @Extension
  @Symbol("octaneServer")
  public static class DescriptorImpl extends Descriptor<OctaneServer> {
    private static final Duration CONNECTIVITY_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public String getDisplayName() {
      return "ALM Octane server";
    }

    public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      return new StandardListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(
              ACL.SYSTEM2,
              Jenkins.get(),
              StandardUsernamePasswordCredentials.class,
              List.of(),
              credentials -> true)
          .includeCurrentValue(credentialsId);
    }

    public FormValidation doCheckCredentialsId(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      if (Util.isBlank(value)) {
        return FormValidation.error("Credentials are required.");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckServerId(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      if (Util.isBlank(value)) {
        return FormValidation.error("Server ID is required.");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckBaseUrl(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      if (Util.isBlank(value)) {
        return FormValidation.error("Base URL is required.");
      }
      try {
        URI uri = new URI(value);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
          return FormValidation.error("Base URL must start with http:// or https://.");
        }
        return FormValidation.ok();
      } catch (URISyntaxException e) {
        return FormValidation.error("Base URL is not a valid URI.");
      }
    }

    public FormValidation doTestBaseUrl(@QueryParameter String baseUrl) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      FormValidation validation = doCheckBaseUrl(baseUrl);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }

      String normalizedBaseUrl = Util.trimTrailingSlash(Util.trimToEmpty(baseUrl));
      HttpClient httpClient =
          HttpClient.newBuilder()
              .connectTimeout(CONNECTIVITY_TIMEOUT)
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(normalizedBaseUrl))
              .timeout(CONNECTIVITY_TIMEOUT)
              .GET()
              .build();
      try {
        HttpResponse<Void> response =
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        if (status >= 200 && status < 400) {
          return FormValidation.ok("OK: HTTP " + status + " from " + normalizedBaseUrl);
        }
        return FormValidation.error("Not OK: HTTP " + status + " from " + normalizedBaseUrl);
      } catch (IOException e) {
        return FormValidation.error(
            "Not OK: could not connect to " + normalizedBaseUrl + ". " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return FormValidation.error("Not OK: connectivity test was interrupted.");
      } catch (IllegalArgumentException e) {
        return FormValidation.error("Not OK: Base URL is not a valid URI.");
      }
    }
  }
}
