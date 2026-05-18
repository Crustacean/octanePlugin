package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.octanesuitegatebyembiti.repositories.OctaneClient;
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
  private final String sharedSpaceId;
  private final String workspaceId;
  private final String credentialsId;

  @DataBoundConstructor
  public OctaneServer(
      String serverId,
      String baseUrl,
      String sharedSpaceId,
      String workspaceId,
      String credentialsId) {
    this.serverId = Util.trimToEmpty(serverId);
    this.baseUrl = Util.trimTrailingSlash(Util.trimToEmpty(baseUrl));
    this.sharedSpaceId = Util.trimToEmpty(sharedSpaceId);
    this.workspaceId = Util.trimToEmpty(workspaceId);
    this.credentialsId = Util.trimToEmpty(credentialsId);
  }

  public String getServerId() {
    return serverId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getSharedSpaceId() {
    return sharedSpaceId;
  }

  public String getWorkspaceId() {
    return workspaceId;
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

    public FormValidation doTestOctaneWorkspace(
        @QueryParameter("baseUrl") String baseUrl,
        @QueryParameter("sharedSpaceId") String sharedSpaceId,
        @QueryParameter("workspaceId") String workspaceId,
        @QueryParameter("credentialsId") String credentialsId) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      FormValidation validation = doCheckBaseUrl(baseUrl);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }
      validation = doCheckSharedSpaceId(sharedSpaceId);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }
      validation = doCheckWorkspaceId(workspaceId);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }
      validation = doCheckCredentialsId(credentialsId);
      if (validation.kind != FormValidation.Kind.OK) {
        return validation;
      }

      String normalizedBaseUrl = Util.trimTrailingSlash(Util.trimToEmpty(baseUrl));
      String pathLabel =
          workspaceTestPath(normalizedBaseUrl, sharedSpaceId, workspaceId, credentialsId);
      StandardUsernamePasswordCredentials credentials = resolveCredentials(credentialsId);
      if (credentials == null) {
        return FormValidation.error("Not OK: credentials were not found for " + pathLabel);
      }

      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECTIVITY_TIMEOUT).build();
      OctaneClient client =
          new OctaneClient(
              httpClient,
              normalizedBaseUrl,
              credentials.getUsername(),
              credentials.getPassword().getPlainText());
      try {
        client.authenticate();
        int status = client.testWorkspaceAccess(sharedSpaceId, workspaceId);
        if (status >= 200 && status < 400) {
          return FormValidation.ok("OK: HTTP " + status + " from " + pathLabel);
        }
        return FormValidation.error("Not OK: HTTP " + status + " from " + pathLabel);
      } catch (IOException e) {
        return FormValidation.error("Not OK: could not test " + pathLabel + ". " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return FormValidation.error("Not OK: workspace connectivity test was interrupted.");
      } finally {
        closeQuietly(client);
      }
    }

    public FormValidation doCheckSharedSpaceId(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      return checkRequiredNumber("Shared space ID", value);
    }

    public FormValidation doCheckWorkspaceId(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      return checkRequiredNumber("Workspace ID", value);
    }

    private FormValidation checkRequiredNumber(String label, String value) {
      if (Util.isBlank(value)) {
        return FormValidation.error(label + " is required.");
      }
      try {
        Long.parseLong(value);
        return FormValidation.ok();
      } catch (NumberFormatException e) {
        return FormValidation.error(label + " must be numeric.");
      }
    }

    private StandardUsernamePasswordCredentials resolveCredentials(String credentialsId) {
      return CredentialsMatchers.firstOrNull(
          CredentialsProvider.lookupCredentialsInItemGroup(
              StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of()),
          CredentialsMatchers.withId(credentialsId));
    }

    private String workspaceTestPath(
        String normalizedBaseUrl, String sharedSpaceId, String workspaceId, String credentialsId) {
      return normalizedBaseUrl
          + "/api/shared_spaces/"
          + Util.trimToEmpty(sharedSpaceId)
          + "/workspaces/"
          + Util.trimToEmpty(workspaceId)
          + "/runs?fields=id&limit=1 using credentials "
          + Util.trimToEmpty(credentialsId)
          + " {TEST}";
    }

    private void closeQuietly(OctaneClient client) {
      try {
        client.close();
      } catch (IOException e) {
        // Keep the validation result focused on the API check, not best-effort sign-out.
      }
    }
  }
}
