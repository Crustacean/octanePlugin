# Architecture

This document describes how **Octane Suite Gate by Embiti** is designed once the
plugin is installed into Jenkins.

## System View

```text
Jenkins controller
  |
  | installs target/octane-suite-gate-by-embiti.hpi
  v
Octane Suite Gate plugin
  |
  | reads Jenkins global Octane server config and credentials
  v
Pipeline step or Freestyle build step
  |
  | authenticates and polls ALM Octane through REST APIs
  v
ALM Octane suite run and child runs
  |
  | returns run statuses
  v
Gate metrics and criteria evaluation
  |
  | pass, fail, unstable, or timeout
  v
Next Jenkins stage or stopped build
```

The plugin is a gate, not a test trigger. It waits for suite runs that already
exist in ALM Octane and decides whether Jenkins may continue.

## Jenkins Installation

The plugin is packaged as an HPI file:

```text
target/octane-suite-gate-by-embiti.hpi
```

After installation, Jenkins discovers the plugin extensions through Jenkins
annotations:

- `configs.OctaneSuiteGateConfiguration`: global Jenkins configuration section.
- `configs.OctaneServer`: repeatable Octane server configuration entries.
- `controllers.OctaneSuiteGateStep`: Pipeline step named `octaneSuiteGate`.
- `controllers.OctaneSuiteGateBuilder`: Freestyle build step named `ALM Octane Suite Gate`.
- `models.OctaneGateScope`: nested scope object named `octaneGateScope`.

The Java sources are organized under the base package into focused folders:

- `actions`: per-build Jenkins report actions and chart pages.
- `configs`: global Jenkins configuration and Octane server definitions.
- `controllers`: Pipeline and Freestyle entry points.
- `entities`: ALM Octane API record shapes.
- `listeners`: Jenkins build-log output helpers.
- `models`: gate request, result, metrics, scope, and status models.
- `repositories`: low-level ALM Octane REST API access.
- `services`: gate orchestration and criteria evaluation.
- `utils`: shared string and parsing helpers.

## Configuration Model

Administrators configure Octane servers in:

```text
Manage Jenkins > System > Octane Suite Gate by Embiti
```

Each server entry contains:

- `serverId`: logical name used by jobs, such as `octane-prod`.
- `baseUrl`: Octane host root, such as `https://octane.example.com`.
- `sharedSpaceId`: default ALM Octane shared space ID.
- `workspaceId`: default ALM Octane workspace ID.
- `credentialsId`: Jenkins username/password credential ID.

The Jenkins credential maps to Octane API key authentication:

- Jenkins username: Octane `client_id`.
- Jenkins password: Octane `client_secret`.

The server form also exposes two validation actions:

- **Test Base URL**: performs a basic HTTP reachability check against `baseUrl`.
- **Test Octane Workspace**: authenticates with the selected credential and probes
  `/api/shared_spaces/{sharedSpaceId}/workspaces/{workspaceId}/runs?fields=id&limit=1`.

These checks validate connectivity and workspace access, but they do not validate
that a particular suite run ID exists.

## Job Entry Points

### Pipeline

Pipeline jobs call:

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: '1196,1200',
  criteria: 'regressions.executionRate == 100 AND regressions.passRate >= 95',
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  markUnstable: false
)
```

`suiteRunId` accepts either a single ID or a comma/space-separated list. Multiple
suite runs are aggregated into one regression metrics set.

### Freestyle

Freestyle jobs use the build step:

```text
ALM Octane Suite Gate
```

The Freestyle builder delegates to the same runtime request model used by the
Pipeline step, so both entry points share the same gate behavior.

## Runtime Flow

1. Jenkins reaches the `octaneSuiteGate` Pipeline step or Freestyle build step.
2. The step creates a `GateRequest`.
3. `OctaneGateRunner` resolves the configured `OctaneServer` by `serverId`.
4. The runner resolves Jenkins credentials by `credentialsId`.
5. The runner creates `OctaneClient` and signs in to Octane.
6. The step attaches an `Octane Gate Report` action to the current build.
7. The runner polls until the gate passes, fails, or times out.
8. Each poll fetches suite child runs, computes metrics, and updates the report snapshot.
9. Optional scopes fetch either separate suite-run child runs or filtered child-run subsets.
10. `CriteriaExpression` evaluates the criteria against regression and scoped metrics.
11. Jenkins continues on pass, fails on terminal gate failure, or marks unstable
    when `markUnstable` is enabled.

## Build Report

Every Pipeline and Freestyle run gets a build-side **Octane Gate Report** at:

```text
<build-url>/octaneSuiteGateReport/
```

The report is backed by a persisted Jenkins `RunAction`. It is attached before
polling starts, updated after every poll, and left on the build after pass,
failure, unstable, timeout, or unexpected error.

The report contains chart cards for regression suite runs and each scope. Cards are
resizable and can be reordered by dragging. Two cards fit per row by default;
when one card is resized wide enough, the neighboring card wraps below. The
report also shows two centered countdown donut cards: Testing Time Remaining
from `timeoutMinutes`, and Status Check from `pollIntervalSeconds`. Timer
ring movement uses browser animation frames for smooth millisecond-based motion,
while the center text remains rounded to minutes or seconds. Timer SVGs render at
a higher internal resolution with geometric precision hints and a subtle progress
halo to reduce jagged circular edges. Each section renders:

- a donut chart for total Passed, Failed, Blocked, Skipped, and Running counts.
- a vertical bar chart for the same counts per suite run, with bar height
  scaled against the suite run with the most tests in that section.

The chart colors are fixed:

- Passed: `#009900`
- Failed: `#990000`
- Blocked: `#631919`
- Skipped: `#ffb74d`
- Running: `#808080`

## Octane API Flow

Authentication:

```text
POST {baseUrl}/authentication/sign_in
```

The request sends `client_id` and `client_secret`. The client remembers Octane
cookies, including `LWSSO_COOKIE_KEY`, and sends them on later API calls.

Suite run lookup:

```text
GET /api/shared_spaces/{space}/workspaces/{workspace}/runs?query="id EQ {suiteRunId}"&fields=...&limit=1
```

If the aggregate runs query is rejected or does not return a suite run, the
client falls back to:

```text
GET /api/shared_spaces/{space}/workspaces/{workspace}/suite_runs/{suiteRunId}?fields=...
```

The client sends:

```text
ALM-OCTANE-TECH-PREVIEW: true
Accept: application/json
```

Child run lookup:

```text
GET /api/shared_spaces/{space}/workspaces/{workspace}/runs?query="id EQ 101||id EQ 102"&fields=...&limit=200&offset=0
```

Scoped child run lookup:

```text
GET /api/shared_spaces/{space}/workspaces/{workspace}/runs
  ?query="(id EQ 101||id EQ 102);(test={((product_areas={id=1004||id=1005}))})"
  &fields=...
  &limit=200
  &offset=0
```

Sign out is best effort:

```text
POST {baseUrl}/authentication/sign_out
```

## Metrics Model

The plugin converts Octane run records into `GateMetrics`:

- `total`
- `executed`
- `passed`
- `failed`
- `skipped`
- `running`
- `executionRate`
- `passRate`
- `failRate`

`executionRate` is:

```text
executed / total * 100
```

`passRate` is:

```text
passed / executed * 100
```

`failRate` is:

```text
failed / executed * 100
```

Zero-run rates evaluate to `0.0`.

## Suite Run Aggregation

When `suiteRunId` contains multiple IDs, for example:

```text
1196,1200
```

the plugin:

1. Splits the value on commas or whitespace.
2. Fetches child runs for each suite run.
3. Deduplicates child runs by run ID.
4. Computes one regression `GateMetrics` object from the combined child runs.
5. Keeps the child-run status list grouped by the original suite run ID for
   build-log and Pipeline-result diagnostics.

The Pipeline return map includes both:

- `suiteRunId`: original string value.
- `suiteRunIds`: parsed list of suite run IDs.

## Scopes

Scopes are named metric buckets. A scope can be backed by its own suite run IDs
or by an Octane query fragment applied to the regression child-run set.

Example:

```groovy
octaneGateScope(
  name: 'critical',
  suiteRunId: '450303,450204'
)
```

For this scope, the plugin fetches child runs for suite runs `450303` and
`450204`. The matching child runs are combined into one metrics bucket named
`critical`.

Scoped suite run IDs and child statuses are tracked separately from the regression
suite-run metrics. If a suite run ID appears in both the regression `suiteRunId`
input and a `critical` scoped `suiteRunId`, the critical scope owns that ID for
criteria and report calculations. It is counted in the critical bucket and excluded
from the regression bucket.

Criteria references the bucket by name:

```text
critical.passRate == 100
```

This expression evaluates the combined `critical` metrics. The criteria
expression controls whether critical metrics override or combine with regression
metrics. With `OR`, either side can pass the gate; with `AND`, both sides must
pass.

Query-backed scopes remain supported for compatibility:

```groovy
octaneGateScope(
  name: 'legacyArea',
  query: 'test={((product_areas={id=1004}))}'
)
```

Query-backed scopes are applied to the combined regression child-run set.

## Criteria Engine

The criteria parser supports:

- `AND`, `OR`
- parentheses
- `==`, `!=`, `>`, `>=`, `<`, `<=`
- regression metrics, such as `regressions.passRate >= 95`
- scoped metrics, such as `critical.passRate == 100`
- shorthand thresholds, such as `100% execution` and `95% pass`

Default criteria:

```text
100% execution AND 100% pass
```

Criteria are evaluated on every poll. The gate passes as soon as the expression
evaluates to true.

Unqualified regression metrics and shorthand expressions remain supported for
backward compatibility, but new Jenkinsfiles should prefer `regressions.executionRate`
and `regressions.passRate` for readability.

## Terminal, Timeout, And Build Result Behavior

The plugin keeps polling while relevant runs are still running and the criteria
are false.

The gate fails when:

- all relevant regression and scoped runs are terminal, and
- the criteria still evaluate to false.

The gate times out when:

- `timeoutMinutes` is reached before pass or terminal failure.

When `markUnstable` is false, gate failure stops the build. When `markUnstable`
is true, the build result is set to `UNSTABLE` and the step returns the latest
gate result map.

## Pipeline Result Map

On success, and on unstable gate failure when `markUnstable` is true, Pipeline
receives a map shaped like:

```groovy
[
  suiteRunId: '1196,1200',
  suiteRunIds: ['1196', '1200'],
  criteria: 'regressions.executionRate == 100 AND regressions.passRate >= 95',
  passed: true,
  terminal: true,
  polledAt: '2026-05-13T00:00:00Z',
  metrics: [
    total: 10,
    executed: 10,
    passed: 10,
    failed: 0,
    skipped: 0,
    running: 0,
    executionRate: 100.0,
    passRate: 100.0,
    failRate: 0.0
  ],
  regressions: [
    total: 10,
    executed: 10,
    passed: 10,
    failed: 0,
    skipped: 0,
    running: 0,
    executionRate: 100.0,
    passRate: 100.0,
    failRate: 0.0
  ],
  scopes: [
    critical: [
      total: 4,
      executed: 4,
      passed: 4,
      failed: 0,
      skipped: 0,
      running: 0,
      executionRate: 100.0,
      passRate: 100.0,
      failRate: 0.0
    ]
  ],
  scopeDetails: [
    critical: [
      name: 'critical',
      query: '',
      queryIds: [],
      suiteRunId: '450303,450204',
      suiteRunIds: ['450303', '450204'],
      runIds: ['101', '102', '103', '104'],
      metrics: [
        total: 4,
        executed: 4,
        passed: 4,
        failed: 0,
        skipped: 0,
        running: 0,
        executionRate: 100.0,
        passRate: 100.0,
        failRate: 0.0
      ],
      runs: [
        [id: '101', name: 'critical api', status: 'passed']
      ],
      suiteRuns: [
        '450303': [
          [id: '101', name: 'critical api', status: 'passed']
        ],
        '450204': [
          [id: '104', name: 'critical ui', status: 'passed']
        ]
      ]
    ]
  ],
  suiteRuns: [
    '1196': [
      [id: '101', name: 'critical api', status: 'passed']
    ],
    '1200': [
      [id: '104', name: 'critical ui', status: 'passed']
    ]
  ]
]
```

## Security

The plugin never logs the Octane client secret. Credentials are resolved through
Jenkins Credentials APIs and used only to authenticate against Octane.

Connectivity validation and credential listing require Jenkins administrator
permission.

HTTP failure messages include request URI, status code, and a bounded response
body to help diagnose schema, workspace, and suite-run errors. Request bodies
containing API secrets are not logged.

## Key Classes

| Class | Responsibility |
| --- | --- |
| `actions.OctaneGateReportAction` | Per-build chart report and live snapshot holder. |
| `configs.OctaneSuiteGateConfiguration` | Jenkins global configuration root. |
| `configs.OctaneServer` | One configured Octane server and its validation endpoints. |
| `controllers.OctaneSuiteGateStep` | Pipeline `octaneSuiteGate` step. |
| `controllers.OctaneSuiteGateBuilder` | Freestyle `ALM Octane Suite Gate` build step. |
| `models.GateRequest` | Runtime request created from Pipeline/Freestyle inputs. |
| `services.OctaneGateRunner` | Main gate loop, polling, metrics, criteria, and build decision. |
| `repositories.OctaneClient` | Low-level authenticated Octane REST client. |
| `models.GateMetrics` | Regression or scoped computed run metrics. |
| `models.GateScopeResult` | Scoped suite/query source, matched run statuses, and scoped metrics. |
| `models.MetricsContext` | Resolves regression and scoped metrics during criteria evaluation. |
| `services.CriteriaExpression` | Safe criteria parser and evaluator. |
| `models.GateResult` | Pipeline result map model. |
| `models.OctaneGateScope` | Named scoped suite-run or query model. |
| `models.OctaneGateReportSnapshot` | Report sections, pie data, and bar data for the build page. |

## Examples

- `examples/Jenkinsfile`: scoped gate with a `critical` suite-run scope.
- `examples/Jenkinsfile2`: regression-only gate with no scoped query.

## Verification

The test suite covers:

- criteria parsing and scoped metrics
- ID-list parsing
- Octane authentication and cookies
- workspace probing
- suite-run fallback endpoint behavior
- multi-ID scoped query forwarding
- suite-run-backed scoped metrics
- Octane Gate Report chart snapshots and Jenkins build-page rendering
- Pipeline result map shape

Run:

```bash
mvn spotless:check test
```
