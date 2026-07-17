# Octane Jenkins Plugin Scale Profile

Date: 2026-07-16

Baseline commit: `55baa8c`

Companion proposal: [SCALE_ARCHITECTURE_PROPOSAL.md](SCALE_ARCHITECTURE_PROPOSAL.md)

## Executive Summary

The current implementation does not safely sustain the requested load of 20 concurrent gates with
500 suite runs per gate. The 2 GB JVM run completed, but observed heap reached 2.11 GB (98.5% of
the configured maximum). The same workload failed reproducibly with `OutOfMemoryError: Java heap
space` at a 1 GB heap limit.

The retained model is not the primary problem. After a forced full GC while all 20 snapshots were
still reachable, heap use was 83.8 MB. Peak pressure comes from concurrent JSON/model construction,
HTML generation, and especially Jenkins XStream serialization. Each job allocated approximately
755 MB and produced a median 29.0 MB XML representation of its snapshot.

The API access pattern is also too chatty and synchronous. One poll issued 2,502 HTTP requests per
job, or 50,040 requests for the 20-job run. A gate occupies one Jenkins shared `Timer` worker for its
entire lifetime, including about 29.4 seconds of network work in this low-latency local test and the
poll-interval wait. The Pipeline CPS thread is released, but a Declarative stage may still retain its
agent executor unless the gate runs outside a `node` allocation.

The live report response was 1.68 MB and expanded to 34,629 DOM nodes for 500 bars. Firefox median
first contentful paint was 475 ms at 1400 x 900. This is usable in isolation, but it leaves little
headroom inside an already busy Jenkins page and repeats the same heavy server-side HTML work on
every snapshot request.

## Post-Implementation Acceptance

The baseline above remains the evidence for the original architecture. On 2026-07-17, new automated
acceptance fixtures exercised the implemented scale architecture with a 1 GB Maven heap. This is a
focused regression workload rather than a replacement JFR/browser profile: it validates bounded
model/artifact generation, persistence, request fan-out, concurrency, endpoint caching, and initial
DOM structure. Allocation, GC-pause, and cross-browser FCP figures still require a second full
profiler run.

| Verification | Result |
| --- | ---: |
| Concurrent jobs | 30 |
| Suites per job | 500 |
| Child runs per suite / per job | 50 / 25,000 |
| Linked defects per job / total | 1,000 / 30,000 |
| Scale mapper wall time | 1,458 ms |
| Sampled heap peak with `-Xmx1g` | 549,064,800 bytes |
| Maximum compact index | 162,024 bytes |
| Maximum complete JSON | 687,775 bytes |
| Dense fixture `build.xml` | 3,671 bytes |
| Dense fixture initial DOM | 557 nodes |
| Unchanged snapshot/index response | HTTP `304` |
| 500-suite x 50-child suite/child requests | 638, down from 1,500 (57.5%) |
| 20 simultaneous requests | Maximum 8 in flight |
| Aggressive cron registrations (`* * * * *`) | 30 active / 30 queued |
| Maximum progress-email delivery workers | 4 |
| Progress-email minimum delivery interval | 5 minutes per build |
| Progress-email active-schedule admission limit | 256 |
| Full Maven regression suite | 158 tests, 0 failures, 0 errors |

The 1 GB acceptance run completed without `OutOfMemoryError`, versus the reproducible baseline
failure. The 549.1 MB sampled maximum covers concurrent construction and compact
mapping of all 750,000 child-run records plus 30,000 defect records. It does not include a live
Octane server, Jenkins XStream startup, or a browser process, so it should not be compared directly
with the original 2.11 GB end-to-end peak.

The cron scheduler fixture registered 30 simultaneous every-minute schedules while retaining one
future per active build. A separate 20-delivery overlap fixture blocked outbound work deliberately
and observed no more than four concurrent delivery workers. Cancelling registrations removed their
queued futures immediately. Cron parsing tests also cover daily, weekday-range, hour-step, and
minute-step expressions, while the aggressive schedule is advanced to the first eligible cron time
at least five minutes after the preceding delivery.

The persistence fixture confirms that dense report objects no longer enter new `build.xml` files.
Archived inline snapshots remain readable, while current data is published atomically as a compact
index, paged sections, complete JSON, and a compressed compatibility snapshot. The client creates at
most 80 bars at once and fetches subsequent windows on demand, so all 500 bars remain available
without recreating the baseline 34,629-node page.

## Scope And Method

The benchmark exercised existing production classes without changing plugin or test source. A
temporary Java harness outside the worktree used the real `OctaneClient`, model constructors,
`OctaneReportZoneHtmlRenderer`, and Jenkins `XStream2` serializer against a local JDK HTTP server.
The server emulated ALM Octane authentication, suite-run lookup, child-run pagination, and linked
defect responses.

| Parameter | Value |
| --- | ---: |
| Concurrent jobs | 20 |
| Unique suite runs per job | 500 |
| Child runs per suite | 50 |
| Child runs processed | 500,000 |
| Linked defects per job | Up to 1,000 |
| Stub response delay | 2 ms |
| Octane query chunk | Existing 40-item limit |
| Octane page size | Existing 200-item limit |
| JVM | OpenJDK 25.0.3, G1, 2 GB heap |
| Host | Intel i5-8350U, 8 logical CPUs, 7 GiB RAM |
| Browser | Firefox 152.0.6, headless |
| Kernel | Linux 7.0.0-27-generic x86_64 |

Each job performed this sequence once:

1. Authenticate an `OctaneClient`.
2. Fetch 500 suite entities and all 25,000 child runs.
3. Fetch and deduplicate linked defects.
4. Build gate metrics, report sections, risk heat map, and snapshot.
5. Render the live report-zone HTML and static email HTML.
6. Serialize the snapshot using Jenkins `XStream2` and Java serialization.
7. Sign out.

The suite ID range was different for every job. This is a worst-case test for cache misses and avoids
artificially improving the result through shared suite data.

### Measurement Notes

- The 2 ms local response delay is much lower than a production Octane round trip. Network timings
  therefore represent an optimistic floor.
- The local HTTP stub and client ran in the same JVM. Peak thread count includes stub-server threads;
  it is not a direct Jenkins-controller thread count.
- Browser memory is reported as an observed process/cgroup indicator, not a precise retained-heap
  measurement. Summed process RSS double-counts shared pages.
- The forced-layout probe reads geometry after load. It measures a subsequent layout pass, not all
  style/layout work already included in `domInteractive` and first paint.
- Java Flight Recorder adds overhead. Non-instrumented numbers are the latency baseline; JFR is used
  only to identify hotspots.

## Backend Baseline

### Process And Heap

| Metric | Result |
| --- | ---: |
| End-to-end wall time | 38.87 s |
| Process CPU time | 141.07 s |
| Effective CPU use | 3.63 cores / 45.4% of 8 CPUs |
| Observed peak heap | 2,114,398,424 bytes (98.5% of 2 GB) |
| Heap before retained-state GC | 1,349,469,800 bytes |
| Heap retained with all snapshots reachable | 83,751,528 bytes |
| Heap after releasing snapshots | 24,185,584 bytes |
| GC collections / cumulative time | 79 / 3.06 s |
| Peak process threads | 209, including local stub threads |
| Mean allocation per job | 755,179,298 bytes |
| Approximate allocation across 20 jobs | 15.1 GB |

The difference between 1.35 GB before the retained-state GC and 83.8 MB afterward demonstrates that
most pressure is short-lived allocation. The 1 GB rerun failed with `OutOfMemoryError: Java heap
space`, confirming that concurrency can outrun G1 collection even though the final retained graph is
much smaller.

### Per-Job Phase Timing

| Phase | P50 | P95 | Mean |
| --- | ---: | ---: | ---: |
| Synchronous HTTP work | 29.39 s | 29.58 s | 29.43 s |
| Model/snapshot construction | 1.48 s | 1.73 s | 1.41 s |
| Live report HTML rendering | 593 ms | 685 ms | 533 ms |
| Email report HTML rendering | 87 ms | 229 ms | 115 ms |
| Jenkins XStream serialization | 6.31 s | 6.89 s | 6.31 s |
| Complete job | 38.62 s | 38.83 s | 38.48 s |
| Job CPU time | 2.48 s | 2.70 s | 2.50 s |

Only about 6.4% of median job wall time was measured as job-thread CPU. The worker remains occupied
through network waits, serializer contention, GC pauses, and poll-interval waits.

### API Fan-Out

| Request category | Per job | 20 jobs |
| --- | ---: | ---: |
| Authentication and sign-out | 2 | 40 |
| Suite-run lookups | 500 | 10,000 |
| Child-run pages/chunks | 1,000 | 20,000 |
| Defect relation pages/chunks | 1,000 | 20,000 |
| **Total** | **2,502** | **50,040** |

The run received 151,416,142 response bytes (7.57 MB per job) and reached 20 simultaneous requests,
one per synchronous job. Loopback traffic averaged 49.2 Mbps in the JFR run. At only 30 ms of real
round-trip latency, 2,502 sequential calls imply roughly 75 seconds of latency per poll before server
processing; this is an extrapolation, not a measured production result.

### Payload And Persistence Cost

| Artifact | P50 size | Maximum size |
| --- | ---: | ---: |
| Live report-zone HTML | 1,679,270 bytes | 1,679,270 bytes |
| Static email report HTML | 404,103 bytes | 404,103 bytes |
| Jenkins XStream snapshot XML | 28,963,537 bytes | 29,024,929 bytes |
| Java serialized snapshot | 1,504,509 bytes | 1,507,890 bytes |

`OctaneGateReportAction.onPoll()` rebuilds the snapshot and calls `Run.save()` on every poll. A
snapshot of this density can therefore cause approximately 29 MB of XStream output per active build,
or about 579 MB rewritten for 20 builds in one polling cycle. This is an estimate based on snapshot
serialization; the complete Jenkins `build.xml` has small additional action metadata.

## Browser Baseline

The live fixture was generated by the existing renderer from one representative 500-suite snapshot.
It was served locally with caching disabled and reloaded five times at each viewport.

| Profile | HTML | DOM nodes | Bars | Median `domInteractive` | Median FCP | Median load | Median screenshot |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Live 1400 x 900 | 1.68 MB | 34,629 | 500 | 462 ms | 475 ms | 515 ms | 38 ms |
| Live 1920 x 1080 | 1.68 MB | 34,629 | 500 | 443 ms | 434 ms | 495 ms | 45 ms |
| Email 1400 x 788 | 401 KB | 17,199 | 53 | 36 ms | 166 ms | 162 ms | 29 ms |

The email renderer's width-based capacity policy rendered only 53 of the 500 bars. The full live
fixture did render all 500. A post-load geometry read across all bars took 1-2 ms, while the expensive
initial parse, style, layout, and paint work is reflected in the 434-475 ms first-paint result.

Firefox's summed process-tree RSS rose from approximately 1.16 GB on the first live load to 1.63 GB
after repeated loads; the transient systemd scope reported an approximately 1.1 GB cgroup memory
peak. These values include the browser's multiprocess runtime and caches and should be treated as a
capacity warning rather than page-retained memory.

## Flight Recorder Findings

The instrumented run took 42.17 seconds, so its timings are not used as the baseline. It recorded the
following structural hotspots:

- `byte[]` accounted for 37.68% and `char[]` for 28.98% of sampled allocation pressure.
- `Character.toChars`, array copies, UTF-8 encoding, XStream `QuickWriter`, and Jenkins
  `PrettyPrintWriter` dominated XML/string work.
- XStream `CustomObjectOutputStream.getInstance` caused 72 monitor-contention events averaging
  20.5 ms and reaching 44.4 ms.
- G1 recorded 65 pauses totaling 3.17 seconds. Median pause was 28.4 ms, P95 was 233 ms, and the
  maximum was 425 ms.
- Peak active threads were 211 in the combined harness process. Thread starts included 20 distinct
  `HttpClient` selector managers and up to three client workers per job. Many remaining pool threads
  belonged to the in-process HTTP stub.
- Aggregate `ThreadPark` time was 1 hour 48 minutes across 76,128 events. This is summed across all
  threads and primarily reflects synchronous completion waits and idle executors, not elapsed test
  time.

## Current Architecture Findings

1. **The Pipeline step is asynchronous only at its outer boundary.**
   [`OctaneSuiteGateStep.Execution`](src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/controllers/OctaneSuiteGateStep.java#L282)
   returns `false` from `start()`, but submits the entire blocking gate to Jenkins' shared `Timer` at
   line 308. One timer worker remains occupied until completion.
2. **Polling and fan-out are sequential per job.**
   [`OctaneGateRunner`](src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/services/OctaneGateRunner.java#L90)
   performs a synchronous loop. Its suite fetch at line 524 invokes one blocking client operation per
   suite.
3. **The HTTP client blocks and multiplies thread pools.**
   [`OctaneClient`](src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/repositories/OctaneClient.java#L45)
   creates a new JDK `HttpClient` per gate and calls `send()` at lines 73 and 501. Retry backoff uses
   `Thread.sleep()` at line 592.
4. **Pagination exists, but aggregation is monolithic.** The current 40-item query chunks and
   200-item pages prevent one enormous response, but all parsed records are accumulated in nested
   lists/maps before metrics and report models are built.
5. **Every poll persists and re-renders dense state.**
   [`OctaneGateReportAction`](src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/actions/OctaneGateReportAction.java#L91)
   rebuilds and saves the snapshot. Its `/snapshot` endpoint renders a complete HTML fragment at line
   208 instead of returning compact data.
6. **Backend and browser duplicate presentation work.**
   [`OctaneReportZoneHtmlRenderer`](src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/services/OctaneReportZoneHtmlRenderer.java#L56)
   constructs the full live zone, after which the browser still parses, lays out, and paints tens of
   thousands of nodes.

## Bottleneck Priority

| Priority | Bottleneck | Evidence | Consequence |
| --- | --- | --- | --- |
| P0 | XStream/build persistence | 29 MB snapshot XML; 6.31 s P50; dominant char/byte allocation | OOM and controller disk/CPU pressure |
| P0 | Synchronous API fan-out | 2,502 calls/job; 29.39 s P50 on 2 ms local stub | Timer and agent occupancy; Octane rate-limit risk |
| P1 | Monolithic intermediate models | 755 MB allocated/job; 2.11 GB peak | GC pauses and 1 GB OOM |
| P1 | Per-gate HTTP clients | 20 selectors plus client workers | Controller thread growth and duplicated connection pools |
| P1 | Server-rendered live HTML | 1.68 MB, 34,629 nodes, 475 ms FCP | Slow refreshes and browser memory growth |
| P2 | Poll-time `Run.save()` | Full XStream rewrite every poll | Build storage I/O and lock contention |

## Baseline Conclusion

At commit `55baa8c`, the requested scenario exceeded the safe operating envelope. Increasing heap
would have postponed failure without addressing the 50,040-call poll, long-lived Timer workers,
15.1 GB of transient allocation, or 579 MB of potential build XML rewrites. The post-implementation
acceptance section records which of those risks are now structurally removed and which measurements
still require a second full JFR/browser profile.
