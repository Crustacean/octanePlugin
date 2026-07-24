package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

final class OctaneSuiteTopologyCache {
  static final int MAXIMUM_ENTRIES = 20_000;
  static final Duration ACTIVE_TTL = Duration.ofSeconds(30);

  private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(256, 0.75f, true);
  private static final ConcurrentMap<Key, CompletableFuture<List<String>>> IN_FLIGHT =
      new ConcurrentHashMap<>();
  private static final AtomicLong HITS = new AtomicLong();
  private static final AtomicLong MISSES = new AtomicLong();
  private static final AtomicLong EVICTIONS = new AtomicLong();

  private OctaneSuiteTopologyCache() {}

  static Map<String, List<String>> getAll(
      String namespace, Collection<String> suiteRunIds, Loader loader)
      throws IOException, InterruptedException {
    return getAll(namespace, suiteRunIds, loader, System.nanoTime());
  }

  static Map<String, List<String>> getAll(
      String namespace, Collection<String> suiteRunIds, Loader loader, long now)
      throws IOException, InterruptedException {
    Map<String, List<String>> result = new LinkedHashMap<>();
    Map<Key, CompletableFuture<List<String>>> pending = new LinkedHashMap<>();
    Map<Key, CompletableFuture<List<String>>> owned = new LinkedHashMap<>();
    for (String suiteRunId : suiteRunIds) {
      Key key = new Key(namespace, suiteRunId);
      Entry cached = cachedEntry(key, now);
      if (cached != null) {
        HITS.incrementAndGet();
        result.put(suiteRunId, cached.runIds);
        continue;
      }
      MISSES.incrementAndGet();
      CompletableFuture<List<String>> candidate = new CompletableFuture<>();
      CompletableFuture<List<String>> existing = IN_FLIGHT.putIfAbsent(key, candidate);
      CompletableFuture<List<String>> future = existing == null ? candidate : existing;
      pending.put(key, future);
      if (existing == null) {
        Entry refreshed = cachedEntry(key, System.nanoTime());
        if (refreshed == null) {
          owned.put(key, candidate);
        } else {
          candidate.complete(refreshed.runIds);
          IN_FLIGHT.remove(key, candidate);
        }
      }
    }

    if (!owned.isEmpty()) {
      loadOwned(loader, owned, now);
    }
    for (Map.Entry<Key, CompletableFuture<List<String>>> entry : pending.entrySet()) {
      result.put(entry.getKey().suiteRunId, await(entry.getValue()));
    }
    return result;
  }

  static Metrics metrics() {
    synchronized (CACHE) {
      return new Metrics(HITS.get(), MISSES.get(), EVICTIONS.get(), CACHE.size());
    }
  }

  static void resetForTests() {
    synchronized (CACHE) {
      CACHE.clear();
    }
    IN_FLIGHT.clear();
    HITS.set(0L);
    MISSES.set(0L);
    EVICTIONS.set(0L);
  }

  private static void evictToBound() {
    while (CACHE.size() > MAXIMUM_ENTRIES) {
      Key eldest = CACHE.keySet().iterator().next();
      CACHE.remove(eldest);
      EVICTIONS.incrementAndGet();
    }
  }

  private static Entry cachedEntry(Key key, long now) {
    synchronized (CACHE) {
      Entry entry = CACHE.get(key);
      if (entry != null && entry.expiresAtNanos > now) {
        return entry;
      }
      if (entry != null) {
        CACHE.remove(key);
      }
      return null;
    }
  }

  private static void loadOwned(
      Loader loader, Map<Key, CompletableFuture<List<String>>> owned, long loadedAt)
      throws IOException, InterruptedException {
    List<String> ids = owned.keySet().stream().map(key -> key.suiteRunId()).toList();
    try {
      Map<String, List<String>> loaded = loader.load(ids);
      long expiry = loadedAt + ACTIVE_TTL.toNanos();
      synchronized (CACHE) {
        for (Map.Entry<Key, CompletableFuture<List<String>>> entry : owned.entrySet()) {
          List<String> runIds =
              List.copyOf(loaded.getOrDefault(entry.getKey().suiteRunId, List.of()));
          CACHE.put(entry.getKey(), new Entry(runIds, expiry));
          entry.getValue().complete(runIds);
        }
        evictToBound();
      }
    } catch (IOException | InterruptedException | RuntimeException | Error failure) {
      owned.values().forEach(future -> future.completeExceptionally(failure));
      throw failure;
    } finally {
      owned.forEach((key, future) -> IN_FLIGHT.remove(key, future));
    }
  }

  private static List<String> await(CompletableFuture<List<String>> future)
      throws IOException, InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException ioException) {
        throw ioException;
      }
      if (cause instanceof InterruptedException interruptedException) {
        throw interruptedException;
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IOException("Unable to load ALM Octane suite topology.", cause);
    }
  }

  interface Loader {
    Map<String, List<String>> load(List<String> suiteRunIds)
        throws IOException, InterruptedException;
  }

  record Metrics(long hits, long misses, long evictions, int size) {}

  private record Key(String namespace, String suiteRunId) {}

  private record Entry(List<String> runIds, long expiresAtNanos) {}
}
