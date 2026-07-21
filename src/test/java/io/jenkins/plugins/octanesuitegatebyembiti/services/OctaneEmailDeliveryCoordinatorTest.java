package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class OctaneEmailDeliveryCoordinatorTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void serializesSameBuildWorkspaceAndReleasesRegistryEntry() throws Exception {
    FreeStyleProject project = jenkins.createFreeStyleProject();
    FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
    FilePath workspace = jenkins.jenkins.getWorkspaceFor(project);
    CountDownLatch attempted = new CountDownLatch(1);
    CountDownLatch acquired = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      try (OctaneEmailDeliveryCoordinator.Lease ignored =
          OctaneEmailDeliveryCoordinator.acquire(build, workspace)) {
        Future<?> waiter =
            executor.submit(
                () -> {
                  attempted.countDown();
                  try (OctaneEmailDeliveryCoordinator.Lease second =
                      OctaneEmailDeliveryCoordinator.acquire(build, workspace)) {
                    acquired.countDown();
                  }
                  return null;
                });
        assertTrue(attempted.await(2, TimeUnit.SECONDS));
        assertFalse(acquired.await(150, TimeUnit.MILLISECONDS));
        assertFalse(waiter.isDone());
      }
      assertTrue(acquired.await(2, TimeUnit.SECONDS));
    }

    assertEquals(0, OctaneEmailDeliveryCoordinator.activeEntryCount());
  }
}
