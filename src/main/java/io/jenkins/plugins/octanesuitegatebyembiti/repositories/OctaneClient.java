package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import hudson.AbortException;
import io.jenkins.plugins.octanesuitegatebyembiti.configs.OctaneServerUrl;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class OctaneClient implements AutoCloseable {
  private static final int PAGE_SIZE = 200;
  private static final int QUERY_CHUNK_SIZE = 40;
  private static final int MAX_ATTEMPTS = 3;
  private static final int RESPONSE_BODY_LIMIT = 1_000;
  static final int MAX_JSON_RESPONSE_BYTES = 16 * 1024 * 1024;
  private static final Pattern SAFE_ENTITY_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
  private static final Pattern SENSITIVE_RESPONSE_VALUE =
      Pattern.compile(
          "(?i)(\\\"(?:client_secret|password|token|access_token|refresh_token|authorization)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
  private static final String TECH_PREVIEW_HEADER = "ALM-OCTANE-TECH-PREVIEW";
  private static final String RUN_FIELDS =
      "id,name,native_status{logical_name,name},status{logical_name,name},run_by{id,name},"
          + "test{id,name},product_areas{id,name},runs_in_suite";
  private static final String EXTENDED_DEFECT_FIELDS =
      "id,name,severity{logical_name,name},priority{logical_name,name},phase{logical_name,name},"
          + "run{id,name},detected_in_run{id,name},test{id,name},owner_test{id,name},"
          + "product_areas{id,name}";
  private static final String DEFECT_FIELDS =
      "id,name,severity{logical_name,name},priority{logical_name,name},phase{logical_name,name},"
          + "run{id,name},test{id,name},product_areas{id,name}";
  private static final String MINIMAL_DEFECT_FIELDS =
      "id,name,severity{logical_name,name},priority{logical_name,name},phase{logical_name,name}";
  private static final HttpClient SHARED_HTTP_CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(30))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String baseUrl;
  private final String clientId;
  private final String clientSecret;
  private String cookieHeader = "";

  public OctaneClient(String baseUrl, String clientId, String clientSecret) {
    this(SHARED_HTTP_CLIENT, baseUrl, clientId, clientSecret);
  }

  public OctaneClient(HttpClient httpClient, String baseUrl, String clientId, String clientSecret) {
    this.httpClient = httpClient;
    this.baseUrl = OctaneServerUrl.normalize(baseUrl);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public void authenticate() throws IOException, InterruptedException {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("client_id", clientId);
    payload.put("client_secret", clientSecret);

    HttpRequest request =
        requestBuilder(baseUrl + "/authentication/sign_in")
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
    StringResponse response = sendForString(request);
    if (response.statusCode() != 200) {
      throw new AbortException(
          "ALM Octane authentication failed with HTTP "
              + response.statusCode()
              + " for "
              + request.uri()
              + responseBodyMessage(response.body()));
    }
    rememberCookies(response.headers());
  }

  public List<RunRecord> fetchSuiteChildRuns(
      String sharedSpaceId, String workspaceId, String suiteRunId)
      throws IOException, InterruptedException {
    JsonNode suiteRun = fetchSuiteRun(sharedSpaceId, workspaceId, suiteRunId);
    List<String> runIds = parseRunsInSuite(suiteRun);
    if (runIds.isEmpty()) {
      return List.of(parseRun(suiteRun));
    }
    return fetchRunsByIds(sharedSpaceId, workspaceId, runIds, "");
  }

  public Map<String, List<RunRecord>> fetchSuiteChildRuns(
      String sharedSpaceId, String workspaceId, List<String> suiteRunIds)
      throws IOException, InterruptedException {
    return fetchSuiteChildRuns(sharedSpaceId, workspaceId, suiteRunIds, false);
  }

  /** Fetches all currently reachable suites while omitting suite runs confirmed as missing. */
  public Map<String, List<RunRecord>> fetchAvailableSuiteChildRuns(
      String sharedSpaceId, String workspaceId, List<String> suiteRunIds)
      throws IOException, InterruptedException {
    return fetchSuiteChildRuns(sharedSpaceId, workspaceId, suiteRunIds, true);
  }

  /** Resolves suite runs assigned to the named release and sprint. */
  public List<String> fetchSuiteRunIdsByReleaseAndSprint(
      String sharedSpaceId, String workspaceId, String releaseName, String sprintName)
      throws IOException, InterruptedException {
    String relationshipQuery =
        "release EQ {name EQ "
            + octaneStringLiteral(releaseName)
            + "};sprint EQ {name EQ "
            + octaneStringLiteral(sprintName)
            + "}";
    String aggregateQuery =
        "test EQ {subtype EQ ^test_suite^};release EQ {name EQ "
            + octaneStringLiteral(releaseName)
            + "};sprint EQ {name EQ "
            + octaneStringLiteral(sprintName)
            + "}";
    try {
      return fetchEntityIds(sharedSpaceId, workspaceId, "runs", aggregateQuery);
    } catch (IOException aggregateFailure) {
      try {
        return fetchEntityIds(sharedSpaceId, workspaceId, "suite_runs", relationshipQuery);
      } catch (IOException suiteRunsFailure) {
        throw new AbortException(
            "ALM Octane release/sprint suite discovery failed. Runs collection lookup failed: "
                + aggregateFailure.getMessage()
                + ". suite_runs fallback failed: "
                + suiteRunsFailure.getMessage());
      }
    }
  }

  private List<String> fetchEntityIds(
      String sharedSpaceId, String workspaceId, String entityName, String query)
      throws IOException, InterruptedException {
    LinkedHashSet<String> suiteRunIds = new LinkedHashSet<>();
    int offset = 0;
    while (true) {
      String path =
          workspacePath(sharedSpaceId, workspaceId)
              + "/"
              + entityName
              + "?"
              + parameter("query", quote(query))
              + "&"
              + parameter("fields", "id")
              + "&"
              + parameter("limit", Integer.toString(PAGE_SIZE))
              + "&"
              + parameter("offset", Integer.toString(offset));
      JsonNode data = getJson(path).path("data");
      if (!data.isArray() || data.isEmpty()) {
        break;
      }
      for (JsonNode node : data) {
        String id = node.path("id").asString();
        if (!id.isEmpty()) {
          suiteRunIds.add(safeEntityId(id));
        }
      }
      if (data.size() < PAGE_SIZE) {
        break;
      }
      offset += PAGE_SIZE;
    }
    return List.copyOf(suiteRunIds);
  }

  private Map<String, List<RunRecord>> fetchSuiteChildRuns(
      String sharedSpaceId,
      String workspaceId,
      List<String> suiteRunIds,
      boolean tolerateMissingSuites)
      throws IOException, InterruptedException {
    if (suiteRunIds.isEmpty()) {
      return Map.of();
    }
    String namespace =
        baseUrl
            + "\u0000"
            + clientId
            + "\u0000"
            + sharedSpaceId
            + "\u0000"
            + workspaceId
            + (tolerateMissingSuites ? "\u0000available" : "\u0000strict");
    Map<String, List<String>> topology =
        OctaneSuiteTopologyCache.getAll(
            namespace,
            suiteRunIds,
            missing ->
                fetchSuiteTopologies(sharedSpaceId, workspaceId, missing, tolerateMissingSuites));
    LinkedHashSet<String> childRunIds = new LinkedHashSet<>();
    for (String suiteRunId : suiteRunIds) {
      childRunIds.addAll(topology.getOrDefault(suiteRunId, List.of()));
    }
    List<RunRecord> childRuns =
        fetchRunsByIds(sharedSpaceId, workspaceId, new ArrayList<>(childRunIds), "");
    Map<String, RunRecord> runsById = new LinkedHashMap<>();
    for (RunRecord run : childRuns) {
      runsById.putIfAbsent(run.getId(), run);
    }
    Map<String, List<RunRecord>> result = new LinkedHashMap<>();
    for (String suiteRunId : suiteRunIds) {
      List<String> topologyRunIds = topology.getOrDefault(suiteRunId, List.of());
      // Existing suites always retain their own run ID when no child runs are present. An empty
      // topology therefore identifies a suite omitted by tolerant missing-suite resolution.
      if (tolerateMissingSuites && topologyRunIds.isEmpty()) {
        continue;
      }
      List<RunRecord> runs = new ArrayList<>();
      for (String runId : topologyRunIds) {
        RunRecord run = runsById.get(runId);
        if (run != null) {
          runs.add(run);
        }
      }
      if (tolerateMissingSuites && runs.isEmpty()) {
        continue;
      }
      result.put(suiteRunId, List.copyOf(runs));
    }
    return result;
  }

  public List<RunRecord> fetchScopedRuns(
      String sharedSpaceId, String workspaceId, List<String> runIds, String scopeQuery)
      throws IOException, InterruptedException {
    if (runIds.isEmpty()) {
      return List.of();
    }
    return fetchRunsByIds(sharedSpaceId, workspaceId, runIds, scopeQuery);
  }

  public List<String> fetchSuiteChildRunIds(
      String sharedSpaceId, String workspaceId, String suiteRunId)
      throws IOException, InterruptedException {
    JsonNode suiteRun = fetchSuiteRun(sharedSpaceId, workspaceId, suiteRunId);
    List<String> runIds = parseRunsInSuite(suiteRun);
    if (runIds.isEmpty()) {
      return List.of(suiteRun.path("id").asString(suiteRunId));
    }
    return runIds;
  }

  public List<DefectRecord> fetchLinkedDefects(
      String sharedSpaceId,
      String workspaceId,
      Map<String, List<RunRecord>> suiteRuns,
      String defectQuery,
      int maxDefects)
      throws IOException, InterruptedException {
    if (suiteRuns.isEmpty() || maxDefects <= 0) {
      return List.of();
    }

    LinkedHashSet<String> runIds = collectRunIds(suiteRuns);
    LinkedHashSet<String> testIds = collectTestIds(suiteRuns);
    if (runIds.isEmpty() && testIds.isEmpty()) {
      return List.of();
    }

    Map<String, DefectRecord> recordsById = new LinkedHashMap<>();
    fetchDefectsForRelation(
        sharedSpaceId, workspaceId, "test", testIds, defectQuery, maxDefects, recordsById);
    fetchDefectsForRelation(
        sharedSpaceId, workspaceId, "run", runIds, defectQuery, maxDefects, recordsById);
    fetchDefectsForRelation(
        sharedSpaceId,
        workspaceId,
        "detected_in_run",
        runIds,
        defectQuery,
        maxDefects,
        recordsById);
    fetchDefectsForRelation(
        sharedSpaceId, workspaceId, "owner_test", testIds, defectQuery, maxDefects, recordsById);
    return new ArrayList<>(recordsById.values());
  }

  public List<DefectRecord> fetchDefectsByIds(
      String sharedSpaceId, String workspaceId, List<String> defectIds, int maxDefects)
      throws IOException, InterruptedException {
    if (defectIds.isEmpty() || maxDefects <= 0) {
      return List.of();
    }
    List<String> clauses = new ArrayList<>();
    for (String defectId : defectIds) {
      if (!Util.isBlank(defectId)) {
        clauses.add("id EQ " + safeEntityId(defectId));
      }
    }
    if (clauses.isEmpty()) {
      return List.of();
    }
    List<DefectRecord> records = new ArrayList<>();
    for (int start = 0;
        start < clauses.size() && records.size() < maxDefects;
        start += QUERY_CHUNK_SIZE) {
      int end = Math.min(start + QUERY_CHUNK_SIZE, clauses.size());
      records.addAll(
          fetchDefectsChunk(
              sharedSpaceId,
              workspaceId,
              clauses.subList(start, end),
              "",
              maxDefects - records.size()));
    }
    return records;
  }

  public int testWorkspaceAccess(String sharedSpaceId, String workspaceId)
      throws IOException, InterruptedException {
    String path =
        workspacePath(sharedSpaceId, workspaceId)
            + "/runs?"
            + parameter("fields", "id")
            + "&"
            + parameter("limit", "1");
    HttpResponse<Void> response =
        send(requestBuilder(baseUrl + path).GET().build(), HttpResponse.BodyHandlers.discarding());
    rememberCookies(response);
    return response.statusCode();
  }

  @Override
  public void close() throws IOException {
    if (cookieHeader.isEmpty()) {
      return;
    }

    HttpRequest request =
        requestBuilder(baseUrl + "/authentication/sign_out")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    try {
      send(request, HttpResponse.BodyHandlers.discarding());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while signing out of ALM Octane.", e);
    }
  }

  private JsonNode fetchSuiteRun(String sharedSpaceId, String workspaceId, String suiteRunId)
      throws IOException, InterruptedException {
    String query = "id EQ " + safeEntityId(suiteRunId);
    String path =
        workspacePath(sharedSpaceId, workspaceId)
            + "/runs?"
            + parameter("query", quote(query))
            + "&"
            + parameter("fields", RUN_FIELDS)
            + "&"
            + parameter("limit", "1");
    Exception runsLookupFailure = null;
    try {
      JsonNode collection = getJson(path);
      JsonNode data = collection.path("data");
      if (data.isArray() && !data.isEmpty()) {
        JsonNode candidate = data.get(0);
        if (suiteRunId.equals(candidate.path("id").asString())) {
          return candidate;
        }
      }
    } catch (IOException | JacksonException e) {
      // Some Octane versions reject querying suite runs through the aggregate collection.
      runsLookupFailure = e;
    }

    String fallbackPath =
        workspacePath(sharedSpaceId, workspaceId)
            + "/suite_runs/"
            + encode(suiteRunId)
            + "?"
            + parameter("fields", RUN_FIELDS);
    JsonNode node;
    try {
      node = getJson(fallbackPath);
    } catch (IOException e) {
      if (isNotFound(e)) {
        throw new AbortException(suiteRunNotFoundMessage(sharedSpaceId, workspaceId, suiteRunId));
      }
      if (runsLookupFailure != null) {
        throw new AbortException(
            "ALM Octane suite run lookup failed. Runs collection lookup failed: "
                + runsLookupFailure.getMessage()
                + ". suite_runs fallback failed: "
                + e.getMessage());
      }
      throw e;
    }
    JsonNode data = node.path("data");
    if (data.isArray() && !data.isEmpty()) {
      return data.get(0);
    }
    if (!node.path("id").isMissingNode()) {
      return node;
    }
    throw new AbortException(suiteRunNotFoundMessage(sharedSpaceId, workspaceId, suiteRunId));
  }

  private Map<String, List<String>> fetchSuiteTopologies(
      String sharedSpaceId,
      String workspaceId,
      List<String> suiteRunIds,
      boolean tolerateMissingSuites)
      throws IOException, InterruptedException {
    Map<String, List<String>> topology = new LinkedHashMap<>();
    for (int start = 0; start < suiteRunIds.size(); start += QUERY_CHUNK_SIZE) {
      int end = Math.min(start + QUERY_CHUNK_SIZE, suiteRunIds.size());
      List<String> chunk = suiteRunIds.subList(start, end);
      try {
        String path =
            workspacePath(sharedSpaceId, workspaceId)
                + "/runs?"
                + parameter("query", quote(buildIdQuery(chunk)))
                + "&"
                + parameter("fields", RUN_FIELDS)
                + "&"
                + parameter("limit", Integer.toString(PAGE_SIZE));
        JsonNode data = getJson(path).path("data");
        if (data.isArray()) {
          for (JsonNode suiteRun : data) {
            String id = suiteRun.path("id").asString();
            if (!chunk.contains(id)) {
              continue;
            }
            List<String> runIds = parseRunsInSuite(suiteRun);
            topology.put(id, runIds.isEmpty() ? List.of(id) : List.copyOf(runIds));
          }
        }
      } catch (IOException ignored) {
        // Older Octane versions may require the suite_runs entity endpoint.
      }
      for (String suiteRunId : chunk) {
        if (topology.containsKey(suiteRunId)) {
          continue;
        }
        JsonNode suiteRun;
        try {
          suiteRun = fetchSuiteRun(sharedSpaceId, workspaceId, suiteRunId);
        } catch (AbortException e) {
          if (tolerateMissingSuites && isMissingSuiteRun(e)) {
            continue;
          }
          throw e;
        }
        List<String> runIds = parseRunsInSuite(suiteRun);
        topology.put(
            suiteRunId,
            runIds.isEmpty()
                ? List.of(suiteRun.path("id").asString(suiteRunId))
                : List.copyOf(runIds));
      }
    }
    return topology;
  }

  private boolean isNotFound(IOException exception) {
    return exception != null && Util.trimToEmpty(exception.getMessage()).contains("HTTP 404");
  }

  private boolean isMissingSuiteRun(AbortException exception) {
    String message = Util.trimToEmpty(exception.getMessage());
    return message.startsWith("ALM Octane suite run ") && message.contains(" was not found ");
  }

  private String suiteRunNotFoundMessage(
      String sharedSpaceId, String workspaceId, String suiteRunId) {
    return "ALM Octane suite run "
        + Util.trimToEmpty(suiteRunId)
        + " was not found in shared space "
        + Util.trimToEmpty(sharedSpaceId)
        + " / workspace "
        + Util.trimToEmpty(workspaceId)
        + ". Check that the job's shared space ID and workspace ID match the Octane workspace "
        + "that owns this suite run.";
  }

  private List<RunRecord> fetchRunsByIds(
      String sharedSpaceId, String workspaceId, List<String> runIds, String scopeQuery)
      throws IOException, InterruptedException {
    List<RunRecord> records = new ArrayList<>();
    for (int start = 0; start < runIds.size(); start += QUERY_CHUNK_SIZE) {
      int end = Math.min(start + QUERY_CHUNK_SIZE, runIds.size());
      records.addAll(
          fetchRunsChunk(sharedSpaceId, workspaceId, runIds.subList(start, end), scopeQuery));
    }
    return records;
  }

  private List<RunRecord> fetchRunsChunk(
      String sharedSpaceId, String workspaceId, List<String> runIds, String scopeQuery)
      throws IOException, InterruptedException {
    String query = buildIdQuery(runIds);
    if (!Util.isBlank(scopeQuery)) {
      query = "(" + query + ");(" + scopeQuery + ")";
    }

    Set<String> requestedIds = new LinkedHashSet<>(runIds);
    Map<String, RunRecord> recordsById = new LinkedHashMap<>();
    int offset = 0;
    while (recordsById.size() < requestedIds.size()) {
      String path =
          workspacePath(sharedSpaceId, workspaceId)
              + "/runs?"
              + parameter("query", quote(query))
              + "&"
              + parameter("fields", RUN_FIELDS)
              + "&"
              + parameter("limit", Integer.toString(PAGE_SIZE))
              + "&"
              + parameter("offset", Integer.toString(offset));
      JsonNode collection = getJson(path);
      JsonNode data = collection.path("data");
      if (!data.isArray() || data.isEmpty()) {
        break;
      }
      int previousSize = recordsById.size();
      for (JsonNode node : data) {
        RunRecord record = parseRun(node);
        if (requestedIds.contains(record.getId())) {
          recordsById.putIfAbsent(record.getId(), record);
        }
      }
      if (data.size() < PAGE_SIZE || recordsById.size() == previousSize) {
        break;
      }
      offset += PAGE_SIZE;
    }
    return new ArrayList<>(recordsById.values());
  }

  private String buildIdQuery(List<String> runIds) {
    List<String> clauses = new ArrayList<>();
    for (String runId : runIds) {
      clauses.add("id EQ " + safeEntityId(runId));
    }
    return String.join("||", clauses);
  }

  private LinkedHashSet<String> collectRunIds(Map<String, List<RunRecord>> suiteRuns) {
    LinkedHashSet<String> runIds = new LinkedHashSet<>();
    for (List<RunRecord> runs : suiteRuns.values()) {
      for (RunRecord run : runs) {
        if (!Util.isBlank(run.getId())) {
          runIds.add(run.getId());
        }
      }
    }
    return runIds;
  }

  private LinkedHashSet<String> collectTestIds(Map<String, List<RunRecord>> suiteRuns) {
    LinkedHashSet<String> testIds = new LinkedHashSet<>();
    for (List<RunRecord> runs : suiteRuns.values()) {
      for (RunRecord run : runs) {
        if (!Util.isBlank(run.getTestId())) {
          testIds.add(run.getTestId());
        }
      }
    }
    return testIds;
  }

  private void fetchDefectsForRelation(
      String sharedSpaceId,
      String workspaceId,
      String relationField,
      Set<String> relatedIds,
      String defectQuery,
      int maxDefects,
      Map<String, DefectRecord> recordsById)
      throws IOException, InterruptedException {
    if (relatedIds.isEmpty() || recordsById.size() >= maxDefects) {
      return;
    }
    List<String> clauses = buildRelationClauses(relationField, relatedIds);
    try {
      for (int start = 0;
          start < clauses.size() && recordsById.size() < maxDefects;
          start += QUERY_CHUNK_SIZE) {
        int end = Math.min(start + QUERY_CHUNK_SIZE, clauses.size());
        List<DefectRecord> records =
            fetchDefectsChunk(
                sharedSpaceId,
                workspaceId,
                clauses.subList(start, end),
                defectQuery,
                maxDefects - recordsById.size());
        for (DefectRecord record : records) {
          recordsById.putIfAbsent(record.getId(), record);
        }
      }
    } catch (IOException e) {
      if (!isUnknownFieldFailure(e)) {
        throw e;
      }
      // Octane schemas differ by version. Ignore only relationships the server explicitly
      // reports as unknown; transport and server failures must not become zero-defect results.
    }
  }

  private List<String> buildRelationClauses(String relationField, Set<String> relatedIds) {
    List<String> clauses = new ArrayList<>();
    for (String relatedId : relatedIds) {
      clauses.add(relationField + " EQ {id EQ " + safeEntityId(relatedId) + "}");
    }
    return clauses;
  }

  private List<DefectRecord> fetchDefectsChunk(
      String sharedSpaceId,
      String workspaceId,
      List<String> clauses,
      String defectQuery,
      int maxDefects)
      throws IOException, InterruptedException {
    String query = String.join("||", clauses);
    if (!Util.isBlank(defectQuery)) {
      query = "(" + query + ");(" + defectQuery + ")";
    }

    List<DefectRecord> records = new ArrayList<>();
    int offset = 0;
    while (records.size() < maxDefects) {
      int limit = Math.min(PAGE_SIZE, maxDefects - records.size());
      JsonNode collection = getDefectsJson(sharedSpaceId, workspaceId, query, limit, offset);
      JsonNode data = collection.path("data");
      if (!data.isArray() || data.isEmpty()) {
        break;
      }
      for (JsonNode node : data) {
        records.add(parseDefect(node));
      }
      if (data.size() < limit) {
        break;
      }
      offset += limit;
    }
    return records;
  }

  private JsonNode getDefectsJson(
      String sharedSpaceId, String workspaceId, String query, int limit, int offset)
      throws IOException, InterruptedException {
    try {
      return getJson(
          defectsPath(sharedSpaceId, workspaceId, query, EXTENDED_DEFECT_FIELDS, limit, offset));
    } catch (IOException e) {
      if (!isUnknownFieldFailure(e)) {
        throw e;
      }
      try {
        return getJson(
            defectsPath(sharedSpaceId, workspaceId, query, DEFECT_FIELDS, limit, offset));
      } catch (IOException fallbackError) {
        if (!isUnknownFieldFailure(fallbackError)) {
          throw fallbackError;
        }
        return getJson(
            defectsPath(sharedSpaceId, workspaceId, query, MINIMAL_DEFECT_FIELDS, limit, offset));
      }
    }
  }

  private String defectsPath(
      String sharedSpaceId,
      String workspaceId,
      String query,
      String fields,
      int limit,
      int offset) {
    return workspacePath(sharedSpaceId, workspaceId)
        + "/defects?"
        + parameter("query", quote(query))
        + "&"
        + parameter("fields", fields)
        + "&"
        + parameter("limit", Integer.toString(limit))
        + "&"
        + parameter("offset", Integer.toString(offset));
  }

  private boolean isUnknownFieldFailure(IOException exception) {
    String message = exception.getMessage();
    return message != null && message.contains("platform.unknown_field");
  }

  private JsonNode getJson(String path) throws IOException, InterruptedException {
    StringResponse response = sendWithRetry(() -> requestBuilder(baseUrl + path).GET().build());
    try {
      return objectMapper.readTree(response.body());
    } catch (JacksonException e) {
      throw new IOException(
          "ALM Octane returned malformed JSON for "
              + response.request().uri()
              + responseBodyMessage(response.body()),
          e);
    }
  }

  private StringResponse sendWithRetry(RequestFactory requestFactory)
      throws IOException, InterruptedException {
    IOException lastException = null;
    HttpRequest lastRequest = null;
    StringResponse lastResponse = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      HttpRequest request = requestFactory.create();
      lastRequest = request;
      StringResponse response;
      try {
        response = sendForString(request);
      } catch (IOException e) {
        lastException = e;
        pauseBeforeRetry(attempt, null);
        continue;
      }
      rememberCookies(response.headers());
      if (response.statusCode() == 401 && attempt == 1) {
        authenticate();
        continue;
      }
      if (response.statusCode() == 429 || response.statusCode() >= 500) {
        lastResponse = response;
        pauseBeforeRetry(attempt, response);
        continue;
      }
      if (response.statusCode() >= 400) {
        throw requestFailure(request, response);
      }
      return response;
    }
    if (lastResponse != null) {
      throw requestFailure(lastRequest, lastResponse);
    }
    if (lastException != null) {
      throw lastException;
    }
    throw new AbortException("ALM Octane request failed after retries.");
  }

  private HttpRequest.Builder requestBuilder(String uri) {
    URI requestUri = OctaneServerUrl.requireAllowedRequest(baseUrl, URI.create(uri));
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .header(TECH_PREVIEW_HEADER, "true");
    if (!cookieHeader.isEmpty()) {
      builder.header("Cookie", cookieHeader);
    }
    return builder;
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
      throws IOException, InterruptedException {
    return OctaneRequestCoordinator.send(baseUrl, httpClient, request, bodyHandler);
  }

  private StringResponse sendForString(HttpRequest request)
      throws IOException, InterruptedException {
    HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream input = response.body()) {
      byte[] bytes = input.readNBytes(MAX_JSON_RESPONSE_BYTES + 1);
      if (bytes.length > MAX_JSON_RESPONSE_BYTES) {
        throw new IOException(
            "ALM Octane response exceeded the "
                + MAX_JSON_RESPONSE_BYTES
                + " byte safety limit for "
                + request.uri()
                + ".");
      }
      return new StringResponse(
          response.statusCode(),
          response.request(),
          response.headers(),
          new String(bytes, StandardCharsets.UTF_8));
    }
  }

  private AbortException requestFailure(HttpRequest request, StringResponse response) {
    return new AbortException(
        "ALM Octane request failed with HTTP "
            + response.statusCode()
            + " for "
            + request.uri()
            + responseBodyMessage(response.body()));
  }

  private String responseBodyMessage(String body) {
    if (body == null || body.isBlank()) {
      return ". Response body: <empty>";
    }
    String normalized =
        SENSITIVE_RESPONSE_VALUE.matcher(body).replaceAll("$1***$2").replaceAll("\\s+", " ").trim();
    if (normalized.length() > RESPONSE_BODY_LIMIT) {
      normalized = normalized.substring(0, RESPONSE_BODY_LIMIT) + "...";
    }
    return ". Response body: " + normalized;
  }

  private void rememberCookies(HttpResponse<?> response) {
    rememberCookies(response.headers());
  }

  private void rememberCookies(HttpHeaders headers) {
    List<String> cookies = headers.allValues("Set-Cookie");
    if (cookies.isEmpty()) {
      return;
    }
    Set<String> cookiePairs = new LinkedHashSet<>();
    if (!cookieHeader.isEmpty()) {
      for (String existingCookie : cookieHeader.split(";")) {
        String pair = existingCookie.trim();
        if (!pair.isEmpty()) {
          cookiePairs.add(pair);
        }
      }
    }
    for (String cookie : cookies) {
      String pair = cookie.split(";", 2)[0];
      if (!pair.isBlank()) {
        cookiePairs.add(pair);
      }
    }
    if (!cookiePairs.isEmpty()) {
      cookieHeader = String.join("; ", cookiePairs);
    }
  }

  private void pauseBeforeRetry(int attempt, StringResponse response) throws InterruptedException {
    if (attempt >= MAX_ATTEMPTS) {
      return;
    }
    long exponentialMillis = Math.min(10_000L, 500L << Math.max(0, attempt - 1));
    long jitterMillis = ThreadLocalRandom.current().nextLong(exponentialMillis / 4L + 1L);
    long retryAfterMillis = retryAfterMillis(response);
    Thread.sleep(Math.max(retryAfterMillis, exponentialMillis + jitterMillis));
  }

  private long retryAfterMillis(StringResponse response) {
    if (response == null) {
      return 0L;
    }
    Optional<String> value = response.headers().firstValue("Retry-After");
    if (value.isEmpty() || value.get().isBlank()) {
      return 0L;
    }
    String retryAfter = value.get().trim();
    try {
      return Math.max(0L, Long.parseLong(retryAfter)) * 1000L;
    } catch (NumberFormatException ignored) {
      try {
        Duration duration =
            Duration.between(
                ZonedDateTime.now(),
                ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME));
        return Math.max(0L, duration.toMillis());
      } catch (DateTimeParseException invalidDate) {
        return 0L;
      }
    }
  }

  private String workspacePath(String sharedSpaceId, String workspaceId) {
    return "/api/shared_spaces/" + encode(sharedSpaceId) + "/workspaces/" + encode(workspaceId);
  }

  private String parameter(String name, String value) {
    return encode(name) + "=" + encode(value);
  }

  private String quote(String value) {
    return "\"" + value + "\"";
  }

  private String octaneStringLiteral(String value) {
    String source = Util.trimToEmpty(value);
    StringBuilder escaped = new StringBuilder(source.length() + 2).append('^');
    for (int index = 0; index < source.length(); index++) {
      char character = source.charAt(index);
      if ("\\^\"'{}()[]?<>".indexOf(character) >= 0) {
        escaped.append('\\');
      }
      escaped.append(character);
    }
    return escaped.append('^').toString();
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String safeEntityId(String value) {
    String id = Util.trimToEmpty(value);
    if (!SAFE_ENTITY_ID.matcher(id).matches()) {
      throw new IllegalArgumentException("ALM Octane returned an unsafe entity ID.");
    }
    return id;
  }

  private RunRecord parseRun(JsonNode node) {
    String status = readStatus(node.path("native_status"));
    if (status.isEmpty()) {
      status = readStatus(node.path("status"));
    }
    EntityReference test = readEntity(node.path("test"));
    EntityReference project = readFirstEntity(node, List.of("product_areas", "product_area"));
    return new RunRecord(
        node.path("id").asString(),
        node.path("name").asString(),
        status,
        readPersonName(node),
        test.id,
        test.name,
        project.id,
        project.name);
  }

  private DefectRecord parseDefect(JsonNode node) {
    EntityReference run = readFirstEntity(node, List.of("run", "detected_in_run"));
    EntityReference test = readFirstEntity(node, List.of("test", "owner_test"));
    EntityReference project = readFirstEntity(node, List.of("product_areas", "product_area"));
    return new DefectRecord(
        node.path("id").asString(),
        node.path("name").asString(),
        readStatus(node.path("severity")),
        readStatus(node.path("priority")),
        readStatus(node.path("phase")),
        run.id,
        test.id,
        project.id,
        project.name);
  }

  private String readStatus(JsonNode statusNode) {
    if (statusNode.isMissingNode() || statusNode.isNull()) {
      return "";
    }
    if (statusNode.isString()) {
      return statusNode.asString();
    }
    Optional<String> logicalName = readOptionalText(statusNode, "logical_name");
    if (logicalName.isPresent()) {
      return logicalName.get();
    }
    return readOptionalText(statusNode, "name").orElse("");
  }

  private Optional<String> readOptionalText(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    if (value.isMissingNode() || value.isNull()) {
      return Optional.empty();
    }
    return Optional.of(value.asString());
  }

  private EntityReference readFirstEntity(JsonNode node, List<String> fieldNames) {
    for (String fieldName : fieldNames) {
      EntityReference reference = readEntity(node.path(fieldName));
      if (!reference.isEmpty()) {
        return reference;
      }
    }
    return EntityReference.EMPTY;
  }

  private EntityReference readEntity(JsonNode entityNode) {
    if (entityNode.isMissingNode() || entityNode.isNull()) {
      return EntityReference.EMPTY;
    }
    if (entityNode.isArray()) {
      for (JsonNode item : entityNode) {
        EntityReference reference = readEntity(item);
        if (!reference.isEmpty()) {
          return reference;
        }
      }
      return EntityReference.EMPTY;
    }
    if (entityNode.isObject()) {
      JsonNode data = entityNode.path("data");
      if (data.isArray() && !data.isEmpty()) {
        return readEntity(data.get(0));
      }
      String id = readOptionalText(entityNode, "id").orElse("");
      String name = readOptionalText(entityNode, "name").orElse("");
      return new EntityReference(id, name);
    }
    return new EntityReference(entityNode.asString(), entityNode.asString());
  }

  private String readPersonName(JsonNode node) {
    for (String fieldName : List.of("run_by", "assigned_to", "assignee", "owner", "executor")) {
      Optional<String> name = readPersonField(node.path(fieldName));
      if (name.isPresent()) {
        return name.get();
      }
    }
    return "";
  }

  private Optional<String> readPersonField(JsonNode personNode) {
    if (personNode.isMissingNode() || personNode.isNull()) {
      return Optional.empty();
    }
    if (personNode.isString()) {
      return Optional.of(personNode.asString());
    }
    for (String fieldName : List.of("name", "full_name", "display_name", "email", "id")) {
      Optional<String> value = readOptionalText(personNode, fieldName);
      if (value.isPresent() && !value.get().isBlank()) {
        return value;
      }
    }
    return Optional.empty();
  }

  private List<String> parseRunsInSuite(JsonNode node) {
    LinkedHashSet<String> runIds = new LinkedHashSet<>();
    collectRunIds(node.path("runs_in_suite"), runIds);
    return new ArrayList<>(runIds);
  }

  private void collectRunIds(JsonNode node, Set<String> runIds) {
    if (node.isMissingNode() || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        collectRunIds(item, runIds);
      }
    } else if (node.isObject()) {
      JsonNode id = node.path("id");
      if (!id.isMissingNode() && !id.isNull()) {
        runIds.add(id.asString());
      }
      collectRunIds(node.path("data"), runIds);
      collectRunIds(node.path("run"), runIds);
    } else if (node.isString() || node.isNumber()) {
      runIds.add(node.asString());
    }
  }

  private interface RequestFactory {
    HttpRequest create();
  }

  private record StringResponse(
      int statusCode, HttpRequest request, HttpHeaders headers, String body) {}

  private static final class EntityReference {
    private static final EntityReference EMPTY = new EntityReference("", "");

    private final String id;
    private final String name;

    private EntityReference(String id, String name) {
      this.id = Util.trimToEmpty(id);
      this.name = Util.trimToEmpty(name);
    }

    private boolean isEmpty() {
      return id.isEmpty() && name.isEmpty();
    }
  }
}
