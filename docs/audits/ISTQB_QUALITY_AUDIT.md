# ISTQB Quality And Code Coverage Audit

Date: 2026-07-30

## Alignment

- CT-SEC: trust boundaries, credential handling, input abuse, SSRF, authorization, and safe errors.
- CT-PT: concurrency, percentiles, saturation, cleanup, failure recovery, and endurance testing.
- CTAL-TTA: white-box decisions, complexity, coverage instrumentation, and maintainability.

## Structural Remediation

PMD 7.17 `CyclomaticComplexity` results for production methods:

| Measure | Before this audit | After |
| --- | ---: | ---: |
| Methods with complexity greater than 10 | 17 | 0 |
| Highest reported method complexity | 17 | None above threshold |

The refactor split metric dispatch, validation, risk-map assembly, failure clustering, tester defect
mapping, suite topology assembly/fallbacks, HTTP retry disposition, recursive person parsing,
browser validation, attachment validation, email style selection, cache failure propagation, and
artifact filtering into characterized helpers. Public contracts and request/cache boundaries were
not changed.

## Coverage

JaCoCo counters from the clean 287-test run:

| Counter | Covered | Total | Coverage |
| --- | ---: | ---: | ---: |
| Instructions | 31,266 | 38,826 | 80.53% |
| Lines | 6,875 | 8,838 | 77.79% |
| Branches | 2,297 | 3,548 | 64.74% |
| Methods | 1,408 | 1,795 | 78.44% |
| Classes | 146 | 158 | 92.41% |

JaCoCo branch coverage is decision evidence, not formal MC/DC. MC/DC intent is supplied by explicit
truth-table tests for criteria AND/OR/grouping, defect percentages/counts, disabled or changing suite
buckets, suite-source fallback, primary/extended timeout, retry classification, automation targets,
and progress-email state transitions. Formal safety-critical MC/DC certification would require a
dedicated instrumentation tool and requirement-to-condition traceability outside this repository.

## Test Evidence

- Java: 287 tests, zero failures, zero errors, one default skip for the opt-in 24-hour soak.
- Frontend: 65 Node/browser tests, zero failures, including real Chromium and Firefox geometry.
- Static analysis: SpotBugs zero findings; PMD zero methods over complexity 10; Spotless clean.
- Security: OSV 2.3.8 has no unresolved finding after documented, expiring provided-dependency
  exceptions.
- Endurance: enabled one-minute 500-job soak passed after exposing and fixing a scheduler race.

## Browser And UX Matrix

| Engine/environment | Evidence | Status |
| --- | --- | --- |
| Chromium/Blink | Compact/desktop, normal/focused/expanded geometry, clipping and scale checks | Passed |
| Firefox/Gecko | Five viewports, normal/focused/expanded geometry and interaction checks | Passed |
| Safari/WebKit | Requires supported macOS/Safari runner | External release gate |
| Failure recovery | Last-good data, connection status, stale-request cancellation, bounded retry | Passed automated checks |
| 4x CPU and long-session memory profile | Requires browser performance tooling on representative clients | External release gate |

JUnit Vintage deprecation messages and Jenkins harness native-access/Bouncy Castle initialization
messages are test-environment warnings. They did not fail tests, but migrating remaining JUnit 4
tests and tracking upstream harness compatibility are maintainability actions.
