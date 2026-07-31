# Enterprise Performance And Benchmark Report

Date: 2026-07-30

## Environment

- Linux x86_64
- OpenJDK 25.0.3; production bytecode baseline Java 21
- Maven 3.9.12
- Loopback HTTP service for deterministic transport measurements
- Test JVM heap: 768 MiB

## Measured Results

| Scenario | Scale | Result |
| --- | --- | --- |
| Parallel Octane polling | 500 jobs, eight requests per server | 1,199.53 RPS; p95 384 ms; p99 387 ms; maximum in flight 8; permits fully released |
| Progress email dispatch | 500 registrations, four workers | 500/500 delivered exactly once in 31 ms; no queue or registry leak after cancellation |
| Suite topology request reduction | 500 suites x 50 child runs | 638 current requests versus 1,500 legacy requests |
| Dense report mapping | 30 jobs x 500 suites x 50 child runs | 750,000 child-run records exercised with bounded artifacts |
| Large single report | 701 suites x 150 child runs | 105,150 child runs; bounded index and complete-report artifacts |
| Accelerated soak | 1 minute, 500 jobs per cycle | 17,037 cycles; 8,518,500 deliveries; peak heap 496,427,552 bytes; threads 8 to 9; no deadlock or residual work |

The scheduler benchmark measures in-process scheduling correctness, not 500 simultaneous Chrome
processes or SMTP round trips. Screenshot capture is serialized per build/workspace and work is
globally bounded. Real browser, SMTP, Octane, controller, and agent capacity must be measured in the
deployment environment.

## Capacity And Recovery Controls

- One shared Java HTTP client, redirects disabled, virtual-thread poll work, fair eight-request
  per-server admission, and bounded retry/backoff for I/O, HTTP 429, and HTTP 5xx.
- Bounded HTTP responses, criteria/query sizes, topology cache, Pipeline maps, snapshots, and email
  schedules.
- Four daemon email workers, 1,024 active-schedule admission, remove-on-cancel futures, and a fixed
  cancel/reschedule race proven by the accelerated soak.
- Frontend polling aborts stale requests, coalesces renders, retains last-good data, reports
  connectivity, and backs off failed requests to a 15-second ceiling.
- Hidden tabs reduce timer rendering to one-second frames; layout tests require graph work below
  500 ms and validate normal, focused, expanded, compact, and desktop modes.

## Endurance Gate

The opt-in soak defaults to 24 hours and 500 jobs per cycle:

```bash
mvn -f pom-build.xml \
  -Doctane.enterpriseSoak.enabled=true \
  -Doctane.enterpriseSoak.durationMinutes=1440 \
  -Doctane.enterpriseSoak.jobs=500 \
  -Dtest=OctaneEnterpriseSoakTest test
```

The one-minute accelerated run passed locally and exposed the scheduler race that was remediated.
The full 24-hour run was not practical in this implementation session and remains a mandatory
pre-production gate on representative infrastructure with JVM/native-memory and SMTP telemetry.

