package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class OctaneProgressEmailSchedulerTest {
  @Test
  public void boundsTwentyOverlappingPipelineSchedulesToFourThreads() throws Exception {
    OctaneProgressEmailScheduler scheduler =
        OctaneProgressEmailScheduler.createForTests(
            4, 64, Duration.ofMinutes(5L), Duration.ofSeconds(5L));
    CountDownLatch fourStarted = new CountDownLatch(4);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch completed = new CountDownLatch(20);
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maximumActive = new AtomicInteger();
    List<OctaneProgressEmailScheduler.Delivery> deliveries = new ArrayList<>();
    List<OctaneProgressEmailScheduler.Registration> registrations = new ArrayList<>();
    try {
      for (int pipeline = 0; pipeline < 20; pipeline++) {
        OctaneProgressEmailScheduler.Delivery delivery =
            occurrence -> {
              int current = active.incrementAndGet();
              maximumActive.accumulateAndGet(current, Math::max);
              fourStarted.countDown();
              try {
                assertTrue(release.await(5L, TimeUnit.SECONDS));
              } finally {
                active.decrementAndGet();
                completed.countDown();
              }
            };
        deliveries.add(delivery);
        registrations.add(
            scheduler.schedule(
                "pipeline-" + pipeline,
                "build-" + pipeline,
                new ImmediateThenDailySchedule(),
                delivery));
      }

      assertTrue(fourStarted.await(5L, TimeUnit.SECONDS));
      assertEquals(20, scheduler.activeScheduleCount());
      assertTrue(scheduler.queuedTaskCount() <= 16);
      assertTrue(scheduler.largestPoolSize() <= 4);
      release.countDown();
      assertTrue(completed.await(10L, TimeUnit.SECONDS));
      assertTrue(maximumActive.get() <= 4);
    } finally {
      release.countDown();
      for (OctaneProgressEmailScheduler.Registration registration : registrations) {
        registration.cancel();
      }
      scheduler.shutdownForTests();
    }
  }

  @Test
  public void rejectsSchedulesBeyondConfiguredBound() {
    OctaneProgressEmailScheduler scheduler =
        OctaneProgressEmailScheduler.createForTests(
            1, 2, Duration.ofMinutes(5L), Duration.ofSeconds(5L));
    OctaneProgressEmailScheduler.Delivery first = occurrence -> {};
    OctaneProgressEmailScheduler.Delivery second = occurrence -> {};
    OctaneProgressEmailScheduler.Delivery rejected = occurrence -> {};
    try {
      scheduler.schedule("one", "build-one", new ImmediateThenDailySchedule(), first);
      scheduler.schedule("two", "build-two", new ImmediateThenDailySchedule(), second);
      try {
        scheduler.schedule("three", "build-three", new ImmediateThenDailySchedule(), rejected);
        fail("Expected the bounded scheduler to reject the third registration.");
      } catch (RejectedExecutionException expected) {
        assertTrue(expected.getMessage().contains("active schedule limit"));
      }
    } finally {
      scheduler.shutdownForTests();
    }
  }

  @Test
  public void registersThirtyAggressiveCronPipelinesWithoutExecutorGrowth() {
    OctaneProgressEmailScheduler scheduler =
        OctaneProgressEmailScheduler.createForTests(
            4, 64, Duration.ofMinutes(5L), Duration.ofSeconds(5L));
    List<OctaneProgressEmailScheduler.Delivery> deliveries = new ArrayList<>();
    List<OctaneProgressEmailScheduler.Registration> registrations = new ArrayList<>();
    try {
      for (int pipeline = 0; pipeline < 30; pipeline++) {
        OctaneProgressEmailScheduler.Delivery delivery = occurrence -> {};
        deliveries.add(delivery);
        registrations.add(
            scheduler.schedule(
                "aggressive-pipeline-" + pipeline,
                "aggressive-build-" + pipeline,
                "* * * * *",
                delivery));
      }

      assertEquals(30, scheduler.activeScheduleCount());
      assertEquals(30, scheduler.queuedTaskCount());
      assertTrue(scheduler.largestPoolSize() <= 4);
    } finally {
      for (OctaneProgressEmailScheduler.Registration registration : registrations) {
        registration.cancel();
      }
      assertEquals(0, scheduler.activeScheduleCount());
      assertEquals(0, scheduler.queuedTaskCount());
      scheduler.shutdownForTests();
    }
  }

  @Test
  public void cancelOwnerRemovesEveryTimerForCompletedRun() {
    OctaneProgressEmailScheduler scheduler =
        OctaneProgressEmailScheduler.createForTests(
            1, 10, Duration.ofMinutes(5L), Duration.ofSeconds(5L));
    OctaneProgressEmailScheduler.Delivery first = occurrence -> {};
    OctaneProgressEmailScheduler.Delivery second = occurrence -> {};
    try {
      scheduler.schedule("one", "same-build", new ImmediateThenDailySchedule(), first);
      scheduler.schedule("two", "same-build", new ImmediateThenDailySchedule(), second);
      assertEquals(2, scheduler.activeScheduleCount());

      scheduler.cancelOwner("same-build");

      assertEquals(0, scheduler.activeScheduleCount());
    } finally {
      scheduler.shutdownForTests();
    }
  }

  private static final class ImmediateThenDailySchedule
      implements OctaneProgressEmailScheduler.Schedule {
    @Override
    public String expression() {
      return "* * * * *";
    }

    @Override
    public String description() {
      return "Every minute.";
    }

    @Override
    public Instant nextAfter(Instant after, Instant lastDelivery, Duration minimumInterval) {
      return lastDelivery == null ? after.plusMillis(25L) : after.plus(Duration.ofDays(1L));
    }
  }
}
