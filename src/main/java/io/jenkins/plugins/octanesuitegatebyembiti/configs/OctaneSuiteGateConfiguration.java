package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import hudson.Extension;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
public class OctaneSuiteGateConfiguration extends GlobalConfiguration {
  private static final int VERSION_MAJOR = 1;
  private static final int VERSION_MEDIUM = 0;
  private static final int VERSION_MINOR = 0;

  private List<OctaneServer> servers = new ArrayList<>();

  public OctaneSuiteGateConfiguration() {
    load();
  }

  public static OctaneSuiteGateConfiguration get() {
    return GlobalConfiguration.all().get(OctaneSuiteGateConfiguration.class);
  }

  @Override
  public String getDisplayName() {
    return "Octane Suite Gate by Embiti";
  }

  public String getDisplayVersion() {
    return "v" + VERSION_MAJOR + "." + VERSION_MEDIUM + "." + VERSION_MINOR;
  }

  public List<OctaneServer> getServers() {
    return Collections.unmodifiableList(servers);
  }

  public void setServers(List<OctaneServer> servers) {
    this.servers = servers == null ? new ArrayList<>() : new ArrayList<>(servers);
  }

  public OctaneServer getServer(String serverId) {
    for (OctaneServer server : servers) {
      if (server.getServerId().equals(serverId)) {
        return server;
      }
    }
    return null;
  }

  @Override
  public boolean configure(StaplerRequest2 request, JSONObject formData) throws FormException {
    Object serversNode = formData.opt("servers");
    if (serversNode == null) {
      setServers(List.of());
    } else {
      setServers(request.bindJSONToList(OctaneServer.class, serversNode));
    }
    save();
    return true;
  }

  public ListBoxModel doFillServerIdItems() {
    ListBoxModel items = new ListBoxModel();
    items.add("", "");
    for (OctaneServer server : servers) {
      items.add(server.getServerId(), server.getServerId());
    }
    return items;
  }
}
