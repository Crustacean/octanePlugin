# Octane Gate Scale Architecture Proposal

Date: 2026-07-17

Evidence: [PERFORMANCE_PROFILING.md](PERFORMANCE_PROFILING.md)

Status: Core scale architecture implemented; compatibility renderer retained for small reports and
email capture.

## Goals

- Support 20 concurrent gates with 500 suite runs each without exhausting a 1 GB controller heap.
- Avoid holding a Jenkins shared timer worker or agent executor while waiting for Octane or a poll
  deadline.
- Bound Octane request concurrency and reuse connections across jobs.
- Keep `build.xml` small and stable regardless of chart density.
- Deliver a useful report quickly, then render dense/off-screen details incrementally.
- Preserve restart, cancellation, email screenshot, security, and accessibility behavior.

## Implemented Milestone

The first production milestone now implements the controller-safety and live-report portions of this
proposal:

| Area | Implementation |
| --- | --- |
| Persistence | Atomic, checksum-addressed `octane-results.json`, compact index, paged section files, and a gzip compatibility snapshot under the build directory |
| Run action | Only schema/artifact metadata is persisted for new dense reports; the historical `snapshot` field remains readable for archived builds |
| Endpoints | Authorized JSON index/section endpoints with `ETag`, `If-None-Match`, `304`, private cache controls, bounded cursors, and a locally served renderer bundle |
| Browser | Safe DOM/SVG renderer, 80-bar visible cap, on-demand section paging, one delegated tooltip overlay, responsive resize, observer cleanup, stale-render guards, `content-visibility`, and a no-JavaScript summary |
| Pipeline | Persisted one-poll state machine; Jenkins Timer only schedules short wake-ups; HTTP/poll work runs on cancellable virtual threads |
| HTTP | One shared JDK client, `sendAsync`, fair per-server semaphore capped at 8 by default, `Retry-After`, exponential backoff, and cancellation propagation |
| API/cache | 40-ID bulk suite topology lookup, deduplicated child fetches, 30-second/20,000-entry LRU topology cache, and per-suite single-flight loading without serializing unrelated jobs |

The existing static email renderer remains the compatibility path. Moving email capture to the same
client bundle requires a separately authenticated or embedded-data headless flow and should happen
only after visual acceptance, as anticipated by delivery step 7. The current page reducer still
retains the compact run records needed by criteria, tester, defect-link, and email behavior; replacing
those shared contracts with a streaming-only reducer is a later optimization rather than a condition
of this milestone.

## Current And Target Flows

```text
Current
Pipeline Step
  -> Jenkins Timer worker (held for the full gate)
  -> synchronous OctaneClient.send()
  -> nested run/defect collections
  -> snapshot + full HTML
  -> Run.save() / XStream build.xml
  -> /snapshot renders full HTML again
  -> browser parses and paints 34k+ nodes

Implemented core flow
Pipeline Step
  -> persisted poll coordinator
  -> scheduled one-shot wake-up
  -> shared async HTTP client + per-server limiter
  -> paged reducer + bounded caches
  -> compact versioned JSON artifact
  -> small RunAction metadata in build.xml
  -> browser fetches JSON and lazily renders visible SVG regions
```

## A. Split Data Production From UI Rendering

### Persist Compact Data, Not Presentation

Store a versioned `octane-results.json` artifact (optionally gzip-compressed) and keep only a small
pointer/summary in `OctaneGateReportAction`. The action should contain terminal state, timestamps,
schema version, artifact path, checksum, refresh metadata, and small headline counters. It should not
contain pre-rendered HTML or the complete visual object graph.

A compact schema can use arrays for repeated series data while retaining named objects at public
boundaries:

```json
{
  "schemaVersion": 1,
  "state": "POLLING",
  "updatedAt": "2026-07-16T20:00:00Z",
  "criteria": { "passed": false, "rows": [] },
  "summary": { "total": 25000, "executed": 18750, "passed": 17200 },
  "sections": [
    {
      "id": "regressions",
      "statusCounts": [17200, 450, 300, 800, 0, 250],
      "testerBars": [["tester-1", 480, 10, 4, 6, 0, 0]]
    }
  ],
  "defects": { "open": 72, "closed": 18, "trend": [] }
}
```

Use atomic replacement when updating the live artifact. Keep the final artifact immutable so archived
reports remain reproducible. If artifact APIs are awkward during an active build, use a plugin-owned
build directory with the same `Run` authorization checks and publish it as an artifact on completion.

### Snapshot API

Change the report endpoint to return JSON with `ETag`/checksum support. Split large sections behind a
section/cursor endpoint so a headline refresh does not retransmit all bars:

```text
GET .../octaneSuiteGateReport/snapshot          -> state, summary, section index
GET .../octaneSuiteGateReport/data/sections/0   -> first chart section
GET .../octaneSuiteGateReport/data/sections/0/bars?cursor=...
```

Clients should send `If-None-Match`; unchanged polls return `304`. Do not cache active-build responses
in shared proxies.

### Client Rendering

- Render only the first visible chart section initially.
- Use one SVG per visible chart, with bars represented by `<rect>` elements and one delegated tooltip
  overlay. Do not create a hidden tooltip subtree for every point.
- Virtualize long tester/bar collections and request subsequent chunks near the viewport.
- Apply `content-visibility: auto` only to confirmed off-screen, self-contained report sections and
  pair it with `contain-intrinsic-size` to prevent layout shift. Provide a manual containment fallback
  for older Jenkins-supported browsers.
- Preserve an accessible textual summary/table for chart data and verify keyboard traversal across
  deferred sections.
- Keep all JavaScript and CSS inside the plugin; do not introduce CDN dependencies into Jenkins.

The modern-web-guidance review specifically supports `content-visibility` plus intrinsic sizing for
dense, off-screen dashboard regions. It should complement, not replace, data virtualization: 500
unused elements should not be created merely because the browser can skip painting them.

### Email Screenshots

The headless renderer should load the same versioned JSON and client renderer used by the live page.
Expose a deterministic `window.__octaneReportReady` promise and capture only after it resolves. The
email path can request an explicit static layout/cap, but it must not maintain a second model-to-HTML
implementation.

Retain a small no-JavaScript summary and downloadable JSON link for degraded environments.

## B. Make Polling Truly Non-Blocking

The existing `StepExecution.start()` already returns asynchronously, but its single `Timer` task then
runs the entire blocking loop. Replace that loop with a restartable state machine:

1. `start()` persists deadlines and schedules an immediate poll, then returns `false`.
2. A short callback starts a bounded async fetch plan and returns; it never sleeps or waits.
3. JDK `HttpClient.sendAsync()` stages parse/reduce pages and publish one atomic snapshot.
4. Completion schedules the next one-shot wake-up at the poll deadline.
5. Terminal completion calls `StepContext.onSuccess/onFailure` exactly once.

Use Jenkins' scheduler only to trigger short callbacks. `QueueListener` observes queue transitions and
is not the right primary abstraction for an already-running gate. A custom asynchronous
`StepExecution` with persisted state and completion callbacks is the best fit.

### Shared Client And Concurrency Limits

- Maintain one shared `HttpClient`/connection pool per Octane server configuration, not per gate.
- Enforce a per-server limit of 8 in-flight requests and a smaller per-job limit (initially 2-4).
- Queue excess work fairly so one 500-suite gate cannot starve normal jobs.
- Honor `Retry-After`; use exponential backoff with jitter scheduled asynchronously.
- Propagate cancellation through every `CompletableFuture` and discard late responses.
- Re-authenticate on resume/401 without serializing cookies or credentials into Pipeline state.

For Declarative Pipelines, document an `agent none` wait stage or equivalent placement outside a
`node` block. The plugin does not require a workspace, so an agent executor should not remain reserved
while the controller waits for Octane.

### Restart Safety

Persist only IDs, criteria, deadlines, last successful cursor/checksum, and terminal guards. On
controller restart, rebuild transient clients, authenticate again, and schedule the next required
page/poll. Snapshot publication must be idempotent, and stale callbacks must compare a generation ID
before writing.

## C. Add A Bounded, Observable Cache

Caffeine is preferred for weighted eviction, refresh, in-flight request coalescing, and metrics. A
Guava cache is acceptable if dependency policy makes Caffeine undesirable, but the same limits and
isolation rules apply.

| Target | Suggested TTL | Bound | Notes |
| --- | ---: | ---: | --- |
| Field/list metadata (phase, severity, priority) | 60 min | 2,000 entries | Invalidate on server config/API version change |
| User references | 30 min | 50,000 entries | Cache ID/name only |
| Test/product-area references | 10 min | 100,000 entries or weighted 50 MB | Immutable display metadata only |
| Suite topology (`runs_in_suite`) | 30-60 s active, 10 min terminal | 20,000 suites | Refresh while a suite is still changing |
| Run static fields (name, test, tester) | 2-5 min | Weighted 100 MB | Do not cache status this long |
| Dynamic run status | At most half one poll interval | Weighted 50 MB | Prefer per-poll memoization |
| Defect identity/static fields | 10 min | Weighted 50 MB | Phase/status TTL remains short |
| Dynamic defect phase/status | At most half one poll interval | Weighted 25 MB | Must reflect closure promptly |

Every key must include server/config generation, shared-space ID, workspace ID, entity/query identity,
requested field set, and schema/API version. Never cache plaintext credentials. Session cookies may be
owned by a shared authenticated client, must expire, and must be refreshed atomically after a 401.

Use a single-flight loader so simultaneous jobs requesting the same static entity share one future.
Expose hit/miss/load/eviction/weight metrics through Jenkins administrative monitoring. Configuration
changes must invalidate the affected server namespace.

The benchmark used distinct suite IDs per job, so it intentionally did not quantify cross-job cache
benefit. Production jobs sharing workspaces and metadata should gain substantially, while worst-case
memory remains bounded by `maximumWeight`.

## D. Stream And Batch API Pagination

The existing client already uses 40-item query chunks and 200-item pages. The problem is that callers
retain all pages in nested lists before reduction and perform many small requests sequentially.

### Fetch Plan

1. Bulk-query suite entities by ID in 40-ID chunks instead of one suite request per ID, if the Octane
   endpoint supports the required `runs_in_suite` fields.
2. Flatten and deduplicate child run IDs immediately into a compact set.
3. Fetch child-run chunks with bounded concurrency and feed each page to an incremental reducer.
4. Update status counters, tester-bar aggregates, scope membership, defect-link indexes, and compact
   report DTOs as records arrive.
5. Release each Jackson page/tree after reduction. Retain full `RunRecord` objects only where a
   downstream feature demonstrably needs them.
6. Stream the JSON artifact incrementally or write section chunks; do not rebuild one giant string.

Java `Stream` alone is not reactive and does not provide backpressure. Prefer a page-consumer API or
JDK `Flow.Publisher` backed by the bounded async client. Introduce Reactor only if the project accepts
the dependency and its operational complexity.

### Rate And Failure Handling

- Cap global concurrency independently of page size.
- Respect Octane pagination totals and stop on short pages.
- Retry only idempotent reads; use jitter and `Retry-After` for 429/5xx responses.
- Record page cursor, request count, bytes, retries, and latency by endpoint.
- Define whether one failed section makes the gate fail, retries the poll, or publishes a partial
  report. Never silently evaluate criteria from incomplete data.

## Observability Required Before Refactoring

Add metrics around the existing and target flows before enabling the redesign broadly:

- Active gates, scheduled polls, in-flight requests, and queued requests by server.
- Request count/bytes/retries/429s and latency by entity endpoint.
- Poll wall time, worker-active time, reducer CPU, snapshot size, artifact size, and `Run.save()` time.
- Cache hit/miss/load/eviction/current weight.
- JSON response size, DOM node count, first paint, report-ready time, and tooltip interaction latency.
- Controller heap/GC pause and rejected/cancelled callbacks.

Use structured IDs for job/build/poll generation, but do not log credentials, cookies, raw defect
descriptions, or recipient data.

## Delivery Sequence

1. **Implemented - instrumentation and guardrails:** request/cache counters, request cap, and repeatable
   Maven scale fixtures are present. Production administrative presentation remains a follow-up.
2. **Implemented - persistence split:** versioned compact artifacts are atomic and dense snapshots no
   longer enter new `build.xml` files. Legacy inline snapshots remain readable.
3. **Implemented in bounded form - fetch planner:** suite topology is bulk queried, child IDs are
   deduplicated, and all response pages remain bounded. A page-consumer reducer can replace retained
   run DTOs later if feature contracts are narrowed.
4. **Implemented - async coordinator:** one-shot wake-ups, virtual poll tasks, shared `sendAsync`,
   cancellation, resume, and exactly-once completion replace the long-lived Timer loop.
5. **Implemented for the highest-duplication target - cache/coalescing:** suite topology has bounded
   TTL/LRU storage and per-suite single flight. Additional metadata caches should be added only when
   their API call sites exist and can be measured.
6. **Implemented for live dense reports - lazy renderer:** JSON, bounded paging, deferred sections,
   observer cleanup, and safe delegated interaction are active over the 80-bar threshold.
7. **Retained compatibility path:** static email and small-report rendering remain until browser,
   screenshot, archived-build, and accessibility acceptance is complete.

## Acceptance Targets

Run the same 20-job x 500-suite scenario for every milestone.

| Area | Target | Current verification |
| --- | --- | --- |
| Heap | No OOM at 1 GB; peak below 768 MB at 2 GB | Two exact model/artifact runs pass at 1 GB; conservative sampled peak 488,771,680 bytes |
| Allocation | Below 200 MB/job for the measured poll | Requires a post-change JFR allocation profile |
| GC | P95 pause below 100 ms; cumulative pause below 5% of wall time | Requires a post-change JFR pause profile |
| Worker preservation | No Timer/CPS worker blocked across HTTP or poll intervals | Timer schedules wake-ups only; virtual threads own poll/HTTP waits |
| HTTP concurrency | Maximum 8 in-flight requests per Octane server, independently configurable | 20-request test peaks at 8; system property can set 1-64 |
| API fan-out | Bulk suite lookup; at least 50% fewer requests in the benchmark | 638 versus 1,500 suite/child requests, a 57.5% reduction |
| Persistence | RunAction contribution below 100 KB; compressed JSON below 5 MB/job | Dense fixture `build.xml` is 3,671 bytes; complete JSON maximum is 687,775 bytes |
| Live response | Initial snapshot/index below 250 KB; unchanged poll returns `304` | Scale index maximum is 162,024 bytes; snapshot and index `304` tests pass |
| Browser | Initial DOM below 5,000 nodes; local median FCP below 200 ms | Jenkins fixture starts at 557 nodes; fresh cross-browser FCP measurement remains pending |
| Interaction | Tooltip/view switch P95 below 100 ms with 500 bars available | All 500 bars are paged through an 80-bar DOM window; browser P95 remains pending |
| Correctness | Criteria results identical to the legacy path for all fixtures | Existing criteria/runner suite retained; all 146 Maven tests pass |
| Resilience | Restart/cancel/401/429/partial-page tests pass without duplicate completion | Resume/cancel/401/retry/paging coverage retained; all 146 Maven tests pass |

## Risks And Controls

- **Archived compatibility:** keep a schema-versioned reader and a minimal legacy snapshot adapter.
- **Authorization:** serve artifacts through Jenkins `Run` permissions; do not expose direct filesystem
  paths.
- **XSS:** deserialize JSON as data and populate SVG/text with DOM APIs or strict escaping. Never place
  Octane values into `innerHTML`.
- **Cache leakage:** isolate cache keys by server/shared space/workspace and never mix credentials.
- **Stale criteria:** dynamic status/defect data must not outlive its short TTL or a poll generation.
- **Email drift:** use the same DTO and renderer bundle for live and screenshot paths.
- **Browser support:** feature-detect `content-visibility`; maintain a containment/display fallback and
  test Firefox, Edge, Safari, and Chrome according to the plugin's support policy.

This sequence addresses the measured failure modes without combining every risk into one release.
The first implementation milestone should be instrumentation plus persistence reduction, because the
29 MB XStream snapshot and 1 GB OOM are the most immediate controller-safety issues.
