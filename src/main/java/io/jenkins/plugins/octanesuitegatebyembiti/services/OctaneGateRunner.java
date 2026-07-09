package io.jenkins.plugins.octanesuitegatebyembiti.services;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.security.ACL;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneServer;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneSuiteGateConfiguration;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateLogListener;
import io.jenkins.plugins.octanesuitegatebyembiti.listeners.OctaneGateReportPublisher;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.DefectCriteriaMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateScopeResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.MetricsContext;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectLedger;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateScope;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneRiskHeatMap;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneRiskHeatMapBuilder;
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
import java.util.Objects;
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
    String sharedSpaceId = requiredWorkspaceValue("Shared space ID", request.getSharedSpaceId());
    String workspaceId = requiredWorkspaceValue("Workspace ID", request.getWorkspaceId());
    StandardUsernamePasswordCredentials credentials = resolveCredentials(server.getCredentialsId());

    CriteriaExpression criteria = CriteriaExpression.parse(request.getCriteria());
    StatusClassifier classifier = request.createStatusClassifier();
    Instant primaryDeadline = clock.instant().plus(Duration.ofMinutes(request.getTimeoutMinutes()));
    Instant extendedDeadline =
        primaryDeadline.plus(Duration.ofMinutes(request.getTimeoutMinutesExtended()));
    boolean extendedTimeoutConfigured = request.getTimeoutMinutesExtended() > 0;
    boolean extendedTimeActive = false;
    List<String> suiteRunIds = regressionSuiteRunIdsForCriteria(request);
    OctaneDefectLedger defectLedger = new OctaneDefectLedger();

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
            poll(
                client,
                request,
                suiteRunIds,
                sharedSpaceId,
                workspaceId,
                criteria,
                classifier,
                listener,
                defectLedger);
        logListener.logPollResult(listener, result);
        publishPollResult(reportPublisher, result, classifier, extendedTimeActive);
        if (!extendedTimeoutConfigured && result.isPassed()) {
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
                  reportPublisher,
                  defectLedger);
        }
        if (!extendedTimeoutConfigured && result.isPassed()) {
          return passGate(listener, reportPublisher, result, classifier);
        }
        if (!extendedTimeoutConfigured && result.isTerminal()) {
          String message = "ALM Octane suite gate failed.";
          reportPublisher.onFinal(failureState(request), message, result, classifier);
          throw new GateFailedException(message, result);
        }
        Instant now = clock.instant();
        if (!extendedTimeActive && !now.isBefore(primaryDeadline)) {
          if (!extendedTimeoutConfigured) {
            String message = "Timed out waiting for ALM Octane suite gate.";
            reportPublisher.onFinal(timeoutState(request), message, result, classifier);
            throw new GateFailedException(message, result);
          }
          extendedTimeActive = true;
          logListener.logExtendedTimeStarted(listener, request.getTimeoutMinutesExtended());
          reportPublisher.onExtendedTime(result, classifier);
        }
        if (extendedTimeActive
            && (reportPublisher.isManualExitRequested() || !now.isBefore(extendedDeadline))) {
          return finishExtendedGate(
              request,
              listener,
              reportPublisher,
              result,
              classifier,
              reportPublisher.isManualExitRequested());
        }
        Duration waitDuration =
            waitDuration(request, extendedTimeActive ? extendedDeadline : primaryDeadline);
        if (waitDuration.isZero() || waitDuration.isNegative()) {
          continue;
        }
        reportPublisher.awaitNextPollOrManualExit(waitDuration);
      }
    }
  }

  private void publishPollResult(
      OctaneGateReportPublisher reportPublisher,
      GateResult result,
      StatusClassifier classifier,
      boolean extendedTimeActive) {
    if (extendedTimeActive) {
      reportPublisher.onExtendedTime(result, classifier);
    } else {
      reportPublisher.onPoll(result, classifier);
    }
  }

  private GateResult passGate(
      TaskListener listener,
      OctaneGateReportPublisher reportPublisher,
      GateResult result,
      StatusClassifier classifier) {
    logListener.logPassed(listener);
    reportPublisher.onFinal(
        OctaneGateReportState.PASSED, "ALM Octane suite gate passed.", result, classifier);
    return result;
  }

  private GateResult finishExtendedGate(
      GateRequest request,
      TaskListener listener,
      OctaneGateReportPublisher reportPublisher,
      GateResult result,
      StatusClassifier classifier,
      boolean manualExitRequested)
      throws GateFailedException {
    if (manualExitRequested) {
      logListener.logManualExitRequested(listener);
    } else {
      logListener.logExtendedTimeExpired(listener);
    }
    if (result.isPassed()) {
      return passGate(listener, reportPublisher, result, classifier);
    }

    String message =
        manualExitRequested
            ? "Exit Octane and Continue requested before criteria passed."
            : "Extended timeout elapsed before the ALM Octane suite gate passed.";
    OctaneGateReportState state =
        manualExitRequested ? failureState(request) : timeoutState(request);
    reportPublisher.onFinal(state, message, result, classifier);
    throw new GateFailedException(message, result);
  }

  private Duration waitDuration(GateRequest request, Instant deadline) {
    Duration pollInterval = Duration.ofSeconds(request.getPollIntervalSeconds());
    Duration remaining = Duration.between(clock.instant(), deadline);
    if (remaining.isZero() || remaining.isNegative()) {
      return Duration.ZERO;
    }
    return remaining.compareTo(pollInterval) < 0 ? remaining : pollInterval;
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
    return refreshPassedResult(
        client,
        previousResult,
        request,
        suiteRunIds,
        sharedSpaceId,
        workspaceId,
        criteria,
        classifier,
        listener,
        reportPublisher,
        new OctaneDefectLedger());
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
      OctaneGateReportPublisher reportPublisher,
      OctaneDefectLedger defectLedger)
      throws InterruptedException {
    logListener.logFinalRefresh(listener);
    try {
      GateResult refreshedResult =
          poll(
              client,
              request,
              suiteRunIds,
              sharedSpaceId,
              workspaceId,
              criteria,
              classifier,
              listener,
              defectLedger);
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
      StatusClassifier classifier,
      TaskListener listener,
      OctaneDefectLedger defectLedger)
      throws IOException, InterruptedException {
    Map<String, List<RunRecord>> suiteRuns =
        fetchSuiteChildRuns(client, sharedSpaceId, workspaceId, suiteRunIds);
    List<RunRecord> childRuns = flattenAndDedupeRuns(suiteRuns);
    GateMetrics regressionMetrics = GateMetrics.fromRuns(childRuns, classifier);
    List<String> childRunIds = runIds(childRuns);

    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    Map<String, GateScopeResult> scopedResults = new LinkedHashMap<>();
    for (OctaneGateScope scope : request.getScopes()) {
      GateScopeResult scopeResult =
          pollScope(client, sharedSpaceId, workspaceId, childRunIds, suiteRuns, classifier, scope);
      scopedMetrics.put(scope.getName(), scopeResult.getMetrics());
      scopedResults.put(scope.getName(), scopeResult);
    }

    boolean defectCriteriaRequired = criteria.usesMetricNamespace("defects");
    DefectPollResult defectPollResult =
        pollDefects(
            client,
            request,
            sharedSpaceId,
            workspaceId,
            heatMapSuiteRuns(suiteRuns, scopedResults),
            classifier,
            listener,
            defectLedger,
            defectCriteriaRequired);
    DefectCriteriaMetrics defectMetrics =
        new DefectCriteriaMetrics(defectPollResult.severitySummary, request.getDefectGroups());
    MetricsContext metricsContext =
        new MetricsContext(regressionMetrics, scopedMetrics, defectMetrics);
    CriteriaEvaluation criteriaEvaluation = criteria.evaluateDetailed(metricsContext);
    boolean passed = criteriaEvaluation.isPassed();
    boolean terminal = regressionMetrics.isTerminal();
    for (GateMetrics scopedMetric : scopedMetrics.values()) {
      if (!Objects.requireNonNull(scopedMetric).isTerminal()) {
        terminal = false;
        break;
      }
    }
    return new GateResult(
        String.join(",", suiteRunIds),
        request.getCriteria(),
        passed,
        terminal,
        regressionMetrics,
        childRuns,
        suiteRuns,
        scopedResults,
        defectPollResult.reportHeatMap,
        defectMetrics,
        defectPollResult.defects,
        criteriaEvaluation,
        clock.instant());
  }

  private Map<String, List<RunRecord>> heatMapSuiteRuns(
      Map<String, List<RunRecord>> regressionSuiteRuns,
      Map<String, GateScopeResult> scopedResults) {
    Map<String, List<RunRecord>> values = new LinkedHashMap<>(regressionSuiteRuns);
    for (GateScopeResult scopeResult : scopedResults.values()) {
      if (!scopeResult.isSuiteRunScope()) {
        continue;
      }
      for (Map.Entry<String, List<RunRecord>> entry : scopeResult.getSuiteRuns().entrySet()) {
        values.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }
    return values;
  }

  private DefectPollResult pollDefects(
      OctaneClient client,
      GateRequest request,
      String sharedSpaceId,
      String workspaceId,
      Map<String, List<RunRecord>> suiteRuns,
      StatusClassifier classifier,
      TaskListener listener,
      OctaneDefectLedger defectLedger,
      boolean defectCriteriaRequired)
      throws IOException, InterruptedException {
    if (!request.isRiskHeatMap() && !defectCriteriaRequired) {
      return DefectPollResult.empty();
    }
    try {
      List<DefectRecord> defects =
          client.fetchLinkedDefects(
              sharedSpaceId,
              workspaceId,
              suiteRuns,
              request.getRiskHeatMapDefectQuery(),
              request.getRiskHeatMapMaxDefects());
      defectLedger.merge(defects);
      refreshKnownDefects(
          client,
          sharedSpaceId,
          workspaceId,
          request.getRiskHeatMapMaxDefects(),
          defectLedger,
          defectCriteriaRequired);
      OctaneRiskHeatMap heatMap =
          new OctaneRiskHeatMapBuilder()
              .build(workspaceId, suiteRuns, defectLedger.getDefects(), classifier);
      if (request.isRiskHeatMap()) {
        logRiskHeatMapSummary(listener, heatMap);
      }
      return new DefectPollResult(
          request.isRiskHeatMap() ? heatMap : OctaneRiskHeatMap.disabled(),
          heatMap.getDefectSeveritySummary(),
          defectLedger.getDefects());
    } catch (IOException e) {
      if (defectCriteriaRequired) {
        listener
            .getLogger()
            .println(
                "Octane defect criteria data unavailable: " + Util.trimToEmpty(e.getMessage()));
        throw new AbortException(
            "Defect criteria could not be evaluated because current ALM Octane defect data is unavailable.");
      }
      listener
          .getLogger()
          .println("Octane risk heat map unavailable: " + Util.trimToEmpty(e.getMessage()));
      return new DefectPollResult(
          OctaneRiskHeatMap.unavailable("Risk heat map unavailable: " + e.getMessage()),
          OctaneDefectSeveritySummary.empty(),
          List.of());
    }
  }

  private void refreshKnownDefects(
      OctaneClient client,
      String sharedSpaceId,
      String workspaceId,
      int maxDefects,
      OctaneDefectLedger defectLedger,
      boolean defectCriteriaRequired)
      throws IOException, InterruptedException {
    if (defectLedger.isEmpty()) {
      return;
    }
    try {
      defectLedger.merge(
          client.fetchDefectsByIds(
              sharedSpaceId, workspaceId, defectLedger.getDefectIds(), maxDefects));
    } catch (IOException e) {
      if (defectCriteriaRequired) {
        throw e;
      }
      // Keep the last known defect states rather than making the whole report unavailable.
    }
  }

  private void logRiskHeatMapSummary(TaskListener listener, OctaneRiskHeatMap heatMap) {
    listener
        .getLogger()
        .println(
            "Octane risk heat map: risk "
                + heatMap.getRiskScore()
                + ", defects fetched "
                + heatMap.getFetchedDefectCount()
                + ", linked "
                + heatMap.getLinkedDefectCount()
                + ", unlinked "
                + heatMap.getUnlinkedOpenDefectCount()
                + ", ignored closed "
                + heatMap.getIgnoredClosedDefectCount()
                + ".");
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
    Set<String> scopedRunIds = new LinkedHashSet<>(runIds(scopedRuns));
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

  private List<String> runIds(List<RunRecord> runs) {
    List<String> ids = new ArrayList<>(runs.size());
    for (RunRecord run : runs) {
      ids.add(Objects.requireNonNull(run).getId());
    }
    return List.copyOf(ids);
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
    validateDefectGroups(request.getDefectGroups());
  }

  private void validateDefectGroups(List<OctaneDefectGroup> defectGroups) throws AbortException {
    Set<String> names = new LinkedHashSet<>();
    for (OctaneDefectGroup group : defectGroups) {
      if (group == null) {
        throw new AbortException("Defect group configuration cannot be empty.");
      }
      String validationError = group.getValidationError();
      if (!validationError.isEmpty()) {
        throw new AbortException(validationError);
      }
      String normalizedName = OctaneDefectGroup.normalizeName(group.getName());
      if (!names.add(normalizedName)) {
        throw new AbortException(
            "Defect group names must be unique regardless of letter case: " + group.getName());
      }
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

  private String requiredWorkspaceValue(String label, String value) throws AbortException {
    String chosen = Util.trimToEmpty(value);
    if (chosen.isEmpty()) {
      throw new AbortException(
          "Shared space ID and workspace ID must be provided in the Jenkins job configuration.");
    }
    try {
      Long.parseLong(chosen);
    } catch (NumberFormatException e) {
      throw new AbortException(label + " must be numeric.");
    }
    return chosen;
  }

  private static class DefectPollResult {
    private final OctaneRiskHeatMap reportHeatMap;
    private final OctaneDefectSeveritySummary severitySummary;
    private final List<DefectRecord> defects;

    private DefectPollResult(
        OctaneRiskHeatMap reportHeatMap,
        OctaneDefectSeveritySummary severitySummary,
        List<DefectRecord> defects) {
      this.reportHeatMap = reportHeatMap;
      this.severitySummary = severitySummary;
      this.defects = defects == null ? List.of() : List.copyOf(defects);
    }

    private static DefectPollResult empty() {
      return new DefectPollResult(
          OctaneRiskHeatMap.disabled(), OctaneDefectSeveritySummary.empty(), List.of());
    }
  }
}
