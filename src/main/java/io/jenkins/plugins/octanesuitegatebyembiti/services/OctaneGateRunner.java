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
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    return run(request, listener, new OctaneGateReportPublisher() {});
  }

  public GateResult run(
      GateRequest request, TaskListener listener, OctaneGateReportPublisher reportPublisher)
      throws IOException, InterruptedException {
    validateRequest(request);
    OctaneServer server = resolveServer(request.getServerId());
    String sharedSpaceId = chooseValue(request.getSharedSpaceId(), server.getSharedSpaceId());
    String workspaceId = chooseValue(request.getWorkspaceId(), server.getWorkspaceId());
    StandardUsernamePasswordCredentials credentials = resolveCredentials(server.getCredentialsId());

    CriteriaExpression criteria = CriteriaExpression.parse(request.getCriteria());
    StatusClassifier classifier = request.createStatusClassifier();
    Instant deadline = clock.instant().plus(Duration.ofMinutes(request.getTimeoutMinutes()));
    List<String> suiteRunIds = regressionSuiteRunIdsForCriteria(request);

    logListener.logWaiting(listener, request, suiteRunIds);
    reportPublisher.onWaiting(request, suiteRunIds);

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
        reportPublisher.onPoll(result, classifier);
        if (result.isPassed()) {
          result =
              refreshPassedResult(
                  client,
                  result,
                  request,
                  suiteRunIds,
                  sharedSpaceId,
                  workspaceId,
                  criteria,
                  classifier,
                  listener,
                  reportPublisher);
        }
        if (result.isPassed()) {
          logListener.logPassed(listener);
          reportPublisher.onFinal(
              OctaneGateReportState.PASSED, "ALM Octane suite gate passed.", result, classifier);
          return result;
        }
        if (result.isTerminal()) {
          String message = "ALM Octane suite gate failed.";
          reportPublisher.onFinal(failureState(request), message, result, classifier);
          throw new GateFailedException(message, result);
        }
        if (!clock.instant().isBefore(deadline)) {
          String message = "Timed out waiting for ALM Octane suite gate.";
          reportPublisher.onFinal(timeoutState(request), message, result, classifier);
          throw new GateFailedException(message, result);
        }
        Thread.sleep(Duration.ofSeconds(request.getPollIntervalSeconds()).toMillis());
      }
    }
  }

  GateResult refreshPassedResult(
      OctaneClient client,
      GateResult previousResult,
      GateRequest request,
      List<String> suiteRunIds,
      String sharedSpaceId,
      String workspaceId,
      CriteriaExpression criteria,
      StatusClassifier classifier,
      TaskListener listener,
      OctaneGateReportPublisher reportPublisher)
      throws InterruptedException {
    logListener.logFinalRefresh(listener);
    try {
      GateResult refreshedResult =
          poll(client, request, suiteRunIds, sharedSpaceId, workspaceId, criteria, classifier);
      logListener.logPollResult(listener, refreshedResult);
      reportPublisher.onPoll(refreshedResult, classifier);
      return refreshedResult;
    } catch (IOException e) {
      logListener.logFinalRefreshSkipped(listener, e);
      return previousResult;
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
    Map<String, List<RunRecord>> suiteRuns =
        fetchSuiteChildRuns(client, sharedSpaceId, workspaceId, suiteRunIds);
    List<RunRecord> childRuns = flattenAndDedupeRuns(suiteRuns);
    GateMetrics regressionMetrics = GateMetrics.fromRuns(childRuns, classifier);
    List<String> childRunIds = childRuns.stream().map(RunRecord::getId).toList();

    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    Map<String, GateScopeResult> scopedResults = new LinkedHashMap<>();
    for (OctaneGateScope scope : request.getScopes()) {
      GateScopeResult scopeResult =
          pollScope(client, sharedSpaceId, workspaceId, childRunIds, suiteRuns, classifier, scope);
      scopedMetrics.put(scope.getName(), scopeResult.getMetrics());
      scopedResults.put(scope.getName(), scopeResult);
    }

    MetricsContext metricsContext = new MetricsContext(regressionMetrics, scopedMetrics);
    boolean passed = criteria.evaluate(metricsContext);
    boolean terminal =
        regressionMetrics.isTerminal()
            && scopedMetrics.values().stream().allMatch(GateMetrics::isTerminal);
    return new GateResult(
        String.join(",", suiteRunIds),
        request.getCriteria(),
        passed,
        terminal,
        regressionMetrics,
        childRuns,
        suiteRuns,
        scopedResults,
        clock.instant());
  }

  private GateScopeResult pollScope(
      OctaneClient client,
      String sharedSpaceId,
      String workspaceId,
      List<String> childRunIds,
      Map<String, List<RunRecord>> regressionSuiteRuns,
      StatusClassifier classifier,
      OctaneGateScope scope)
      throws IOException, InterruptedException {
    if (scope.isSuiteRunScope()) {
      Map<String, List<RunRecord>> scopeSuiteRuns =
          fetchSuiteChildRuns(client, sharedSpaceId, workspaceId, scope.getSuiteRunIds());
      List<RunRecord> scopeRuns = flattenAndDedupeRuns(scopeSuiteRuns);
      GateMetrics metrics = GateMetrics.fromRuns(scopeRuns, classifier);
      return new GateScopeResult(
          scope.getName(),
          "",
          List.of(),
          scope.getSuiteRunId(),
          scope.getSuiteRunIds(),
          metrics,
          scopeRuns,
          scopeSuiteRuns);
    }

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
              + scopeQueryHint(scope)
              + e.getMessage());
    }
    GateMetrics metrics = GateMetrics.fromRuns(scopedRuns, classifier);
    return new GateScopeResult(
        scope.getName(),
        scope.getQuery(),
        scope.getReferencedIds(),
        "",
        List.of(),
        metrics,
        scopedRuns,
        groupScopedRunsBySuiteRun(regressionSuiteRuns, scopedRuns));
  }

  private OctaneGateReportState failureState(GateRequest request) {
    return request.isMarkUnstable() ? OctaneGateReportState.UNSTABLE : OctaneGateReportState.FAILED;
  }

  private OctaneGateReportState timeoutState(GateRequest request) {
    return request.isMarkUnstable()
        ? OctaneGateReportState.UNSTABLE
        : OctaneGateReportState.TIMED_OUT;
  }

  private Map<String, List<RunRecord>> fetchSuiteChildRuns(
      OctaneClient client, String sharedSpaceId, String workspaceId, List<String> suiteRunIds)
      throws IOException, InterruptedException {
    Map<String, List<RunRecord>> suiteRuns = new LinkedHashMap<>();
    for (String suiteRunId : suiteRunIds) {
      suiteRuns.put(suiteRunId, client.fetchSuiteChildRuns(sharedSpaceId, workspaceId, suiteRunId));
    }
    return suiteRuns;
  }

  private List<RunRecord> flattenAndDedupeRuns(Map<String, List<RunRecord>> suiteRuns) {
    Map<String, RunRecord> recordsById = new LinkedHashMap<>();
    for (List<RunRecord> records : suiteRuns.values()) {
      for (RunRecord record : records) {
        recordsById.putIfAbsent(record.getId(), record);
      }
    }
    return new ArrayList<>(recordsById.values());
  }

  private Map<String, List<RunRecord>> groupScopedRunsBySuiteRun(
      Map<String, List<RunRecord>> suiteRuns, List<RunRecord> scopedRuns) {
    Set<String> scopedRunIds =
        new LinkedHashSet<>(scopedRuns.stream().map(RunRecord::getId).toList());
    Map<String, List<RunRecord>> groupedRuns = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> entry : suiteRuns.entrySet()) {
      List<RunRecord> matchingRuns =
          entry.getValue().stream().filter(run -> scopedRunIds.contains(run.getId())).toList();
      if (!matchingRuns.isEmpty()) {
        groupedRuns.put(entry.getKey(), matchingRuns);
      }
    }
    return groupedRuns;
  }

  private String scopeQueryHint(OctaneGateScope scope) {
    String query = scope.getQuery().toLowerCase(Locale.ROOT);
    if (query.contains("product_area") && !query.contains("product_areas")) {
      return "Use product_areas for Octane test product-area filters. ";
    }
    return "";
  }

  static List<String> regressionSuiteRunIdsForCriteria(GateRequest request) {
    Set<String> criticalSuiteRunIds = criticalSuiteRunIds(request);
    return request.getSuiteRunIds().stream()
        .filter(suiteRunId -> !criticalSuiteRunIds.contains(suiteRunId))
        .toList();
  }

  private static Set<String> criticalSuiteRunIds(GateRequest request) {
    Set<String> criticalSuiteRunIds = new LinkedHashSet<>();
    for (OctaneGateScope scope : request.getScopes()) {
      if ("critical".equalsIgnoreCase(scope.getName()) && scope.isSuiteRunScope()) {
        criticalSuiteRunIds.addAll(scope.getSuiteRunIds());
      }
    }
    return criticalSuiteRunIds;
  }

  private void validateRequest(GateRequest request) throws AbortException {
    if (Util.isBlank(request.getServerId())) {
      throw new AbortException("Octane server ID is required.");
    }
    if (request.getSuiteRunIds().isEmpty()) {
      throw new AbortException("At least one Octane suite run ID is required.");
    }
    for (OctaneGateScope scope : request.getScopes()) {
      validateScope(scope);
    }
  }

  private void validateScope(OctaneGateScope scope) throws AbortException {
    if (Util.isBlank(scope.getName())) {
      throw new AbortException("Octane scope name is required.");
    }

    boolean hasSuiteRunIds = scope.isSuiteRunScope();
    boolean hasQuery = scope.isQueryScope();
    if (!hasSuiteRunIds && !hasQuery) {
      throw new AbortException(
          "Octane scope '" + scope.getName() + "' must define suite run ID(s) or an Octane query.");
    }
    if (hasSuiteRunIds && hasQuery) {
      throw new AbortException(
          "Octane scope '"
              + scope.getName()
              + "' must define either suite run ID(s) or an Octane query, not both.");
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
