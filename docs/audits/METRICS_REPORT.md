# Metrics Before/After Report

Audit date: 2026-07-24

## Scope And Method

The baseline was captured before this audit and the final sample after implementation. Logical
code metrics use Lizard 1.23.0 over production Java and JavaScript:

```text
lizard -l java -l javascript --CCN 10 --length 60 src/main/java src/main/webapp/js
```

Physical lines use `wc -l`. Generated build output and the Markdown audit reports are excluded.
The review thresholds are project quality gates, not claims of an official universal standard:

- Average cyclomatic complexity (CCN): target at or below 5.
- Individual function CCN: review above 10; mandatory redesign discussion at 16 or above.
- Function length: review above 60 lines.
- Automated builds: zero test, formatting, and static-analysis failures.

## Production Code

| Metric | Before | After | Change | Assessment |
| --- | ---: | ---: | ---: | --- |
| Logical NLOC | 16,274 | 16,359 | +85 | Small increase for cancellation/lifecycle safety |
| Java NLOC | 14,821 | 14,806 | -15 | Reduced despite added cache seam |
| JavaScript NLOC | 1,453 | 1,553 | +100 | Added request cancellation and render coalescing |
| Functions | 1,540 | 1,562 | +22 | Smaller extracted units replaced complex routines |
| Average CCN | 2.1 | 2.1 | 0 | Within the target |
| Review warnings | 24 | 20 | -4 (-16.7%) | Improved; remaining debt is documented |
| CCN 16+ audit hotspots | 3 | 0 | -3 | Mandatory-refactor threshold cleared |

The three severe baseline hotspots were:

- `OctaneCronSchedule.describe`, CCN 32.
- `CriteriaExpression.tokenize`, CCN 24.
- `OctaneDefectSeveritySummary.fromDefects`, CCN 16.

They were replaced by focused helpers/stateful scanners while preserving public behavior.

## Physical Lines

| Area | Before | After | Change |
| --- | ---: | ---: | ---: |
| Main Java | 16,606 | 16,600 | -6 |
| Main resources | 8,037 | 8,020 | -17 |
| Main JavaScript | 1,537 | 1,645 | +108 |
| Test Java | 7,538 | 7,815 | +277 |
| Test JavaScript | 1,788 | 1,818 | +30 |
| Total | 35,506 | 35,898 | +392 |

Production physical lines increased by 85 (0.32%). Test lines increased by 307 to cover exact
cache TTL boundaries, parser limits, state races, per-server concurrency, pagination above 700
suites, request cancellation, and frame coalescing. The audit therefore reduced duplicated Java,
Jelly, and color declarations but did not manufacture a lower total by withholding safety code.

## Scale Measurements

The measured acceptance fixture completed successfully:

| Workload/metric | Result | Gate |
| --- | ---: | ---: |
| Concurrent jobs | 30 | 30 |
| Suites per job | 500 | 500 |
| Child runs per job | 25,000 | 25,000 |
| Defects per job | 1,000 | 1,000 |
| Total elapsed | 1,669 ms | under 60,000 ms |
| Peak used heap | 644,485,216 bytes | under 1 GiB |
| Largest index JSON | 162,055 bytes | under 250,000 bytes |
| Largest complete JSON | 695,306 bytes | under 5,000,000 bytes |

A separate boundary fixture mapped 701 suites with 150 children each (105,150 child runs) into
bounded paged artifacts. The Jenkins action fixture measured a 4,035-byte build XML, 825 initial
DOM nodes, and a 1,445-byte index response.

## Verification Totals

- Java: 212 tests passed.
- JavaScript and browser: 49 tests passed.
- Browser layout: headless Firefox at 360, 768, 1,440, and 2,560 pixel widths.
- Spotless: passed.
- SpotBugs: zero bugs and zero analysis errors.

