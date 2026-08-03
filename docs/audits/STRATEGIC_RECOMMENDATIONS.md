# Strategic Recommendations

Audit date: 2026-07-24

## Priority 0: Keep The New Gates Mandatory

- Run all 212 Java tests and 49 JavaScript/browser tests on pull requests.
- Keep Spotless, SpotBugs, dependency convergence, OWASP Dependency Check, and OSV scanning as
  blocking checks.
- Add the Lizard command from `METRICS_REPORT.md` to CI with a no-new-warning policy. Existing
  warnings should be burned down separately rather than blocking unrelated delivery.
- Retain the 30-job and >700-suite acceptance fixtures as release tests.

## Priority 1: Measure Real Jenkins Workloads

- Capture JFR and GC logs during an eight-hour soak with at least 30 concurrent jobs.
- Track poll latency percentiles, queue wait, cache hit/miss ratio, artifact bytes, client fetch
  time, and screenshot duration as Jenkins metrics.
- Run controlled Octane latency, timeout, 429, 5xx, malformed-response, and connection-reset
  experiments. Confirm permits, single-flight owners, and email locks always release.
- Compare heap behavior with 500, 701, and 1,500 suites before changing current cache or paging
  limits.

## Priority 1: Broaden Browser And Accessibility Coverage

- Keep the current real Firefox layout test and add Chromium, Edge, and WebKit/Safari-compatible
  CI runs.
- Add axe-core checks for each report face in light/dark, normal/focused, and terminal/running
  states.
- Record Core Web Vitals or equivalent render/interaction timings for the Jenkins report zone,
  especially INP during polling and chart switching.

## Priority 2: Reduce Remaining Structural Debt

- Split `OctaneReportZoneHtmlRenderer.appendStyle` into versioned reusable style resources.
- Decompose `renderBarChart`, `renderBarCard`, artifact filtering, polling, and test-management
  categorization when those areas next change.
- Move remaining long Jelly script regions into tested static modules without changing Stapler
  endpoint or CSP behavior.
- Migrate JUnit Vintage tests incrementally to JUnit Jupiter; the current deprecation warning is
  upstream-compatible today but should not become permanent debt.

## Priority 2: Deepen Test Effectiveness

- Add mutation testing for criteria comparisons, defect composition, timer terminal transitions,
  and cache expiration.
- Fuzz criteria expressions and Octane JSON reference shapes under the existing size limits.
- Add persistence/restart tests that terminate Jenkins between artifact write phases and during
  joined polls.
- Test controller clock changes and daylight-saving transitions for cron windows and interval
  email scheduling.

## Operating Guardrails

- Do not raise request concurrency, cache bounds, response limits, or artifact limits without a
  recorded load test and rollback threshold.
- Do not cache credentials, mutable Jenkins objects, or unbounded Octane entities.
- Keep cache namespaces keyed by server/shared-space/workspace to prevent cross-job contamination.
- Prefer bounded pagination and backpressure over larger in-memory snapshots.

