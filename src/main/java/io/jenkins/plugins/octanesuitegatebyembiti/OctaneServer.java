package io.jenkins.plugins.octanesuitegatebyembiti;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class OctaneServer extends AbstractDescribableImpl<OctaneServer> implements Serializable {
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
  }
}
