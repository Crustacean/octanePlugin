package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.AbortException;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.DefectRecord;
import io.jenkins.plugins.octanesuitegatebyembiti.entities.RunRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OctaneClientTest {
  private HttpServer server;
  private String baseUrl;

  @Before
  public void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
  }

  @After
  public void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  public void authenticatesAndFetchesSuiteChildRuns() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query = exchange.getRequestURI().getRawQuery();
          assertTrue(exchange.getRequestHeaders().getFirst("Cookie").contains("LWSSO_COOKIE_KEY"));
          if (query.contains("limit=1")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\",\"runs_in_suite\":["
                    + "{\"id\":\"101\"},{\"id\":\"102\"}]}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":["
                    + "{\"id\":\"101\",\"name\":\"one\","
                    + "\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"Ada Tester\"}},"
                    + "{\"id\":\"102\",\"name\":\"two\","
                    + "\"native_status\":{\"logical_name\":\"failed\"},"
                    + "\"run_by\":{\"name\":\"Ben Tester\"}}"
                    + "]}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", "55");

      assertEquals(2, records.size());
      assertEquals("101", records.get(0).getId());
      assertEquals("passed", records.get(0).getStatus());
      assertEquals("Ada Tester", records.get(0).getRunByName());
      assertEquals("failed", records.get(1).getStatus());
      assertEquals("Ben Tester", records.get(1).getRunByName());
    }
  }

  @Test
  public void reAuthenticatesOnceAfterUnauthorizedResponse() throws Exception {
    AtomicInteger authCount = new AtomicInteger();
    AtomicInteger runCount = new AtomicInteger();
    server.createContext(
        "/authentication/sign_in",
        exchange -> {
          authCount.incrementAndGet();
          json(exchange, 200, "{}");
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          int count = runCount.incrementAndGet();
          if (count == 1) {
            json(exchange, 401, "{\"error\":\"expired\"}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange ->
            json(
                exchange,
                200,
                "{\"id\":\"55\",\"name\":\"suite\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", "55");

      assertEquals(2, authCount.get());
      assertEquals(1, records.size());
      assertEquals("passed", records.get(0).getStatus());
    }
  }

  @Test
  public void testsWorkspaceAccessWithAuthenticatedCookie() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          assertEquals("fields=id&limit=1", exchange.getRequestURI().getRawQuery());
          assertTrue(exchange.getRequestHeaders().getFirst("Cookie").contains("LWSSO_COOKIE_KEY"));
          assertEquals("true", exchange.getRequestHeaders().getFirst("ALM-OCTANE-TECH-PREVIEW"));
          json(exchange, 200, "{\"data\":[]}");
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      assertEquals(200, client.testWorkspaceAccess("1001", "2002"));
    }
  }

  @Test
  public void fallsBackToPluralSuiteRunsEndpointWithTechnicalPreviewHeader() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> json(exchange, 400, "{\"error\":\"unsupported runs query\"}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> {
          assertEquals("true", exchange.getRequestHeaders().getFirst("ALM-OCTANE-TECH-PREVIEW"));
          json(
              exchange,
              200,
              "{\"id\":\"55\",\"name\":\"suite\","
                  + "\"native_status\":{\"logical_name\":\"passed\"}}");
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", "55");

      assertEquals(1, records.size());
      assertEquals("55", records.get(0).getId());
      assertEquals("passed", records.get(0).getStatus());
    }
  }

  @Test
  public void reportsSuiteRunLookupRequestDetails() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> json(exchange, 400, "{\"error\":\"bad runs query\"}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 400, "{\"error\":\"bad suite run\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      try {
        client.fetchSuiteChildRuns("1001", "2002", "55");
      } catch (AbortException e) {
        String message = e.getMessage();
        assertTrue(message.contains("Runs collection lookup failed"));
        assertTrue(message.contains("/api/shared_spaces/1001/workspaces/2002/runs?"));
        assertTrue(message.contains("bad runs query"));
        assertTrue(message.contains("/api/shared_spaces/1001/workspaces/2002/suite_runs/55?"));
        assertTrue(message.contains("bad suite run"));
        return;
      }
    }
    throw new AssertionError("Expected suite run lookup to fail.");
  }

  @Test
  public void reportsFriendlyMissingSuiteRunMessageForWorkspaceMismatch() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> json(exchange, 200, "{\"data\":[]}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 404, "{\"description\":\"HTTP 404 Not Found\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      try {
        client.fetchSuiteChildRuns("1001", "2002", "55");
      } catch (AbortException e) {
        String message = e.getMessage();
        assertTrue(message.contains("suite run 55"));
        assertTrue(message.contains("shared space 1001 / workspace 2002"));
        assertTrue(message.contains("match the Octane workspace"));
        assertFalse(message.contains("/api/shared_spaces/1001/workspaces/2002"));
        assertFalse(message.contains("Response body"));
        return;
      }
    }
    throw new AssertionError("Expected suite run lookup to fail.");
  }

  @Test
  public void passesMultipleProductAreaIdsThroughScopeQuery() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          assertTrue(query.contains("(id EQ 101||id EQ 102)"));
          assertTrue(query.contains("test={((product_areas={id=1004||id=1005}))}"));
          json(exchange, 200, "{\"data\":[]}");
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      List<RunRecord> records =
          client.fetchScopedRuns(
              "1001", "2002", List.of("101", "102"), "test={((product_areas={id=1004||id=1005}))}");

      assertEquals(0, records.size());
    }
  }

  @Test
  public void fetchesKnownDefectsByIdForLedgerRefresh() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/defects",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          assertTrue(query.contains("id EQ 901||id EQ 902"));
          json(
              exchange,
              200,
              "{\"data\":["
                  + "{\"id\":\"901\",\"name\":\"closed later\","
                  + "\"severity\":{\"logical_name\":\"high\"},"
                  + "\"phase\":{\"logical_name\":\"closed\"}},"
                  + "{\"id\":\"902\",\"name\":\"still open\","
                  + "\"severity\":{\"logical_name\":\"critical\"},"
                  + "\"phase\":{\"logical_name\":\"opened\"}}"
                  + "]}");
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<DefectRecord> records =
          client.fetchDefectsByIds("1001", "2002", List.of("901", "902"), 1000);

      assertEquals(2, records.size());
      assertEquals("901", records.get(0).getId());
      assertFalse(records.get(0).isOpen());
      assertEquals("902", records.get(1).getId());
      assertTrue(records.get(1).isOpen());
    }
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.getResponseHeaders().add("Set-Cookie", "LWSSO_COOKIE_KEY=test; Path=/");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream stream = exchange.getResponseBody()) {
      stream.write(bytes);
    }
  }
}
