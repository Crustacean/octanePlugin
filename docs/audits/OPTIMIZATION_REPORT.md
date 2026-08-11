# Optimization Changes Report

Audit date: 2026-07-24

## Architectural Baseline

The implementation remains aligned with `SCALE_ARCHITECTURE_PROPOSAL.md`:

- Report state is persisted as bounded atomic JSON artifacts.
- Large chart sections are paged and client rendered.
- HTTP requests use bounded per-server coordination.
- Topology data uses namespace-aware TTL caching and single-flight loading.
- Snapshot refresh uses checksums/ETags and avoids rebuilding unchanged content.
- Bar capacity is capped at 80 while preserving a 24-pixel overflow indicator.

The audit strengthened these paths rather than introducing a second architecture.

## Backend Changes

### Complexity And Reuse

- Replaced branch-heavy cron descriptions with reusable field matching and schedule-format helpers.
- Replaced the monolithic criteria tokenizer with a bounded scanner that owns operator, number,
  identifier, and token-limit handling.
- Replaced duplicated severity open/closed counters with ordinal-indexed aggregation.
- Added a deterministic clock seam to the topology cache so TTL boundaries can be proven without
  sleeps or flaky timing.

### Concurrency And Isolation

- Verified the request coordinator never exceeds its configured per-server permit count.
- Verified permits return after all requests finish and in-flight metrics return to zero.
- Verified refresh followers share one outcome, including failure propagation.
- Verified late completion from an old owner cannot overwrite a newer polling generation.
- Verified topology cache keys isolate identical suite IDs across server/workspace namespaces.

### Large Data Sets

- Increased dense report coverage to 701 suites and 105,150 child runs.
- Exercised all nine pages at an 80-bar limit and validated every cursor transition.
- Retained bounded pipeline maps, report indexes, complete artifacts, and initial DOM output.
- Kept existing 40-ID topology request chunks, 30-second TTL, 20,000-entry LRU bound, and
  single-flight ownership defined by the scale architecture.

## Frontend Changes

### Rendering And CPU

- Coalesced repeated test-management polling updates into one animation-frame render.
- Preserved immediate initial rendering while batching subsequent updates.
- Kept safe DOM construction (`textContent`/element APIs) and delegated chart interactions.
- Centralized shared system colors and semantic status/severity aliases in one theme token set.

### Network And Lifecycle

- Added `AbortController` cancellation for stale report-index and paged-section requests.
- Replayed the latest resize/page demand after an in-flight section request completes.
- Ignored stale generations and detached cards before applying asynchronous responses.
- Retained observer/timer cleanup through the existing per-zone disposal lifecycle.
- Used feature detection and fallback behavior for older supported browser paths.

### Responsive Output

- Preserved proportional SVG text with `preserveAspectRatio="xMidYMid meet"`.
- Verified normal, focused, and expanded timer/chart layouts at four viewport classes.
- Preserved the 80-bar cap, dynamic bar width, and concise 24-pixel overflow region.

## Deliberately Unchanged

- No new cache layer was added without a measured need.
- No global executor, defect polling, report schema, or public Jenkinsfile contract was replaced.
- No unsafe HTML rendering or third-party chart runtime was introduced.
- Existing security/resource ceilings in `SECURITY_AND_RESILIENCY_AUDIT.md` remain intact.

