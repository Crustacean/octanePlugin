package io.jenkins.plugins.octanesuitegatebyembiti;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.security.ACL;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

class OctaneGateRunner {
  private final Clock clock;

  OctaneGateRunner() {
    this(Clock.systemUTC());
  }

  OctaneGateRunner(Clock clock) {
    this.clock = clock;
  }

  GateResult run(GateRequest request, TaskListener listener)
      throws IOException, InterruptedException {
    validateRequest(request);
    OctaneServer server = resolveServer(request.getServerId());
    String sharedSpaceId = chooseValue(request.getSharedSpaceId(), server.getSharedSpaceId());
    String workspaceId = chooseValue(request.getWorkspaceId(), server.getWorkspaceId());
    StandardUsernamePasswordCredentials credentials = resolveCredentials(server.getCredentialsId());

    CriteriaExpression criteria = CriteriaExpression.parse(request.getCriteria());
    StatusClassifier classifier = request.createStatusClassifier();
    Instant deadline = clock.instant().plus(Duration.ofMinutes(request.getTimeoutMinutes()));

    listener
        .getLogger()
        .println("Waiting for ALM Octane suite run " + request.getSuiteRunId() + ".");

    try (OctaneClient client =
        new OctaneClient(
            server.getBaseUrl(),
            credentials.getUsername(),
            credentials.getPassword().getPlainText())) {
      client.authenticate();
      while (true) {
        GateResult result =
            poll(client, request, sharedSpaceId, workspaceId, criteria, classifier);
        logPollResult(listener, result);
        if (result.isPassed()) {
          listener.getLogger().println("ALM Octane suite gate passed.");
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
      String sharedSpaceId,
      String workspaceId,
      CriteriaExpression criteria,
      StatusClassifier classifier)
      throws IOException, InterruptedException {
    List<RunRecord> childRuns =
        client.fetchSuiteChildRuns(sharedSpaceId, workspaceId, request.getSuiteRunId());
    GateMetrics globalMetrics = GateMetrics.fromRuns(childRuns, classifier);
    List<String> childRunIds = childRuns.stream().map(RunRecord::getId).toList();

    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    for (OctaneGateScope scope : request.getScopes()) {
      List<RunRecord> scopedRuns =
          client.fetchScopedRuns(sharedSpaceId, workspaceId, childRunIds, scope.getQuery());
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

  private void logPollResult(TaskListener listener, GateResult result) {
    GateMetrics metrics = result.getMetrics();
    listener
        .getLogger()
        .printf(
            "Octane suite run %s: execution %.2f%%, pass %.2f%%, total %d, executed %d,"
                + " passed %d, failed %d, skipped %d, running %d.%n",
            result.getSuiteRunId(),
            metrics.getExecutionRate(),
            metrics.getPassRate(),
            metrics.getTotal(),
            metrics.getExecuted(),
            metrics.getPassed(),
            metrics.getFailed(),
            metrics.getSkipped(),
            metrics.getRunning());
  }

  private void validateRequest(GateRequest request) throws AbortException {
    if (Util.isBlank(request.getServerId())) {
      throw new AbortException("Octane server ID is required.");
    }
    if (Util.isBlank(request.getSuiteRunId())) {
      throw new AbortException("Octane suite run ID is required.");
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
