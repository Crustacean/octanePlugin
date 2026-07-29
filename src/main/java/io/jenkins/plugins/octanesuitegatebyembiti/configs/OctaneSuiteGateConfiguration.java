package io.jenkins.plugins.octanesuitegatebyembiti.configs;

import hudson.Extension;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
public class OctaneSuiteGateConfiguration extends GlobalConfiguration {
  private List<OctaneServer> servers = new ArrayList<>();

  public OctaneSuiteGateConfiguration() {
    load();
  }

  public static OctaneSuiteGateConfiguration get() {
    return all().get(OctaneSuiteGateConfiguration.class);
  }

  @Override
  public String getDisplayName() {
    return "Octane Suite Gate by Embiti";
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
    Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    Object serversNode = formData.opt("servers");
    List<OctaneServer> configuredServers;
    if (serversNode == null) {
      configuredServers = List.of();
    } else {
      configuredServers = request.bindJSONToList(OctaneServer.class, serversNode);
    }
    validateServers(configuredServers);
    setServers(configuredServers);
    save();
    return true;
  }

  private void validateServers(List<OctaneServer> configuredServers) throws FormException {
    Set<String> serverIds = new LinkedHashSet<>();
    for (OctaneServer server : configuredServers) {
      validateServerIdentity(server, serverIds);
      validateServerCredentials(server);
      validateServerUrl(server);
    }
  }

  private void validateServerIdentity(OctaneServer server, Set<String> serverIds)
      throws FormException {
    if (server == null || server.getServerId().isBlank()) {
      throw new FormException("Server ID is required.", "servers");
    }
    if (!serverIds.add(server.getServerId())) {
      throw new FormException("Server IDs must be unique.", "servers");
    }
  }

  private void validateServerCredentials(OctaneServer server) throws FormException {
    if (server.getCredentialsId().isBlank()) {
      throw new FormException("Credentials are required.", "servers");
    }
  }

  private void validateServerUrl(OctaneServer server) throws FormException {
    try {
      OctaneServerUrl.normalize(server.getBaseUrl());
    } catch (IllegalArgumentException e) {
      throw new FormException(e.getMessage(), "servers");
    }
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
