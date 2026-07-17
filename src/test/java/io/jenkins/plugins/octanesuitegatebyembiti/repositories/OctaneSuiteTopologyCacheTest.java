package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OctaneSuiteTopologyCacheTest {
  @Before
  public void resetBeforeTest() {
    OctaneSuiteTopologyCache.resetForTests();
  }

  @After
  public void resetAfterTest() {
    OctaneSuiteTopologyCache.resetForTests();
  }

  @Test
  public void coalescesConcurrentLoadsForTheSameSuites() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch loaderStarted = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Map<String, List<String>>> first =
          executor.submit(
              () ->
                  OctaneSuiteTopologyCache.getAll(
                      "server/workspace",
                      List.of("suite-1"),
                      ids -> {
                        loads.incrementAndGet();
                        loaderStarted.countDown();
                        assertTrue(releaseLoader.await(5, TimeUnit.SECONDS));
                        return Map.of("suite-1", List.of("run-1"));
                      }));
      assertTrue(loaderStarted.await(5, TimeUnit.SECONDS));
      Future<Map<String, List<String>>> second =
          executor.submit(
              () ->
                  OctaneSuiteTopologyCache.getAll(
                      "server/workspace",
                      List.of("suite-1"),
                      ids -> {
                        loads.incrementAndGet();
                        return Map.of("suite-1", List.of("duplicate"));
                      }));

      releaseLoader.countDown();

      assertEquals(List.of("run-1"), first.get(5, TimeUnit.SECONDS).get("suite-1"));
      assertEquals(List.of("run-1"), second.get(5, TimeUnit.SECONDS).get("suite-1"));
      assertEquals(1, loads.get());
    }
  }

  @Test
  public void allowsDisjointSuiteLoadsInTheSameWorkspaceToRunConcurrently() throws Exception {
    CountDownLatch bothLoadersStarted = new CountDownLatch(2);
    CountDownLatch releaseLoaders = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Map<String, List<String>>> first =
          executor.submit(
              () -> loadAfterBarrier("suite-1", "run-1", bothLoadersStarted, releaseLoaders));
      Future<Map<String, List<String>>> second =
          executor.submit(
              () -> loadAfterBarrier("suite-2", "run-2", bothLoadersStarted, releaseLoaders));

      assertTrue(bothLoadersStarted.await(5, TimeUnit.SECONDS));
      releaseLoaders.countDown();

      assertEquals(List.of("run-1"), first.get(5, TimeUnit.SECONDS).get("suite-1"));
      assertEquals(List.of("run-2"), second.get(5, TimeUnit.SECONDS).get("suite-2"));
    }
  }

  private Map<String, List<String>> loadAfterBarrier(
      String suiteId,
      String runId,
      CountDownLatch bothLoadersStarted,
      CountDownLatch releaseLoaders)
      throws Exception {
    return OctaneSuiteTopologyCache.getAll(
        "server/workspace",
        List.of(suiteId),
        ids -> {
          bothLoadersStarted.countDown();
          assertTrue(releaseLoaders.await(5, TimeUnit.SECONDS));
          return Map.of(suiteId, List.of(runId));
        });
  }
}
