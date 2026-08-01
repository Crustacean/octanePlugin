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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OctaneClientTest {
  private HttpServer server;
  private String baseUrl;
  private ExecutorService serverExecutor;

  @Before
  public void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    serverExecutor = Executors.newCachedThreadPool();
    server.setExecutor(serverExecutor);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    OctaneRequestCoordinator.resetForTests();
    OctaneSuiteTopologyCache.resetForTests();
    server.start();
  }

  @After
  public void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (serverExecutor != null) {
      serverExecutor.shutdownNow();
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
            assertTrue(URLDecoder.decode(query, StandardCharsets.UTF_8).contains("owner{id,name}"));
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\","
                    + "\"owner\":{\"data\":[{\"name\":\"Ada Owner\"}]},\"runs_in_suite\":["
                    + "{\"id\":\"101\"},{\"id\":\"102\"}]}]}");
          } else {
            String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8);
            assertTrue(decodedQuery.contains("run_by{id,name}"));
            if (decodedQuery.contains("assigned_to")) {
              json(exchange, 400, "{\"error\":\"Unknown run field assigned_to\"}");
              return;
            }
            assertFalse(decodedQuery.contains("assigned_to"));
            json(
                exchange,
                200,
                "{\"data\":["
                    + "{\"id\":\"101\",\"name\":\"one\","
                    + "\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"qa-Jenkins-agent\"}},"
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
      assertEquals("qa-Jenkins-agent", records.get(0).getRunByName());
      assertEquals("Ada Owner", records.get(0).getAssignedToName());
      assertEquals("failed", records.get(1).getStatus());
      assertEquals("Ben Tester", records.get(1).getRunByName());
      assertEquals("Ada Owner", records.get(1).getAssignedToName());
    }
  }

  @Test
  public void batchSuitePollingKeepsMixedChildRunnersUnderTheParentOwner() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":["
                    + "{\"id\":\"101\",\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"}},"
                    + "{\"id\":\"102\",\"native_status\":{\"logical_name\":\"failed\"},"
                    + "\"run_by\":{\"name\":\"Default Manual Runner\"}}]} ");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\","
                    + "\"owner\":{\"data\":[{\"name\":\"Suite Owner\"}]},"
                    + "\"runs_in_suite\":[{\"id\":\"101\"},{\"id\":\"102\"}]}]}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55");

      assertEquals(
          List.of("Jenkins Agent", "Default Manual Runner"),
          records.stream().map(record -> record.getRunByName()).toList());
      assertEquals(
          List.of("Suite Owner", "Suite Owner"),
          records.stream().map(record -> record.getAssignedToName()).toList());
    }
  }

  @Test
  public void usesParentTestOwnerWhenDirectSuiteAssignmentIsNotReturned() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":["
                    + "{\"id\":\"101\",\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"},"
                    + "\"test\":{\"id\":\"test-1\",\"owner\":{\"data\":[{"
                    + "\"email\":\"ada@example.com\"}]}}},"
                    + "{\"id\":\"102\",\"native_status\":{\"logical_name\":\"failed\"},"
                    + "\"run_by\":{\"name\":\"Default Manual Runner\"},"
                    + "\"test\":{\"id\":\"test-2\",\"owner\":{\"data\":[{"
                    + "\"email\":\"ada@example.com\"}]}}}]} ");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\","
                    + "\"runs_in_suite\":[{\"id\":\"101\"},{\"id\":\"102\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange ->
            json(
                exchange,
                200,
                "{\"id\":\"55\",\"test\":{\"owner\":{\"email\":\"ada@example.com\"}}}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55");

      assertEquals(
          List.of("ada@example.com", "ada@example.com"),
          records.stream().map(record -> record.getAssignedToName()).toList());
      assertEquals(
          List.of("Jenkins Agent", "Default Manual Runner"),
          records.stream().map(record -> record.getRunByName()).toList());
    }
  }

  @Test
  public void usesParentDefaultRunByAsSingleGroupingKey() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":["
                    + "{\"id\":\"101\",\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"},"
                    + "\"test\":{\"id\":\"test-1\",\"owner\":{\"name\":\"Ada Owner\"}}},"
                    + "{\"id\":\"102\",\"native_status\":{\"logical_name\":\"failed\"},"
                    + "\"run_by\":{\"name\":\"Default Manual Runner\"},"
                    + "\"test\":{\"id\":\"test-2\"}}]} ");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\","
                    + "\"run_by\":{\"email\":\"default.runner@example.com\"},"
                    + "\"assigned_to\":{\"email\":\"suite.owner@example.com\"},"
                    + "\"runs_in_suite\":[{\"id\":\"101\"},{\"id\":\"102\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 200, "{\"id\":\"55\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55");

      assertEquals(
          List.of("default.runner@example.com", "default.runner@example.com"),
          records.stream().map(record -> record.getAssignedToName()).toList());
      assertEquals(
          List.of("Jenkins Agent", "Default Manual Runner"),
          records.stream().map(record -> record.getRunByName()).toList());
    }
  }

  @Test
  public void preservesParentRunByWhenAutomatedChildRunByChanges() throws Exception {
    AtomicInteger childPolls = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            int childPoll = childPolls.getAndIncrement();
            String runBy =
                childPoll == 0
                    ? ",\"run_by\":{\"name\":\"Manual Runner\"}"
                    : childPoll == 1 ? "" : ",\"run_by\":{\"name\":\"Jenkins Agent\"}";
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"101\",\"native_status\":{\"logical_name\":\"passed\"}"
                    + runBy
                    + "}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\","
                    + "\"run_by\":{\"name\":\"ada@example.com\"},"
                    + "\"runs_in_suite\":[{\"id\":\"101\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 200, "{\"id\":\"55\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      RunRecord planned =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55").get(0);
      RunRecord pickedUp =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55").get(0);
      RunRecord automated =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55").get(0);

      assertEquals("Manual Runner", planned.getRunByName());
      assertEquals("ada@example.com", planned.getAssignedToName());
      assertEquals("", pickedUp.getRunByName());
      assertEquals("ada@example.com", pickedUp.getAssignedToName());
      assertEquals("Jenkins Agent", automated.getRunByName());
      assertEquals("ada@example.com", automated.getAssignedToName());
    }
  }

  @Test
  public void preservesChildTestOwnerWhenParentAssignmentIsUnavailable() throws Exception {
    AtomicInteger childPolls = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            String testOwner =
                childPolls.getAndIncrement() == 0
                    ? ",\"test\":{\"id\":\"test-1\",\"owner\":{\"email\":\"ada@example.com\"}}"
                    : ",\"test\":{\"id\":\"test-1\"}";
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"101\",\"native_status\":{\"logical_name\":\"running\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"}"
                    + testOwner
                    + "}]}");
          } else {
            json(
                exchange, 200, "{\"data\":[{\"id\":\"55\",\"runs_in_suite\":[{\"id\":\"101\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 200, "{\"id\":\"55\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      RunRecord assigned =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55").get(0);
      RunRecord automated =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55")).get("55").get(0);

      assertEquals("ada@example.com", assigned.getAssignedToName());
      assertEquals("ada@example.com", automated.getAssignedToName());
      assertEquals("Jenkins Agent", automated.getRunByName());
    }
  }

  @Test
  public void locksInitialParentAssignmentAcrossParentAndChildStateChanges() throws Exception {
    AtomicInteger parentPolls = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"101\","
                    + "\"native_status\":{\"logical_name\":\"running\"}}]}");
          } else {
            String owner =
                parentPolls.getAndIncrement() == 0 ? "ada@example.com" : "bob@example.com";
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"56\",\"assigned_to\":{\"email\":\""
                    + owner
                    + "\"},\"runs_in_suite\":[{\"id\":\"101\"}]}]}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      RunRecord firstPoll = client.fetchSuiteChildRuns("1001", "2002", "56").get(0);
      RunRecord secondPoll = client.fetchSuiteChildRuns("1001", "2002", "56").get(0);

      assertEquals("ada@example.com", firstPoll.getAssignedToName());
      assertEquals("ada@example.com", secondPoll.getAssignedToName());
    }
  }

  @Test
  public void doesNotLockUnassignedBeforeParentAssignmentBecomesAvailable() throws Exception {
    AtomicInteger parentPolls = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"101\",\"native_status\":{\"logical_name\":\"running\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"}}]}");
          } else {
            String assignment =
                parentPolls.getAndIncrement() == 0
                    ? ""
                    : ",\"run_by\":{\"name\":\"ada@example.com\"}";
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"57\""
                    + assignment
                    + ",\"runs_in_suite\":[{\"id\":\"101\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/57",
        exchange -> json(exchange, 200, "{\"id\":\"57\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      RunRecord unresolved = client.fetchSuiteChildRuns("1001", "2002", "57").get(0);
      RunRecord assigned = client.fetchSuiteChildRuns("1001", "2002", "57").get(0);

      assertEquals("Unassigned (57)", unresolved.getAssignedToName());
      assertEquals("ada@example.com", assigned.getAssignedToName());
    }
  }

  @Test
  public void usesParentRunByWhenOwnerFieldsAreUnsupported() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          if (query.contains("owner{id,name}")) {
            json(exchange, 400, "{\"error\":\"Unknown run field owner\"}");
          } else if (idsFromQuery(exchange).contains("101")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"101\",\"name\":\"child\","
                    + "\"native_status\":{\"logical_name\":\"passed\"},"
                    + "\"run_by\":{\"name\":\"Jenkins Agent\"}}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\","
                    + "\"run_by\":{\"name\":\"Parent Owner\"},"
                    + "\"runs_in_suite\":[{\"id\":\"101\"}]}]}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<RunRecord> records = client.fetchSuiteChildRuns("1001", "2002", "55");

      assertEquals(1, records.size());
      assertEquals("Jenkins Agent", records.get(0).getRunByName());
      assertEquals("Parent Owner", records.get(0).getAssignedToName());
    }
  }

  @Test
  public void discoversSuiteRunsByReleaseAndSprintWithEscapedNames() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          requests.incrementAndGet();
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          assertTrue(query.contains("fields=id"));
          assertTrue(query.contains("test EQ {subtype EQ ^test_suite^}"));
          assertTrue(query.contains("release EQ {name EQ ^Release \\^2.4^}"));
          assertTrue(query.contains("sprint EQ {name EQ ^Sprint 3^}"));
          json(exchange, 200, "{\"data\":[{\"id\":\"55\"},{\"id\":\"56\"}]}");
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      assertEquals(
          List.of("55", "56"),
          client.fetchSuiteRunIdsByReleaseAndSprint("1001", "2002", "Release ^2.4", "Sprint 3"));
      assertEquals(1, requests.get());
    }
  }

  @Test
  public void tolerantSuitePollingOmitsConfirmedMissingRuns() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          if (query.contains("id EQ 55") && query.contains("id EQ 99")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"name\":\"suite\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/99",
        exchange -> json(exchange, 404, "{\"description\":\"HTTP 404 Not Found\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      Map<String, List<RunRecord>> runs =
          client.fetchAvailableSuiteChildRuns("1001", "2002", List.of("55", "99"));

      assertEquals(List.of("55"), List.copyOf(runs.keySet()));
      assertEquals(1, runs.get("55").size());
    }
  }

  @Test
  public void repeatedDiscoveryAllowsSuitePoolTotalsToRiseAndFall() throws Exception {
    AtomicInteger discoveryPoll = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          if (query.contains("release EQ")) {
            int poll = discoveryPoll.incrementAndGet();
            if (poll == 1) {
              json(exchange, 200, "{\"data\":[{\"id\":\"55\"}]}");
            } else if (poll == 2) {
              json(exchange, 200, "{\"data\":[{\"id\":\"55\"},{\"id\":\"56\"}]}");
            } else {
              json(exchange, 200, "{\"data\":[{\"id\":\"56\"}]}");
            }
            return;
          }
          List<String> ids = idsFromQuery(exchange);
          StringBuilder body = new StringBuilder("{\"data\":[");
          for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
              body.append(',');
            }
            body.append("{\"id\":\"")
                .append(ids.get(index))
                .append("\",\"native_status\":{\"logical_name\":\"planned\"}}");
          }
          json(exchange, 200, body.append("]}").toString());
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      assertEquals(List.of("55"), discoverAvailableIds(client));
      assertEquals(List.of("55", "56"), discoverAvailableIds(client));
      assertEquals(List.of("56"), discoverAvailableIds(client));
    }
  }

  @Test
  public void releaseDiscoveryFindsNewSuitesAfterTheSelectionWasEmpty() throws Exception {
    AtomicInteger discoveryPoll = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          if (query.contains("release EQ")) {
            int poll = discoveryPoll.incrementAndGet();
            if (poll == 1) {
              json(exchange, 200, "{\"data\":[{\"id\":\"55\"}]}");
            } else if (poll == 2) {
              json(exchange, 200, "{\"data\":[]}");
            } else {
              json(exchange, 200, "{\"data\":[{\"id\":\"56\"}]}");
            }
            return;
          }
          List<String> ids = idsFromQuery(exchange);
          StringBuilder body = new StringBuilder("{\"data\":[");
          for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
              body.append(',');
            }
            body.append("{\"id\":\"")
                .append(ids.get(index))
                .append("\",\"native_status\":{\"logical_name\":\"planned\"}}");
          }
          json(exchange, 200, body.append("]}").toString());
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      assertEquals(List.of("55"), discoverAvailableIds(client));
      assertEquals(List.of(), discoverAvailableIds(client));
      assertEquals(List.of("56"), discoverAvailableIds(client));
    }
  }

  @Test
  public void rejectsUnsafeEntityIdsBeforeBuildingOctaneQueries() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      try {
        client.fetchSuiteChildRuns("1001", "2002", "55) OR (id GT 0");
      } catch (IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("unsafe entity ID"));
        return;
      }
    }
    throw new AssertionError("Expected an unsafe entity ID to be rejected.");
  }

  @Test
  public void stopsPaginationWhenAFullPageMakesNoRequestedIdProgress() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          requests.incrementAndGet();
          StringBuilder body = new StringBuilder("{\"data\":[");
          for (int index = 0; index < 200; index++) {
            if (index > 0) {
              body.append(',');
            }
            body.append("{\"id\":\"unrelated-")
                .append(index)
                .append("\",\"native_status\":{\"logical_name\":\"passed\"}}");
          }
          body.append("]}");
          json(exchange, 200, body.toString());
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      assertTrue(client.fetchScopedRuns("1001", "2002", List.of("101"), "").isEmpty());
      assertEquals(1, requests.get());
    }
  }

  @Test
  public void boundsJsonResponseBodies() throws Exception {
    server.createContext(
        "/authentication/sign_in",
        exchange -> json(exchange, 200, "x".repeat(OctaneClient.MAX_JSON_RESPONSE_BYTES + 1)));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      try {
        client.authenticate();
      } catch (IOException e) {
        assertTrue(e.getMessage().contains("byte safety limit"));
        return;
      }
    }
    throw new AssertionError("Expected an oversized response body to be rejected.");
  }

  @Test
  public void redactsSensitiveValuesFromOctaneFailureMessages() throws Exception {
    server.createContext(
        "/authentication/sign_in",
        exchange ->
            json(
                exchange,
                401,
                "{\"client_secret\":\"server-echoed-secret\",\"message\":\"denied\"}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "request-secret")) {
      try {
        client.authenticate();
      } catch (AbortException e) {
        assertFalse(e.getMessage().contains("server-echoed-secret"));
        assertTrue(e.getMessage().contains("***"));
        return;
      }
    }
    throw new AssertionError("Expected authentication to fail.");
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
  public void doesNotMisreportFallbackFailureAsMissingBecauseRunsEndpointWasNotFound()
      throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> json(exchange, 404, "{\"error\":\"runs endpoint unavailable\"}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/suite_runs/55",
        exchange -> json(exchange, 400, "{\"error\":\"unsupported suite fields\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      try {
        client.fetchSuiteChildRuns("1001", "2002", "55");
      } catch (AbortException e) {
        assertTrue(e.getMessage().contains("Runs collection lookup failed"));
        assertTrue(e.getMessage().contains("runs endpoint unavailable"));
        assertTrue(e.getMessage().contains("unsupported suite fields"));
        return;
      }
    }
    throw new AssertionError("Expected suite run lookup to fail.");
  }

  @Test
  public void topologyCacheKeepsIdenticalSuiteIdsIsolatedByWorkspace() throws Exception {
    AtomicInteger workspaceOneRequests = new AtomicInteger();
    AtomicInteger workspaceTwoRequests = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2001/runs",
        exchange -> {
          workspaceOneRequests.incrementAndGet();
          if (idsFromQuery(exchange).contains("run-1")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"run-1\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"owner\":{\"name\":\"Owner One\"},"
                    + "\"runs_in_suite\":[{\"id\":\"run-1\"}]}]}");
          }
        });
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          workspaceTwoRequests.incrementAndGet();
          if (idsFromQuery(exchange).contains("run-2")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"run-2\","
                    + "\"native_status\":{\"logical_name\":\"passed\"}}]}");
          } else {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"55\",\"owner\":{\"name\":\"Owner Two\"},"
                    + "\"runs_in_suite\":[{\"id\":\"run-2\"}]}]}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();

      Map<String, List<RunRecord>> first =
          client.fetchSuiteChildRuns("1001", "2001", List.of("55"));
      Map<String, List<RunRecord>> second =
          client.fetchSuiteChildRuns("1001", "2002", List.of("55"));

      assertEquals("run-1", first.get("55").get(0).getId());
      assertEquals("run-2", second.get("55").get(0).getId());
      assertEquals("Owner One", first.get("55").get(0).getAssignedToName());
      assertEquals("Owner Two", second.get("55").get(0).getAssignedToName());
      assertTrue(workspaceOneRequests.get() > 0);
      assertTrue(workspaceTwoRequests.get() > 0);
    }
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

  @Test
  public void ignoresOnlyUnsupportedDefectRelationsAndKeepsSupportedResults() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/defects",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          if (query.contains("run EQ")) {
            json(
                exchange,
                200,
                "{\"data\":[{\"id\":\"901\",\"name\":\"linked defect\","
                    + "\"severity\":{\"logical_name\":\"high\"},"
                    + "\"phase\":{\"logical_name\":\"opened\"}}]}");
          } else {
            json(exchange, 400, "{\"error\":\"platform.unknown_field\"}");
          }
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));
    Map<String, List<RunRecord>> suiteRuns =
        Map.of(
            "suite-1",
            List.of(new RunRecord("run-1", "child", "failed", "Tester", "test-1", "Test", "", "")));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      List<DefectRecord> defects = client.fetchLinkedDefects("1001", "2002", suiteRuns, "", 100);

      assertEquals(1, defects.size());
      assertEquals("901", defects.get(0).getId());
    }
  }

  @Test
  public void propagatesDefectQueryFailuresInsteadOfReturningPartialData() throws Exception {
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/defects",
        exchange -> json(exchange, 400, "{\"error\":\"query failed\"}"));
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));
    Map<String, List<RunRecord>> suiteRuns =
        Map.of(
            "suite-1",
            List.of(new RunRecord("run-1", "child", "failed", "Tester", "test-1", "Test", "", "")));

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      try {
        client.fetchLinkedDefects("1001", "2002", suiteRuns, "", 100);
      } catch (IOException e) {
        assertTrue(e.getMessage().contains("HTTP 400"));
        assertTrue(e.getMessage().contains("query failed"));
        return;
      }
    }
    throw new AssertionError("Expected the failed defect query to fail the poll.");
  }

  @Test
  public void bulkFetchesFiveHundredSuiteTopologiesWithBoundedFanoutAndCacheReuse()
      throws Exception {
    AtomicInteger topologyRequests = new AtomicInteger();
    AtomicInteger childRequests = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          List<String> ids = idsFromQuery(exchange);
          boolean topology = !ids.isEmpty() && ids.get(0).startsWith("suite-");
          if (topology) {
            topologyRequests.incrementAndGet();
          } else {
            childRequests.incrementAndGet();
          }
          StringBuilder body = new StringBuilder("{\"data\":[");
          for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
              body.append(',');
            }
            String id = ids.get(index);
            body.append("{\"id\":\"").append(id).append("\",\"name\":\"").append(id).append('"');
            if (topology) {
              body.append(",\"runs_in_suite\":[{\"id\":\"run-")
                  .append(id.substring("suite-".length()))
                  .append("\"}]");
            } else {
              body.append(",\"native_status\":{\"logical_name\":\"passed\"}");
            }
            body.append('}');
          }
          body.append("]}");
          json(exchange, 200, body.toString());
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));
    List<String> suiteIds = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      suiteIds.add("suite-" + index);
    }

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      Map<String, List<RunRecord>> first = client.fetchSuiteChildRuns("1001", "2002", suiteIds);
      int topologyAfterFirstFetch = topologyRequests.get();
      Map<String, List<RunRecord>> second = client.fetchSuiteChildRuns("1001", "2002", suiteIds);

      assertEquals(500, first.size());
      assertEquals("run-499", first.get("suite-499").get(0).getId());
      assertEquals(500, second.size());
      assertEquals(13, topologyAfterFirstFetch);
      assertEquals(topologyAfterFirstFetch, topologyRequests.get());
      assertEquals(26, childRequests.get());
      assertTrue(topologyRequests.get() + childRequests.get() <= 39);
      assertTrue(OctaneSuiteTopologyCache.metrics().hits() >= 500L);
    }
  }

  @Test
  public void cutsFiveHundredSuiteFiftyChildRunFanoutByMoreThanHalf() throws Exception {
    AtomicInteger topologyRequests = new AtomicInteger();
    AtomicInteger childRequests = new AtomicInteger();
    server.createContext("/authentication/sign_in", exchange -> json(exchange, 200, "{}"));
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          try {
            Thread.sleep(2L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          List<String> ids = idsFromQuery(exchange);
          boolean topology = !ids.isEmpty() && ids.get(0).startsWith("suite-");
          if (topology) {
            topologyRequests.incrementAndGet();
          } else {
            childRequests.incrementAndGet();
          }
          StringBuilder body = new StringBuilder("{\"data\":[");
          for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
              body.append(',');
            }
            String id = ids.get(index);
            body.append("{\"id\":\"").append(id).append("\",\"name\":\"").append(id).append('"');
            if (topology) {
              body.append(",\"runs_in_suite\":[");
              for (int child = 0; child < 50; child++) {
                if (child > 0) {
                  body.append(',');
                }
                body.append("{\"id\":\"run-")
                    .append(id.substring("suite-".length()))
                    .append('-')
                    .append(child)
                    .append("\"}");
              }
              body.append(']');
            } else {
              body.append(",\"native_status\":{\"logical_name\":\"passed\"}");
            }
            body.append('}');
          }
          body.append("]}");
          json(exchange, 200, body.toString());
        });
    server.createContext("/authentication/sign_out", exchange -> json(exchange, 200, "{}"));
    List<String> suiteIds = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      suiteIds.add("suite-" + index);
    }

    try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
      client.authenticate();
      Map<String, List<RunRecord>> runs = client.fetchSuiteChildRuns("1001", "2002", suiteIds);

      assertEquals(500, runs.size());
      assertEquals(50, runs.get("suite-499").size());
      assertEquals(13, topologyRequests.get());
      assertEquals(625, childRequests.get());
      int legacySuiteAndChildRequests = 1_500;
      int currentSuiteAndChildRequests = topologyRequests.get() + childRequests.get();
      assertTrue(currentSuiteAndChildRequests * 2 < legacySuiteAndChildRequests);
      System.out.printf(
          "Octane request acceptance: suites=500 childRunsPerSuite=50 legacyRequests=%d "
              + "currentRequests=%d%n",
          legacySuiteAndChildRequests, currentSuiteAndChildRequests);
    }
  }

  @Test
  public void capsTwentyConcurrentRequestsAtEightPerServer() throws Exception {
    AtomicInteger workspaceRequests = new AtomicInteger();
    AtomicInteger signOutRequests = new AtomicInteger();
    server.createContext(
        "/api/shared_spaces/1001/workspaces/2002/runs",
        exchange -> {
          workspaceRequests.incrementAndGet();
          try {
            Thread.sleep(100L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          json(exchange, 200, "{\"data\":[]}");
        });
    server.createContext(
        "/authentication/sign_out",
        exchange -> {
          signOutRequests.incrementAndGet();
          json(exchange, 200, "{}");
        });
    OctaneRequestCoordinator.resetForTests();
    List<Future<Integer>> futures = new ArrayList<>();
    try (ExecutorService clients = Executors.newFixedThreadPool(20)) {
      for (int index = 0; index < 20; index++) {
        futures.add(
            clients.submit(
                () -> {
                  try (OctaneClient client = new OctaneClient(baseUrl, "client", "secret")) {
                    return client.testWorkspaceAccess("1001", "2002");
                  }
                }));
      }
      for (Future<Integer> future : futures) {
        assertEquals(200, future.get(10, TimeUnit.SECONDS).intValue());
      }
    }

    OctaneRequestCoordinator.Metrics metrics = OctaneRequestCoordinator.metrics(baseUrl);
    assertEquals(20, workspaceRequests.get());
    assertEquals(20, signOutRequests.get());
    assertEquals(40L, metrics.requests());
    assertTrue(metrics.maximumInFlight() > 1);
    assertTrue(metrics.maximumInFlight() <= OctaneRequestCoordinator.DEFAULT_MAX_IN_FLIGHT);
    assertEquals(0, metrics.inFlight());
    System.out.printf(
        "Octane concurrency acceptance: requests=%d maxInFlight=%d%n",
        metrics.requests(), metrics.maximumInFlight());
  }

  private List<String> idsFromQuery(HttpExchange exchange) {
    String query =
        URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
    Matcher matcher = Pattern.compile("id EQ ([^|;)\\\"]+)").matcher(query);
    List<String> ids = new ArrayList<>();
    while (matcher.find()) {
      ids.add(matcher.group(1).trim());
    }
    return ids;
  }

  private List<String> discoverAvailableIds(OctaneClient client) throws Exception {
    List<String> discovered =
        client.fetchSuiteRunIdsByReleaseAndSprint("1001", "2002", "Release 2.4", "Sprint 3");
    return List.copyOf(client.fetchAvailableSuiteChildRuns("1001", "2002", discovered).keySet());
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
