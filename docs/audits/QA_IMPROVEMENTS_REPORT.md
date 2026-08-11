# QA Improvements Report

Audit date: 2026-07-24

## ISTQB-Oriented Coverage

### Boundary Value Analysis

- Criteria nesting accepts the maximum depth of 64.
- Criteria tokenization accepts 1,023 tokens and rejects the first over-limit equivalent case.
- Cron configuration rejects blank input and six fields while accepting exactly five.
- Topology cache hits at TTL minus one nanosecond and refreshes exactly at the TTL boundary.
- Scale actions paginate 701 bars at the maximum visible limit of 80.
- Existing graph tests cover zero density, fractional density, and values through 3,000 defects.

### Equivalence Partitioning

- Valid/invalid criteria, cron, URL, status, severity, and defect-group inputs remain covered.
- Empty, single-item, compact, and dense chart/report partitions are exercised.
- Cache tests separate same-namespace hits from cross-server/workspace isolation.
- Responsive tests cover compact mobile, tablet, desktop, and wide desktop partitions.

### State Transition Testing

- Poll refresh tests cover owner, follower, failure, completion, and new-owner transitions.
- Late completion from an obsolete generation is ignored.
- Frontend requests cover initial load, unchanged checksum, replacement request, abort, retry, and
  detached-element states.
- Existing tests cover running, extended time, manual-exit pending, final, and timed-out report
  states.

### Concurrency And Load

- A local HTTP server test drives six concurrent requests through a two-permit coordinator.
- Thirty dense jobs map concurrently without exceeding artifact or elapsed-time gates.
- A separate >700-suite fixture verifies 105,150 child-run handling.
- Single-flight and namespace-aware cache behavior are deterministic and sleep-free.

## UI And Accessibility

- All 49 JavaScript tests pass.
- Headless Firefox confirms timer graphs are visible and bounded in normal, focused, and expanded
  modes at 360x640, 768x900, 1440x900, and 2560x1440.
- SVG charts retain image roles/labels, buttons retain accessible names, and hidden data summaries
  remain available to non-visual consumers.
- Safe element creation and text assignment are asserted; no client report path uses `innerHTML`.

## Security And Resilience

- SpotBugs 4.9.8.2 reports zero bug instances and zero analysis errors.
- Jenkins report and manual-exit action tests remain green, including POST-only manual exit.
- Existing URL/query validation, filtered deserialization, response-size limits, screenshot bounds,
  endpoint permissions, and email-delivery locks were not weakened.
- Maven dependency convergence, banned-dependency, release-dependency, and bytecode enforcer rules
  pass during the full build.

## Final Test Result

| Suite | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Java/Jenkins harness | 212 | 0 | 0 |
| JavaScript/browser | 49 | 0 | 0 |
| Total | 261 | 0 | 0 |

Spotless and `git diff --check` are separate release gates. Spotless passed after applying the
repository formatter. The clean HPI build result is recorded in the final implementation handoff.

