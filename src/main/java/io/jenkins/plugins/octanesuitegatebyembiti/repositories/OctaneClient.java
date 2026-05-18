package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.AbortException;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OctaneClient implements AutoCloseable {
  private static final int PAGE_SIZE = 200;
  private static final int QUERY_CHUNK_SIZE = 40;
  private static final int MAX_ATTEMPTS = 3;
  private static final int RESPONSE_BODY_LIMIT = 1_000;
  private static final String TECH_PREVIEW_HEADER = "ALM-OCTANE-TECH-PREVIEW";
  private static final String RUN_FIELDS =
      "id,name,native_status{logical_name,name},status{logical_name,name},run_by{id,name},"
          + "test{id,name},product_areas{id,name},runs_in_suite";
  private static final String DEFECT_FIELDS =
      "id,name,severity{logical_name,name},priority{logical_name,name},phase{logical_name,name},"
          + "run{id,name},test{id,name},product_areas{id,name}";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String baseUrl;
  private final String clientId;
  private final String clientSecret;
  private String cookieHeader = "";

  public OctaneClient(String baseUrl, String clientId, String clientSecret) {
    this(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
        baseUrl,
        clientId,
        clientSecret);
  }

  public OctaneClient(HttpClient httpClient, String baseUrl, String clientId, String clientSecret) {
    this.httpClient = httpClient;
    this.baseUrl = Util.trimTrailingSlash(baseUrl);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public void authenticate() throws IOException, InterruptedException {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("client_id", clientId);
    payload.put("client_secret", clientSecret);

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/authentication/sign_in"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new AbortException(
          "ALM Octane authentication failed with HTTP "
              + response.statusCode()
              + " for "
              + request.uri()
              + responseBodyMessage(response.body()));
    }
    rememberCookies(response);
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
      return List.of(suiteRun.path("id").asText(suiteRunId));
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
    List<String> clauses = buildLinkedDefectClauses(suiteRuns);
    if (clauses.isEmpty()) {
      return List.of();
    }

    List<DefectRecord> records = new ArrayList<>();
    for (int start = 0; start < clauses.size() && records.size() < maxDefects; start += QUERY_CHUNK_SIZE) {
      int end = Math.min(start + QUERY_CHUNK_SIZE, clauses.size());
      records.addAll(
          fetchDefectsChunk(
              sharedSpaceId,
              workspaceId,
              clauses.subList(start, end),
              defectQuery,
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
        httpClient.send(
            requestBuilder(baseUrl + path).GET().build(), HttpResponse.BodyHandlers.discarding());
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
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while signing out of ALM Octane.", e);
    }
  }

  private JsonNode fetchSuiteRun(String sharedSpaceId, String workspaceId, String suiteRunId)
      throws IOException, InterruptedException {
    String query = "id EQ " + suiteRunId;
    String path =
        workspacePath(sharedSpaceId, workspaceId)
            + "/runs?"
            + parameter("query", quote(query))
            + "&"
            + parameter("fields", RUN_FIELDS)
            + "&"
            + parameter("limit", "1");
    IOException runsLookupFailure = null;
    try {
      JsonNode collection = getJson(path);
      JsonNode data = collection.path("data");
      if (data.isArray() && !data.isEmpty()) {
        return data.get(0);
      }
    } catch (IOException e) {
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
    throw new AbortException("ALM Octane suite run was not found: " + suiteRunId);
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

    List<RunRecord> records = new ArrayList<>();
    int offset = 0;
    while (true) {
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
      for (JsonNode node : data) {
        records.add(parseRun(node));
      }
      if (data.size() < PAGE_SIZE) {
        break;
      }
      offset += PAGE_SIZE;
    }
    return records;
  }

  private String buildIdQuery(List<String> runIds) {
    List<String> clauses = new ArrayList<>();
    for (String runId : runIds) {
      clauses.add("id EQ " + runId);
    }
    return String.join("||", clauses);
  }

  private List<String> buildLinkedDefectClauses(Map<String, List<RunRecord>> suiteRuns) {
    LinkedHashSet<String> clauses = new LinkedHashSet<>();
    for (List<RunRecord> runs : suiteRuns.values()) {
      for (RunRecord run : runs) {
        if (!Util.isBlank(run.getId())) {
          clauses.add("run EQ {id EQ " + run.getId() + "}");
        }
        if (!Util.isBlank(run.getTestId())) {
          clauses.add("test EQ {id EQ " + run.getTestId() + "}");
        }
      }
    }
    return new ArrayList<>(clauses);
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
      String path =
          workspacePath(sharedSpaceId, workspaceId)
              + "/defects?"
              + parameter("query", quote(query))
              + "&"
              + parameter("fields", DEFECT_FIELDS)
              + "&"
              + parameter("limit", Integer.toString(limit))
              + "&"
              + parameter("offset", Integer.toString(offset));
      JsonNode collection = getJson(path);
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

  private JsonNode getJson(String path) throws IOException, InterruptedException {
    HttpResponse<String> response =
        sendWithRetry(() -> requestBuilder(baseUrl + path).GET().build());
    try {
      return objectMapper.readTree(response.body());
    } catch (IOException e) {
      throw new IOException(
          "ALM Octane returned malformed JSON for "
              + response.request().uri()
              + responseBodyMessage(response.body()),
          e);
    }
  }

  private HttpResponse<String> sendWithRetry(RequestFactory requestFactory)
      throws IOException, InterruptedException {
    IOException lastException = null;
    HttpRequest lastRequest = null;
    HttpResponse<String> lastResponse = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      HttpRequest request = requestFactory.create();
      lastRequest = request;
      HttpResponse<String> response;
      try {
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (IOException e) {
        lastException = e;
        pauseBeforeRetry(attempt);
        continue;
      }
      rememberCookies(response);
      if (response.statusCode() == 401 && attempt == 1) {
        authenticate();
        continue;
      }
      if (response.statusCode() == 429 || response.statusCode() >= 500) {
        lastResponse = response;
        pauseBeforeRetry(attempt);
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
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .header(TECH_PREVIEW_HEADER, "true");
    if (!cookieHeader.isEmpty()) {
      builder.header("Cookie", cookieHeader);
    }
    return builder;
  }

  private AbortException requestFailure(HttpRequest request, HttpResponse<String> response) {
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
    String normalized = body.replaceAll("\\s+", " ").trim();
    if (normalized.length() > RESPONSE_BODY_LIMIT) {
      normalized = normalized.substring(0, RESPONSE_BODY_LIMIT) + "...";
    }
    return ". Response body: " + normalized;
  }

  private void rememberCookies(HttpResponse<?> response) {
    List<String> cookies = response.headers().allValues("Set-Cookie");
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

  private void pauseBeforeRetry(int attempt) throws InterruptedException {
    if (attempt >= MAX_ATTEMPTS) {
      return;
    }
    Thread.sleep(500L * attempt);
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

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private RunRecord parseRun(JsonNode node) {
    String status = readStatus(node.path("native_status"));
    if (status.isEmpty()) {
      status = readStatus(node.path("status"));
    }
    EntityReference test = readEntity(node.path("test"));
    EntityReference project = readFirstEntity(node, List.of("product_areas", "product_area"));
    return new RunRecord(
        node.path("id").asText(),
        node.path("name").asText(),
        status,
        readPersonName(node),
        test.id,
        test.name,
        project.id,
        project.name);
  }

  private DefectRecord parseDefect(JsonNode node) {
    EntityReference run = readEntity(node.path("run"));
    EntityReference test = readEntity(node.path("test"));
    EntityReference project = readFirstEntity(node, List.of("product_areas", "product_area"));
    return new DefectRecord(
        node.path("id").asText(),
        node.path("name").asText(),
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
    if (statusNode.isTextual()) {
      return statusNode.asText();
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
    return Optional.of(value.asText());
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
    return new EntityReference(entityNode.asText(), entityNode.asText());
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
    if (personNode.isTextual()) {
      return Optional.of(personNode.asText());
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
        runIds.add(id.asText());
      }
      collectRunIds(node.path("data"), runIds);
      collectRunIds(node.path("run"), runIds);
    } else if (node.isTextual() || node.isNumber()) {
      runIds.add(node.asText());
    }
  }

  private interface RequestFactory {
    HttpRequest create();
  }

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
