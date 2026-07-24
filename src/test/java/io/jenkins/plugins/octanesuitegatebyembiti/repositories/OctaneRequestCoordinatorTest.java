package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OctaneRequestCoordinatorTest {
  private static final String LIMIT_PROPERTY =
      "io.jenkins.plugins.octanesuitegate.maxRequestsPerServer";

  @Before
  public void resetBeforeTest() {
    OctaneRequestCoordinator.resetForTests();
  }

  @After
  public void resetAfterTest() {
    System.clearProperty(LIMIT_PROPERTY);
    OctaneRequestCoordinator.resetForTests();
  }

  @Test(timeout = 15_000L)
  public void capsConcurrentRequestsPerServerAndReleasesEveryPermit() throws Exception {
    System.setProperty(LIMIT_PROPERTY, "2");
    CountDownLatch firstWave = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger inFlight = new AtomicInteger();
    AtomicInteger maximumInFlight = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(serverExecutor);
      server.createContext(
          "/octane",
          exchange -> {
            int current = inFlight.incrementAndGet();
            maximumInFlight.accumulateAndGet(current, Math::max);
            firstWave.countDown();
            try {
              boolean released;
              try {
                released = release.await(10L, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Test server interrupted.", e);
              }
              if (!released) {
                throw new IllegalStateException("Test server was not released.");
              }
              byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
            } finally {
              inFlight.decrementAndGet();
              exchange.close();
            }
          });
      server.start();
      URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/octane");
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder(endpoint).GET().build();
      List<Future<HttpResponse<String>>> responses = new ArrayList<>();
      for (int index = 0; index < 6; index++) {
        responses.add(
            callers.submit(
                () ->
                    OctaneRequestCoordinator.send(
                        "server-a",
                        client,
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))));
      }

      assertTrue(firstWave.await(5L, TimeUnit.SECONDS));
      Thread.sleep(100L);
      assertEquals(2, maximumInFlight.get());
      release.countDown();
      for (Future<HttpResponse<String>> response : responses) {
        assertEquals(200, response.get(5L, TimeUnit.SECONDS).statusCode());
      }

      OctaneRequestCoordinator.Metrics metrics = OctaneRequestCoordinator.metrics("server-a");
      assertEquals(6L, metrics.requests());
      assertEquals(0, metrics.inFlight());
      assertEquals(2, metrics.maximumInFlight());
    } finally {
      release.countDown();
      server.stop(0);
    }
  }
}
