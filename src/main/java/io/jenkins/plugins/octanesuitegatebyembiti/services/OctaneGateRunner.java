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
import io.jenkins.plugins.octanesuitegatebyembiti.models.SuiteRunSelector;
import io.jenkins.plugins.octanesuitegatebyembiti.repositories.OctaneClient;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.io.Serializable;
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
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

public class OctaneGateRunner {
  private static final Pattern OCTANE_NUMERIC_ID = Pattern.compile("[0-9]{1,18}");
  private static final int MAX_SCOPES = 100;
  private static final int MAX_DEFECT_GROUPS = 100;
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
    PollingState state = new PollingState(clock.instant());
    try (PollingSession session = openSession(request, listener, reportPublisher, state)) {
      while (true) {
        PollOutcome outcome = session.pollOnce();
        if (outcome.isComplete()) {
          return outcome.getResult();
        }
        reportPublisher.awaitNextPollOrManualExit(outcome.getNextDelay());
      }
    }
  }

  public PollingSession openSession(
      GateRequest request,
      TaskListener listener,
      OctaneGateReportPublisher reportPublisher,
      PollingState state)
      throws IOException, InterruptedException {
    return new PollingSession(request, listener, reportPublisher, state);
  }

  public final class PollingSession implements AutoCloseable {
    private final GateRequest request;
    private final TaskListener listener;
    private final OctaneGateReportPublisher reportPublisher;
    private final PollingState state;
    private final String sharedSpaceId;
    private final String workspaceId;
    private final CriteriaExpression criteria;
    private final StatusClassifier classifier;
    private final Instant primaryDeadline;
    private final Instant extendedDeadline;
    private final boolean extendedTimeoutConfigured;
    private final SuiteRunPool regressionSuitePool;
    private final Map<String, SuiteRunPool> suiteScopePools;
    private final boolean regressionSelectionEnabled;
    private final OctaneClient client;

    private PollingSession(
        GateRequest request,
        TaskListener listener,
        OctaneGateReportPublisher reportPublisher,
        PollingState state)
        throws IOException, InterruptedException {
      validateRequest(request);
      this.request = request;
      this.listener = listener;
      this.reportPublisher = reportPublisher;
      this.state = state == null ? new PollingState(clock.instant()) : state;
      OctaneServer server = resolveServer(request.getServerId());
      sharedSpaceId = requiredWorkspaceValue("Shared space ID", request.getSharedSpaceId());
      workspaceId = requiredWorkspaceValue("Workspace ID", request.getWorkspaceId());
      StandardUsernamePasswordCredentials credentials =
          resolveCredentials(server.getCredentialsId());
      criteria = CriteriaExpression.parse(request.getCriteria());
      classifier = request.createStatusClassifier();
      primaryDeadline =
          this.state.getStartedAt().plus(Duration.ofMinutes(request.getTimeoutMinutes()));
      extendedDeadline =
          primaryDeadline.plus(Duration.ofMinutes(request.getTimeoutMinutesExtended()));
      extendedTimeoutConfigured = request.getTimeoutMinutesExtended() > 0;
      regressionSuitePool = new SuiteRunPool("Regressions", request.getSuiteRunSelector());
      suiteScopePools = new LinkedHashMap<>();
      for (OctaneGateScope scope : request.getScopes()) {
        if (scope.isSuiteRunScope()) {
          suiteScopePools.put(
              scope.getName(),
              new SuiteRunPool(displayScopeName(scope.getName()), scope.getSuiteRunSelector()));
        }
      }
      regressionSelectionEnabled = regressionSelectionEnabled(request);
      if (!this.state.isWaitingPublished()) {
        logListener.logLookupContext(listener, sharedSpaceId, workspaceId);
      }
      client =
          new OctaneClient(
              server.getBaseUrl(),
              credentials.getUsername(),
              credentials.getPassword().getPlainText());
      boolean sessionReady = false;
      try {
        client.authenticate();
        preflightSuitePools();
        sessionReady = true;
      } finally {
        if (!sessionReady) {
          try {
            client.close();
          } catch (IOException ignored) {
            // Preserve the authentication or preflight failure that prevented the session.
          }
        }
      }
      List<String> suiteRunIds = currentRegressionSuiteRunIds();
      if (!this.state.isWaitingPublished()) {
        if (!regressionSelectionEnabled) {
          logListener.logRegressionEvaluationSkipped(listener);
        }
        logListener.logWaiting(listener, request, suiteRunIds, currentScopeSuiteRunIds());
        reportPublisher.onWaiting(request, suiteRunIds);
        this.state.setWaitingPublished(true);
      }
    }

    public PollOutcome pollOnce() throws IOException, InterruptedException {
      logManualExitFinalizingIfNeeded();
      CurrentSuiteRuns currentSuiteRuns = refreshSuitePools();
      GateResult result =
          poll(
              client,
              request,
              currentSuiteRuns.regressionSuiteRuns,
              currentSuiteRuns.scopeSuiteRuns,
              regressionSelectionEnabled,
              currentSuiteRuns.awaitingSuiteDiscovery,
              sharedSpaceId,
              workspaceId,
              criteria,
              classifier,
              listener,
              state.getDefectLedger());
      logListener.logPollResult(listener, result);
      publishPollResult(reportPublisher, result, classifier, state.isExtendedTimeActive());
      if (!extendedTimeoutConfigured && result.isPassed()) {
        result = refreshCurrentPassedResult(result);
      }
      if (!extendedTimeoutConfigured && result.isPassed()) {
        return PollOutcome.complete(passGate(listener, reportPublisher, result, classifier));
      }
      if (!extendedTimeoutConfigured && result.isTerminal()) {
        String message = "ALM Octane suite gate failed.";
        reportPublisher.onFinal(failureState(request), message, result, classifier);
        throw new GateFailedException(message, result);
      }
      Instant now = clock.instant();
      if (!state.isExtendedTimeActive() && !now.isBefore(primaryDeadline)) {
        if (!extendedTimeoutConfigured) {
          String message = "Timed out waiting for ALM Octane suite gate.";
          reportPublisher.onFinal(timeoutState(request), message, result, classifier);
          throw new GateFailedException(message, result);
        }
        state.setExtendedTimeActive(true);
        logListener.logExtendedTimeStarted(listener, request.getTimeoutMinutesExtended());
        reportPublisher.onExtendedTime(result, classifier);
      }
      if (state.isExtendedTimeActive()
          && (reportPublisher.isManualExitRequested() || !now.isBefore(extendedDeadline))) {
        logManualExitFinalizingIfNeeded();
        return PollOutcome.complete(
            finishExtendedGate(
                request,
                listener,
                reportPublisher,
                result,
                classifier,
                reportPublisher.isManualExitRequested()));
      }
      Duration delay =
          waitDuration(request, state.isExtendedTimeActive() ? extendedDeadline : primaryDeadline);
      return PollOutcome.continueAfter(delay);
    }

    private void logManualExitFinalizingIfNeeded() {
      if (state.isExtendedTimeActive()
          && reportPublisher.isManualExitRequested()
          && state.markManualExitFinalizingLogged()) {
        logListener.logManualExitRequested(listener);
      }
    }

    private GateResult refreshCurrentPassedResult(GateResult previousResult)
        throws InterruptedException {
      logListener.logFinalRefresh(listener);
      try {
        CurrentSuiteRuns currentSuiteRuns = refreshSuitePools();
        GateResult refreshedResult =
            poll(
                client,
                request,
                currentSuiteRuns.regressionSuiteRuns,
                currentSuiteRuns.scopeSuiteRuns,
                regressionSelectionEnabled,
                currentSuiteRuns.awaitingSuiteDiscovery,
                sharedSpaceId,
                workspaceId,
                criteria,
                classifier,
                listener,
                state.getDefectLedger());
        logListener.logPollResult(listener, refreshedResult);
        reportPublisher.onPoll(refreshedResult, classifier);
        return refreshedResult;
      } catch (IOException e) {
        logListener.logFinalRefreshSkipped(listener, e);
        return previousResult;
      }
    }

    private void preflightSuitePools() throws IOException, InterruptedException {
      if (regressionSelectionEnabled) {
        regressionSuitePool.preflight();
      }
      for (SuiteRunPool pool : suiteScopePools.values()) {
        pool.preflight();
      }
    }

    private CurrentSuiteRuns refreshSuitePools() throws IOException, InterruptedException {
      Map<String, List<RunRecord>> regressionSuiteRuns =
          regressionSelectionEnabled ? regressionSuitePool.refresh() : Map.of();
      Map<String, Map<String, List<RunRecord>>> scopeSuiteRuns = new LinkedHashMap<>();
      for (Map.Entry<String, SuiteRunPool> entry : suiteScopePools.entrySet()) {
        scopeSuiteRuns.put(entry.getKey(), entry.getValue().refresh());
      }

      Set<String> criticalIds = currentCriticalSuiteRunIds(scopeSuiteRuns);
      Map<String, List<RunRecord>> effectiveRegressionRuns = new LinkedHashMap<>();
      if (regressionSelectionEnabled) {
        for (Map.Entry<String, List<RunRecord>> entry : regressionSuiteRuns.entrySet()) {
          if (!criticalIds.contains(entry.getKey())) {
            effectiveRegressionRuns.put(entry.getKey(), entry.getValue());
          }
        }
      }

      boolean awaiting =
          regressionSelectionEnabled
              && regressionSuitePool.isConfigured()
              && effectiveRegressionRuns.isEmpty();
      for (SuiteRunPool pool : suiteScopePools.values()) {
        if (pool.isConfigured() && pool.isEmpty()) {
          awaiting = true;
          break;
        }
      }
      return new CurrentSuiteRuns(effectiveRegressionRuns, scopeSuiteRuns, awaiting);
    }

    private List<String> currentRegressionSuiteRunIds() {
      Set<String> criticalIds = currentCriticalSuiteRunIds(Map.of());
      if (!regressionSelectionEnabled) {
        return List.of();
      }
      return regressionSuitePool.getActiveIds().stream()
          .filter(id -> !criticalIds.contains(id))
          .toList();
    }

    private Map<String, List<String>> currentScopeSuiteRunIds() {
      Map<String, List<String>> values = new LinkedHashMap<>();
      for (Map.Entry<String, SuiteRunPool> entry : suiteScopePools.entrySet()) {
        values.put(entry.getKey(), entry.getValue().getActiveIds());
      }
      return values;
    }

    private Set<String> currentCriticalSuiteRunIds(
        Map<String, Map<String, List<RunRecord>>> refreshedScopeRuns) {
      Set<String> ids = new LinkedHashSet<>();
      for (OctaneGateScope scope : request.getScopes()) {
        if (!"critical".equalsIgnoreCase(scope.getName()) || !scope.isSuiteRunScope()) {
          continue;
        }
        Map<String, List<RunRecord>> refreshed = refreshedScopeRuns.get(scope.getName());
        if (refreshed != null) {
          ids.addAll(refreshed.keySet());
        } else {
          SuiteRunPool pool = suiteScopePools.get(scope.getName());
          if (pool != null) {
            ids.addAll(pool.getActiveIds());
          }
        }
      }
      return ids;
    }

    private final class SuiteRunPool {
      private final String label;
      private final SuiteRunSelector selector;
      private final LinkedHashSet<String> activeIds = new LinkedHashSet<>();
      private boolean initialized;

      private SuiteRunPool(String label, SuiteRunSelector selector) {
        this.label = label;
        this.selector = selector;
      }

      private void preflight() throws IOException, InterruptedException {
        if (!selector.isConfigured()) {
          initialized = true;
          return;
        }
        List<String> candidates = candidateIds();
        Map<String, List<RunRecord>> available;
        if (selector.isDynamic()) {
          logListener.logDynamicSuiteSelector(
              listener, label, selector.getReleaseName(), selector.getSprintName());
          available = client.fetchAvailableSuiteChildRuns(sharedSpaceId, workspaceId, candidates);
        } else {
          available = client.fetchSuiteChildRuns(sharedSpaceId, workspaceId, candidates);
        }
        reconcile(available.keySet());
        initialized = true;
        if (selector.isDynamic() && activeIds.isEmpty()) {
          logListener.logNoDynamicSuiteRuns(
              listener, label, selector.getReleaseName(), selector.getSprintName());
        }
      }

      private Map<String, List<RunRecord>> refresh() throws IOException, InterruptedException {
        if (!selector.isConfigured()) {
          return Map.of();
        }
        List<String> candidates = candidateIds();
        Map<String, List<RunRecord>> available =
            client.fetchAvailableSuiteChildRuns(sharedSpaceId, workspaceId, candidates);
        reconcile(available.keySet());
        return available;
      }

      private List<String> candidateIds() throws IOException, InterruptedException {
        List<String> ids =
            selector.isDynamic()
                ? client.fetchSuiteRunIdsByReleaseAndSprint(
                    sharedSpaceId, workspaceId, selector.getReleaseName(), selector.getSprintName())
                : selector.getExplicitIds();
        if (ids.size() > GateRequest.MAX_SUITE_RUN_IDS) {
          throw new AbortException(
              label
                  + " release/sprint selection returned more than "
                  + GateRequest.MAX_SUITE_RUN_IDS
                  + " suite runs.");
        }
        return ids;
      }

      private void reconcile(Set<String> availableIds) {
        LinkedHashSet<String> available = new LinkedHashSet<>(availableIds);
        if (initialized) {
          List<String> added = available.stream().filter(id -> !activeIds.contains(id)).toList();
          List<String> removed = activeIds.stream().filter(id -> !available.contains(id)).toList();
          if (!added.isEmpty()) {
            logListener.logSuiteRunsAdded(listener, label, added);
          }
          if (!removed.isEmpty()) {
            logListener.logSuiteRunsRemoved(listener, label, removed);
          }
        }
        activeIds.clear();
        activeIds.addAll(available);
      }

      private boolean isConfigured() {
        return selector.isConfigured();
      }

      private boolean isEmpty() {
        return activeIds.isEmpty();
      }

      private List<String> getActiveIds() {
        return List.copyOf(activeIds);
      }
    }

    private record CurrentSuiteRuns(
        Map<String, List<RunRecord>> regressionSuiteRuns,
        Map<String, Map<String, List<RunRecord>>> scopeSuiteRuns,
        boolean awaitingSuiteDiscovery) {}

    @Override
    public void close() throws IOException {
      client.close();
    }
  }

  public static final class PollingState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Instant startedAt;
    private final OctaneDefectLedger defectLedger = new OctaneDefectLedger();
    private boolean extendedTimeActive;
    private boolean manualExitFinalizingLogged;
    private boolean waitingPublished;

    public PollingState(Instant startedAt) {
      this(startedAt, false);
    }

    public PollingState(Instant startedAt, boolean waitingPublished) {
      this.startedAt = startedAt == null ? Instant.now() : startedAt;
      this.waitingPublished = waitingPublished;
    }

    public Instant getStartedAt() {
      return startedAt;
    }

    public OctaneDefectLedger getDefectLedger() {
      return defectLedger;
    }

    public boolean isExtendedTimeActive() {
      return extendedTimeActive;
    }

    private void setExtendedTimeActive(boolean extendedTimeActive) {
      this.extendedTimeActive = extendedTimeActive;
    }

    private synchronized boolean markManualExitFinalizingLogged() {
      if (manualExitFinalizingLogged) {
        return false;
      }
      manualExitFinalizingLogged = true;
      return true;
    }

    public boolean isWaitingPublished() {
      return waitingPublished;
    }

    private void setWaitingPublished(boolean waitingPublished) {
      this.waitingPublished = waitingPublished;
    }
  }

  public static final class PollOutcome {
    private final GateResult result;
    private final Duration nextDelay;
    private final boolean complete;

    private PollOutcome(GateResult result, Duration nextDelay, boolean complete) {
      this.result = result;
      this.nextDelay = nextDelay;
      this.complete = complete;
    }

    private static PollOutcome complete(GateResult result) {
      return new PollOutcome(result, Duration.ZERO, true);
    }

    private static PollOutcome continueAfter(Duration delay) {
      return new PollOutcome(null, delay == null ? Duration.ZERO : delay, false);
    }

    public GateResult getResult() {
      return result;
    }

    public Duration getNextDelay() {
      return nextDelay;
    }

    public boolean isComplete() {
      return complete;
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
    if (!manualExitRequested) {
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
    return poll(
        client,
        request,
        suiteRuns,
        Map.of(),
        !suiteRunIds.isEmpty(),
        false,
        sharedSpaceId,
        workspaceId,
        criteria,
        classifier,
        listener,
        defectLedger);
  }

  private GateResult poll(
      OctaneClient client,
      GateRequest request,
      Map<String, List<RunRecord>> suiteRuns,
      Map<String, Map<String, List<RunRecord>>> suiteScopeRuns,
      boolean regressionEvaluationEnabled,
      boolean awaitingSuiteDiscovery,
      String sharedSpaceId,
      String workspaceId,
      CriteriaExpression criteria,
      StatusClassifier classifier,
      TaskListener listener,
      OctaneDefectLedger defectLedger)
      throws IOException, InterruptedException {
    List<RunRecord> childRuns = flattenAndDedupeRuns(suiteRuns);
    GateMetrics regressionMetrics = GateMetrics.fromRuns(childRuns, classifier);
    List<String> childRunIds = runIds(childRuns);

    Map<String, GateMetrics> scopedMetrics = new LinkedHashMap<>();
    Map<String, GateScopeResult> scopedResults = new LinkedHashMap<>();
    for (OctaneGateScope scope : request.getScopes()) {
      GateScopeResult scopeResult =
          pollScope(
              client,
              sharedSpaceId,
              workspaceId,
              childRunIds,
              suiteRuns,
              suiteScopeRuns.get(scope.getName()),
              classifier,
              scope);
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
    String effectiveCriteria =
        regressionEvaluationEnabled
            ? request.getCriteria()
            : criteria.effectiveExpression(metricsContext, false);
    CriteriaEvaluation criteriaEvaluation =
        regressionEvaluationEnabled
            ? criteria.evaluateDetailed(metricsContext, true)
            : criteria.evaluateAppliedDetailed(metricsContext, false);
    boolean passed = !awaitingSuiteDiscovery && criteriaEvaluation.isPassed();
    boolean terminal =
        !awaitingSuiteDiscovery && (!regressionEvaluationEnabled || regressionMetrics.isTerminal());
    for (GateMetrics scopedMetric : scopedMetrics.values()) {
      if (!Objects.requireNonNull(scopedMetric).isTerminal()) {
        terminal = false;
        break;
      }
    }
    return new GateResult(
        String.join(",", suiteRuns.keySet()),
        effectiveCriteria,
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
      if (defectLedger.isAtCapacity()) {
        listener
            .getLogger()
            .println(
                "Octane defect history reached its safety limit of "
                    + OctaneDefectLedger.MAXIMUM_DEFECTS
                    + " unique defects; existing defect states will continue to refresh.");
      }
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
          defectLedger.getDefects());
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
      Map<String, List<RunRecord>> resolvedScopeSuiteRuns,
      StatusClassifier classifier,
      OctaneGateScope scope)
      throws IOException, InterruptedException {
    if (scope.isSuiteRunScope()) {
      Map<String, List<RunRecord>> scopeSuiteRuns =
          resolvedScopeSuiteRuns == null
              ? fetchSuiteChildRuns(client, sharedSpaceId, workspaceId, scope.getSuiteRunIds())
              : resolvedScopeSuiteRuns;
      List<RunRecord> scopeRuns = flattenAndDedupeRuns(scopeSuiteRuns);
      GateMetrics metrics = GateMetrics.fromRuns(scopeRuns, classifier);
      return new GateScopeResult(
          scope.getName(),
          "",
          List.of(),
          String.join(",", scopeSuiteRuns.keySet()),
          List.copyOf(scopeSuiteRuns.keySet()),
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
    return client.fetchSuiteChildRuns(sharedSpaceId, workspaceId, suiteRunIds);
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

  static boolean regressionSelectionEnabled(GateRequest request) {
    SuiteRunSelector regressionSelector = request.getSuiteRunSelector();
    if (!regressionSelector.isConfigured()) {
      return false;
    }
    for (OctaneGateScope scope : request.getScopes()) {
      if ("critical".equalsIgnoreCase(scope.getName())
          && scope.isSuiteRunScope()
          && regressionSelector.equals(scope.getSuiteRunSelector())) {
        return false;
      }
    }
    return regressionSelector.isDynamic() || !regressionSuiteRunIdsForCriteria(request).isEmpty();
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
    SuiteRunSelector requestSelector =
        validatedSelector("Suite run selection", request.getSuiteRunId());
    List<String> requestedSuiteRunIds = requestSelector.getExplicitIds();
    validateSuiteRunSources(request);
    if (requestedSuiteRunIds.size() > GateRequest.MAX_SUITE_RUN_IDS) {
      throw new AbortException(
          "At most " + GateRequest.MAX_SUITE_RUN_IDS + " Octane suite run IDs are supported.");
    }
    validateNumericId("Shared space ID", request.getSharedSpaceId());
    validateNumericId("Workspace ID", request.getWorkspaceId());
    if (request.getScopes().size() > MAX_SCOPES) {
      throw new AbortException("At most " + MAX_SCOPES + " Octane scopes are supported.");
    }
    if (request.getDefectGroups().size() > MAX_DEFECT_GROUPS) {
      throw new AbortException(
          "At most " + MAX_DEFECT_GROUPS + " Octane defect groups are supported.");
    }
    for (OctaneGateScope scope : request.getScopes()) {
      validateScope(scope);
    }
    validateDefectGroups(request.getDefectGroups());
  }

  static void validateSuiteRunSources(GateRequest request) throws AbortException {
    boolean primaryConfigured = request.getSuiteRunSelector().isConfigured();
    boolean criticalConfigured =
        request.getScopes().stream()
            .anyMatch(
                scope -> "critical".equalsIgnoreCase(scope.getName()) && scope.isSuiteRunScope());
    if (!primaryConfigured && !criticalConfigured) {
      throw new AbortException(
          "A critical Octane suite run selection is required when the regression selection is empty.");
    }
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
    SuiteRunSelector selector =
        hasSuiteRunIds
            ? validatedSelector("Suite run selection", scope.getSuiteRunId())
            : SuiteRunSelector.parse("");
    if (selector.getExplicitIds().size() > GateRequest.MAX_SUITE_RUN_IDS) {
      throw new AbortException(
          "Octane scope '"
              + scope.getName()
              + "' exceeds the "
              + GateRequest.MAX_SUITE_RUN_IDS
              + " suite run ID limit.");
    }
  }

  private SuiteRunSelector validatedSelector(String label, String value) throws AbortException {
    try {
      return SuiteRunSelector.parse(value);
    } catch (IllegalArgumentException e) {
      throw new AbortException(label + " is invalid: " + e.getMessage());
    }
  }

  private String displayScopeName(String scopeName) {
    String name = Util.trimToEmpty(scopeName);
    if (name.isEmpty()) {
      return "Scope";
    }
    return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
  }

  private void validateNumericId(String label, String value) throws AbortException {
    String id = Util.trimToEmpty(value);
    if (id.isEmpty()) {
      return;
    }
    if (!OCTANE_NUMERIC_ID.matcher(id).matches()) {
      throw new AbortException(label + " must contain 1 to 18 digits.");
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
