# Copilot Performance Analysis

## Scope

- Repository: `Crustacean/octanePlugin`
- Review type: static performance review
- Review date: 2026-09-06
- Focus: backend I/O, polling, caching, allocation, and browser rendering

## Review principles

- Findings are based on observable code paths, not benchmark assumptions.
- Network round trips are treated as higher risk than small local allocations.
- A finding is included only when its cost can grow with input size or traffic.
- Existing bounds and safeguards are noted where they limit the impact.
- Suggested mitigations are implementation-agnostic.

## Severity

- **High:** can multiply remote calls or block shared work at scale.
- **Medium:** can cause sustained latency or contention under normal growth.
- **Low:** measurable overhead, but unlikely to dominate without large inputs.

## Areas reviewed

- `OctaneClient` request construction and fallback behavior.
- `OctaneSuiteTopologyCache` cache locking and eviction.
- `OctaneReportArtifactStore` artifact serialization and cleanup.
- `OctaneTestManagementAnalytics` aggregation and sorting.
- `octane-test-management.js` DOM rendering and layout work.
- `octane-scale-report.js` chart sizing and pagination.

## Limitations

- This is a source review; no production trace or representative benchmark was available.
- Browser costs depend on payload size, viewport, and engine.
- Remote API behavior can change the observed cost of fallback requests.
- Findings should be validated with request counters, allocation profiles, and browser traces.

## Recommended validation

- Measure Octane request count per poll and per suite-run count.
- Profile cache lock wait time while loading many suite runs.
- Record report payload sizes and artifact publish duration.
- Capture browser long tasks while switching categories and rendering charts.
- Re-check findings after any API batching or rendering changes.

## Findings

### 1. High — sequential field-set fallbacks multiply Octane requests

**Location:** `src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/repositories/OctaneClient.java:493-586`

Suite-run discovery tries several field candidates in sequence, issuing another HTTP request after each unknown-field failure. The same pattern is used by defect retrieval with extended, normal, and minimal field sets. A schema mismatch therefore turns one logical lookup into multiple serialized network round trips; a bulk lookup can multiply that cost across every suite or defect batch.

**Mitigation:** cache the working field profile per Octane server/session after the first successful request, and prefer one known-compatible profile for subsequent calls. Keep a bounded fallback only for capability changes.

### 2. Medium — cache eviction scans the access-ordered map while holding its global lock

**Location:** `src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/repositories/OctaneSuiteTopologyCache.java:89-95`

When the topology cache exceeds 20,000 entries, `evictToBound()` repeatedly obtains the eldest key and removes it while `CACHE` is locked. Eviction is performed during `loadOwned()`, so concurrent cache readers and loaders wait behind the full eviction loop. The work becomes visible when many suite IDs expire or are inserted together.

**Mitigation:** use an eviction-aware bounded map or remove a bounded batch with a short lock hold; measure lock wait time before choosing the batch size.

### 3. Medium — artifact cleanup materializes and sorts the complete file tree

**Location:** `src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/services/OctaneReportArtifactStore.java:244-253`

`deleteRecursively()` walks the entire artifact tree, sorts all paths, and calls `toList()` before deleting anything. Cleanup of a large report generation therefore holds every path in memory and delays the first deletion until traversal and sorting finish.

**Mitigation:** keep artifact generations shallow and bounded, or delete in a bounded post-order collection rather than materializing the whole tree. Record cleanup duration and peak retained memory.

### 4. Medium — repeated style/layout reads occur during category rendering and scrolling

**Location:** `src/main/webapp/js/octane-test-management.js:298-313, 788-810, 949-969`

`colorsFor()` calls `getComputedStyle(zone)` once for each theme color. The category controls also read computed style, `clientWidth`, `scrollWidth`, and `scrollLeft` during scroll/update cycles, while `setSelectedCategory()` calls `scrollIntoView()` from inside the button loop. On a large dashboard or frequent refreshes, these reads and writes can trigger repeated style/layout work.

**Mitigation:** read computed styles once per render, batch layout reads before writes, and identify the selected button first so scrolling happens at most once per selection.

### 5. Low — full DOM rebuilds allocate and reattach every chart element on refresh

**Location:** `src/main/webapp/js/octane-test-management.js:683-735`

`renderFailureAnalysis()` clears the chart and recreates every category, bar, grid line, and label on each render. This is simple and bounded by the payload, but repeated polling causes avoidable DOM allocation and garbage collection, especially when categories and defect details are unchanged.

**Mitigation:** skip rendering when the relevant payload is unchanged, or update/reuse keyed category elements and replace the chart in one detached fragment.

### 6. Low — report chart sizing synchronously reads layout on every load

**Location:** `src/main/webapp/js/octane-scale-report.js:407, 544`

The chart renderer reads `clientWidth` while rendering and while loading a page. If these reads follow DOM writes in the same refresh, the browser may need to flush layout synchronously; repeated pagination or resize activity can turn this into avoidable main-thread work.

**Mitigation:** cache the measured width for a render cycle and invalidate it from a resize observer or a scheduled resize handler instead of measuring repeatedly.

## Prioritization

Address network fallback amplification first, then cache lock contention and browser layout work. Validate each change with request-count, lock-wait, allocation, and long-task measurements.

## Dead code and duplication review

**Method:** every class under `services/`, `models/`, `controllers/`, `repositories/`, `utils/`, `configs/`, `listeners/`, `entities/`, and `actions/` was checked for live callers outside its own file and tests.

### 7. No unused services found

Every reviewed class (including less-obvious candidates such as `OctaneGateExecutors`, `OctanePollRefreshCoordinator`, `OctaneReportDataMapper`, `OctaneRiskHeatMapRenderer`, `OctaneSpaceMappingResolver`, `OctaneTestMetricsRenderer`, `CriteriaEmailTranslator`, `OctaneCronSchedule`, `DefectDescriptionFormatter`, `OctaneQueryValidator`, `OctaneServerUrl`, and `TesterIdentityResolver`) has at least one production caller in `src/main`. There are no whole-class deletions available without breaking functionality.

### 8. Medium — `OctaneGateReportSnapshot` repeats its full constructor call in nine "with" methods

**Location:** `src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/models/OctaneGateReportSnapshot.java:385-613`

`withTestMetrics`, `withDefectTrend`, `withRiskHeatMap`, `withTesterThresholds`, `withTestManagement`, `withState`, `withDefinedScope`, `withSuiteAttributions`, and `withGraphTitles` each re-invoke the same 21-argument constructor, changing only the one or two fields the method name implies. The other ~19 arguments are copy-pasted identically across all nine methods (~230 lines total).

**Mitigation:** introduce one private all-fields copy method (or a small builder) that the nine public `with*` methods delegate to, passing only the field(s) they change. This is a pure mechanical refactor — each method's return value is unchanged — and removes roughly 150-180 lines with no behavior change. Recommended before any unrelated edits to this class, since the duplication also creates risk of a future field being added to the constructor but missed in one of the nine call sites.

### 9. Low — `createElement`/`createSvgElement` are duplicated between the two dashboard scripts

**Location:** `src/main/webapp/js/octane-scale-report.js:31-48` and `src/main/webapp/js/octane-test-management.js:98-111`

Both files define nearly identical `createElement()` and `createSvgElement()` helpers (the scale-report version additionally accepts an optional `text` argument). Combined, this is roughly 20 duplicate lines.

**Mitigation:** low priority — consolidating would require a shared module and a load-order dependency between two scripts that are currently independently self-contained. Only worth doing if a third script needs the same helpers.

### Out of scope

`examples/Jenkinsfile`, `Jenkinsfile2`, `Jenkinsfile3`, and `Jenkinsfile4` share structure but are user-facing usage examples, not shipped plugin code; removing or merging them would reduce repository LOC but not plugin LOC, and risks deleting a documented scenario a user may be following.



LOC Report:
