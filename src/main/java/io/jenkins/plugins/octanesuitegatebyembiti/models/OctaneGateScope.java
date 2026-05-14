package io.jenkins.plugins.octanesuitegatebyembiti.models;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class OctaneGateScope extends AbstractDescribableImpl<OctaneGateScope>
    implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final String query;

  @DataBoundConstructor
  public OctaneGateScope(String name, String query) {
    this.name = Util.trimToEmpty(name);
    this.query = Util.trimToEmpty(query);
  }

  public String getName() {
    return name;
  }

  public String getQuery() {
    return query;
  }

  @Extension
  @Symbol("octaneGateScope")
  public static class DescriptorImpl extends Descriptor<OctaneGateScope> {
    @Override
    public String getDisplayName() {
      return "Octane gate scope";
    }

    public FormValidation doCheckName(@QueryParameter String value) {
      if (Util.isBlank(value)) {
        return FormValidation.error("Scope name is required.");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckQuery(@QueryParameter String value) {
      if (Util.isBlank(value)) {
        return FormValidation.error("Octane query is required.");
      }
      return FormValidation.ok();
    }
  }
}
