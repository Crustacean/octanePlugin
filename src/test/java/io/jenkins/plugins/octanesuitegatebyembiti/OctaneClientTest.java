package io.jenkins.plugins.octanesuitegatebyembiti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
                    + "\"native_status\":{\"logical_name\":\"passed\"}},"
                    + "{\"id\":\"102\",\"name\":\"two\","
                    + "\"native_status\":{\"logical_name\":\"failed\"}}"
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
      assertEquals("failed", records.get(1).getStatus());
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
        "/api/shared_spaces/1001/workspaces/2002/suite_run/55",
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
