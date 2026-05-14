# Octane Suite Gate

Jenkins plugin that waits for an existing ALM Octane suite run to reach a configured
quality gate before the next Pipeline stage or Freestyle build step proceeds.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the installed-plugin design, runtime
flow, Octane API calls, metrics model, and criteria evaluation behavior.

## Pipeline

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: '1196,1200,1204',
  criteria: '(executionRate == 100 AND passRate >= 95) '
      + 'AND (critical.executionRate == 100 AND critical.passRate == 100)',
  scopes: [
    octaneGateScope(name: 'critical', suiteRunId: '1204,1210')
  ],
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  markUnstable: false
)
```

When `suiteRunId` contains multiple IDs, the plugin polls each suite run and combines their
child runs into one global metric set. A scope can also name suite run IDs. The plugin polls
those suite runs independently and stores their combined child-run metrics under that scope
name. If a suite run appears in both the global `suiteRunId` value and a scoped `suiteRunId`,
it contributes to both metric sets.
The Pipeline return map includes `suiteRunIds`, `metrics`, and `scopes`; for example,
`gateResult.scopes.critical.passRate` is the combined pass rate for every child run in
the critical suite runs. The return map also includes `scopeDetails`, `runs`, and
`suiteRuns` so logs and Pipeline code can inspect the IDs and statuses that fed each metric
bucket. For example, `gateResult.scopeDetails.critical.suiteRunIds` contains the critical
suite run IDs, and `gateResult.scopeDetails.critical.runIds` contains the Octane child run IDs
that fed the critical metrics.

Build logs start with the suite run IDs under consideration, then each poll prints compact
metric lines for the global suite runs and each suite-run-backed scope. For example:

```text
Waiting for ALM Octane suite run(s)
Global suite runs: 450312, 450309
Critical suite runs: 450306
Global suite runs: execution 0.00%, pass 0.00%, total 4, executed 0, passed 0, failed 0, skipped 0, running 4.
Critical suite runs: execution 100.00%, pass 100.00%, total 2, executed 2, passed 2, failed 0, skipped 0, running 0.

Global suite runs: execution 50.00%, pass 100.00%, total 4, executed 2, passed 2, failed 0, skipped 0, running 2.
Critical suite runs: execution 100.00%, pass 100.00%, total 2, executed 2, passed 2, failed 0, skipped 0, running 0.
```

Query-backed scopes remain supported for compatibility. Query scopes are ALM Octane REST API
query fragments applied to the global suite runs' child runs:

```groovy
octaneGateScope(name: 'legacyArea', query: 'test={((product_areas={id=1004}))}')
```

Configure Octane servers from **Manage Jenkins > System**. Store Octane API keys as
Jenkins username/password credentials, with the username as `client_id` and the password as
`client_secret`.

## Configuration

The plugin resolves the ALM Octane connection from Jenkins global configuration and then lets
each Pipeline select a configured server by `serverId`.

### 1. Create Octane API credentials in Jenkins

1. Go to **Manage Jenkins > Credentials**.
2. Add a **Username with password** credential.
3. Set:
   - **Username** to the Octane `client_id`
   - **Password** to the Octane `client_secret`
4. Save it with a stable ID such as `octane-api-prod`.

### 2. Configure the Octane server

1. Go to **Manage Jenkins > System**.
2. Find **Octane Suite Gate by Embiti**.
3. Add a server entry with:
   - **Server ID**: a logical name used from pipelines, for example `octane-prod`
   - **Base URL**: the Octane server root, for example `https://octane.example.com`
   - **Default shared space ID**: the shared space that contains the suite run data
   - **Default workspace ID**: the workspace inside that shared space
   - **API key credentials**: the Jenkins credential created in the previous step
4. Click **Test Base URL** to verify Jenkins can reach the URL. The result shows
   **OK** for HTTP 2xx/3xx responses and **Not OK** for HTTP 4xx/5xx responses
   or connection errors.
5. Click **Test Octane Workspace** to verify the Base URL, default shared space,
   default workspace, and selected API key credentials together. The result shows
   the test path, such as
   `https://your-octane-host/api/shared_spaces/1001/workspaces/2002/runs?fields=id&limit=1 using credentials octane-api-prod {TEST}`.

The base URL should be the host root used by Octane authentication and API requests, such as:

- `https://your-octane-host/authentication/sign_in`
- `https://your-octane-host/api/...`

### 3. Reference the server from a Jenkinsfile

Once the server is configured, the pipeline only needs the `serverId` and the suite run ID:

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: params.OCTANE_SUITE_RUN_ID,
  criteria: '100% execution AND 95% pass'
)
```

`suiteRunId` may be a single ID or a comma/space-separated list such as `1196,1200`.

### 4. Optionally override shared space and workspace per pipeline

If a job needs to point at a different Octane location than the global default, override
`sharedSpaceId` and `workspaceId` in the step:

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: params.OCTANE_SUITE_RUN_ID,
  sharedSpaceId: '1001',
  workspaceId: '2002',
  criteria: '100% execution AND 95% pass'
)
```

## Sample Jenkinsfiles

Examples with a manual `input` confirmation before the Octane gate:

- [examples/Jenkinsfile](examples/Jenkinsfile): uses a `critical` suite-run scope.
- [examples/Jenkinsfile2](examples/Jenkinsfile2): uses only global suite metrics, with no scope.

## Local Development

```bash
mvn spotless:check test
mvn hpi:run
```

Use Java 17 for Jenkins plugin development. If your system Maven reports
`Unknown packaging: hpi`, use a Jenkins-plugin-compatible Maven distribution rather than a
distribution-packaged Maven runtime that does not load the HPI lifecycle correctly.
