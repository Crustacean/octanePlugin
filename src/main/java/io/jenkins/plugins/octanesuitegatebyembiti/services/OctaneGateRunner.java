package io.jenkins.plugins.octanesuitegatebyembiti.services;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.security.ACL;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneServer;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneSuiteGateConfiguration;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import io.jenkins.plugins.octanesuitegatebyembiti.repositories.OctaneClient;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

public class OctaneGateRunner {
  private final Clock clock;
  private final OctaneGateLogListener logListener;

  public OctaneGateRunner() {
    this(Clock.systemUTC(), new OctaneGateLogListener());
  }

  OctaneGateRunner(Clock clock, OctaneGateLogListener logListener) {
    this.clock = clock;
    this.logListener = logListener;
  }

  public GateResult run(GateRequest request, TaskListener listener)
      throws IOException, InterruptedException {
    validateRequest(request);
    OctaneServer server = resolveServer(request.getServerId());
    String sharedSpaceId = chooseValue(request.getSharedSpaceId(), server.getSharedSpaceId());
    String workspaceId = chooseValue(request.getWorkspaceId(), server.getWorkspaceId());
    StandardUsernamePasswordCredentials credentials = resolveCredentials(server.getCredentialsId());

    CriteriaExpression criteria = CriteriaExpression.parse(request.getCriteria());
    StatusClassifier classifier = request.createStatusClassifier();
    Instant deadline = clock.instant().plus(Duration.ofMinutes(request.getTimeoutMinutes()));
    List<String> suiteRunIds = request.getSuiteRunIds();

    logListener.logWaiting(listener, suiteRunIds);

    try (OctaneClient client =
        new OctaneClient(
            server.getBaseUrl(),
            credentials.getUsername(),
            credentials.getPassword().getPlainText())) {
      client.authenticate();
      while (true) {
        GateResult result =
            poll(client, request, suiteRunIds, sharedSpaceId, workspaceId, criteria, classifier);
        logListener.logPollResult(listener, result);
        if (result.isPassed()) {
          logListener.logPassed(listener);
          return result;
        }
        if (result.isTerminal()) {
          throw new GateFailedException("ALM Octane suite gate failed.", result);
        }
        if (!clock.instant().isBefore(deadline)) {
          throw new GateFailedException("Timed out waiting for ALM Octane suite gate.", result);
        }
        Thread.sleep(Duration.ofSeconds(request.getPollIntervalSeconds()).toMillis());
      }
    }
  }

  private GateResult poll(
      OctaneClient client,
      GateRequest request,
      List<String> suiteRunIds,
      String sharedSpaceId,
      String workspaceId,
      CriteriaExpression criteria,
      StatusClassifier classifier)
      throws IOException, InterruptedException {
    List<RunRecord> childRuns =
        fetchSuiteChildRuns(client, sharedSpaceId, workspaceId, suiteRunIds);
    GateMetrics globalMetrics = GateMetrics.fromRuns(childRuns, classifier);
    List<String> childRunIds = childRuns.stream().map(RunRecord::getId).toList();

    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    for (OctaneGateScope scope : request.getScopes()) {
      List<RunRecord> scopedRuns;
      try {
        scopedRuns =
            client.fetchScopedRuns(sharedSpaceId, workspaceId, childRunIds, scope.getQuery());
      } catch (IOException e) {
        throw new AbortException(
            "ALM Octane scope '"
                + scope.getName()
                + "' query failed: "
                + scope.getQuery()
                + ". "
                + e.getMessage());
      }
      scopedMetrics.put(scope.getName(), GateMetrics.fromRuns(scopedRuns, classifier));
    }

    MetricsContext metricsContext = new MetricsContext(globalMetrics, scopedMetrics);
    boolean passed = criteria.evaluate(metricsContext);
    boolean terminal =
        globalMetrics.isTerminal()
            && scopedMetrics.values().stream().allMatch(GateMetrics::isTerminal);
    return new GateResult(
        request.getSuiteRunId(),
        request.getCriteria(),
        passed,
        terminal,
        globalMetrics,
        scopedMetrics,
        clock.instant());
  }

  private List<RunRecord> fetchSuiteChildRuns(
      OctaneClient client, String sharedSpaceId, String workspaceId, List<String> suiteRunIds)
      throws IOException, InterruptedException {
    Map<String, RunRecord> recordsById = new LinkedHashMap<>();
    for (String suiteRunId : suiteRunIds) {
      for (RunRecord record : client.fetchSuiteChildRuns(sharedSpaceId, workspaceId, suiteRunId)) {
        recordsById.putIfAbsent(record.getId(), record);
      }
    }
    return new ArrayList<>(recordsById.values());
  }

  private void validateRequest(GateRequest request) throws AbortException {
    if (Util.isBlank(request.getServerId())) {
      throw new AbortException("Octane server ID is required.");
    }
    if (request.getSuiteRunIds().isEmpty()) {
      throw new AbortException("At least one Octane suite run ID is required.");
    }
  }

  private OctaneServer resolveServer(String serverId) throws AbortException {
    OctaneSuiteGateConfiguration configuration = OctaneSuiteGateConfiguration.get();
    OctaneServer server = configuration == null ? null : configuration.getServer(serverId);
    if (server == null) {
      throw new AbortException("No ALM Octane server is configured with ID: " + serverId);
    }
    return server;
  }

  private StandardUsernamePasswordCredentials resolveCredentials(String credentialsId)
      throws AbortException {
    if (Util.isBlank(credentialsId)) {
      throw new AbortException("ALM Octane credentials are required.");
    }

    StandardUsernamePasswordCredentials credentials =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentialsInItemGroup(
                StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of()),
            CredentialsMatchers.withId(credentialsId));
    if (credentials == null) {
      throw new AbortException("ALM Octane credentials were not found: " + credentialsId);
    }
    return credentials;
  }

  private String chooseValue(String override, String defaultValue) throws AbortException {
    String chosen = Util.isBlank(override) ? defaultValue : override;
    if (Util.isBlank(chosen)) {
      throw new AbortException("Shared space ID and workspace ID must be configured.");
    }
    return chosen;
  }
}
