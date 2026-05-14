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

- `OctaneSuiteGateConfiguration`: global Jenkins configuration section.
- `OctaneServer`: repeatable Octane server configuration entries.
- `OctaneSuiteGateStep`: Pipeline step named `octaneSuiteGate`.
- `OctaneSuiteGateBuilder`: Freestyle build step named `ALM Octane Suite Gate`.
- `OctaneGateScope`: nested scope object named `octaneGateScope`.

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
  criteria: 'executionRate == 100 AND passRate >= 95',
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  markUnstable: false
)
```

`suiteRunId` accepts either a single ID or a comma/space-separated list. Multiple
suite runs are aggregated into one global metrics set.

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
6. The runner polls until the gate passes, fails, or times out.
7. Each poll fetches suite child runs and computes metrics.
8. Optional scopes fetch filtered child-run subsets and compute scoped metrics.
9. `CriteriaExpression` evaluates the criteria against global and scoped metrics.
10. Jenkins continues on pass, fails on terminal gate failure, or marks unstable
    when `markUnstable` is enabled.

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
4. Computes one global `GateMetrics` object from the combined child runs.

The Pipeline return map includes both:

- `suiteRunId`: original string value.
- `suiteRunIds`: parsed list of suite run IDs.

## Scopes

Scopes are named Octane query fragments applied to the combined child-run set.

Example:

```groovy
octaneGateScope(
  name: 'critical',
  query: 'test={((product_areas={id=1004||id=1005}))}'
)
```

For this scope, the plugin fetches child runs whose related test belongs to
product area `1004` or `1005`. The matching runs are combined into one metrics
bucket named `critical`.

Criteria references the bucket by name:

```text
critical.passRate == 100
```

This expression evaluates the combined `critical` metrics. It does not evaluate
each product area independently. To gate product areas separately, create
separate scopes, such as `criticalApi` and `criticalUi`, and reference both in
the criteria.

## Criteria Engine

The criteria parser supports:

- `AND`, `OR`
- parentheses
- `==`, `!=`, `>`, `>=`, `<`, `<=`
- global metrics, such as `passRate >= 95`
- scoped metrics, such as `critical.passRate == 100`
- shorthand thresholds, such as `100% execution` and `95% pass`

Default criteria:

```text
100% execution AND 100% pass
```

Criteria are evaluated on every poll. The gate passes as soon as the expression
evaluates to true.

## Terminal, Timeout, And Build Result Behavior

The plugin keeps polling while relevant runs are still running and the criteria
are false.

The gate fails when:

- all relevant global and scoped runs are terminal, and
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
  criteria: 'executionRate == 100 AND passRate >= 95',
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
| `OctaneSuiteGateConfiguration` | Jenkins global configuration root. |
| `OctaneServer` | One configured Octane server and its validation endpoints. |
| `OctaneSuiteGateStep` | Pipeline `octaneSuiteGate` step. |
| `OctaneSuiteGateBuilder` | Freestyle `ALM Octane Suite Gate` build step. |
| `GateRequest` | Runtime request created from Pipeline/Freestyle inputs. |
| `OctaneGateRunner` | Main gate loop, polling, metrics, criteria, and build decision. |
| `OctaneClient` | Low-level authenticated Octane REST client. |
| `GateMetrics` | Global or scoped computed run metrics. |
| `MetricsContext` | Resolves global and scoped metrics during criteria evaluation. |
| `CriteriaExpression` | Safe criteria parser and evaluator. |
| `GateResult` | Pipeline result map model. |
| `OctaneGateScope` | Named scoped query model. |

## Examples

- `examples/Jenkinsfile`: scoped gate with a `critical` product-area scope.
- `examples/Jenkinsfile2`: global-only gate with no scoped query.

## Verification

The test suite covers:

- criteria parsing and scoped metrics
- ID-list parsing
- Octane authentication and cookies
- workspace probing
- suite-run fallback endpoint behavior
- multi-ID scoped query forwarding
- Pipeline result map shape

Run:

```bash
mvn spotless:check test
```
