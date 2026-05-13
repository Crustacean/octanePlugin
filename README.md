# Octane Suite Gate

Jenkins plugin that waits for an existing ALM Octane suite run to reach a configured
quality gate before the next Pipeline stage or Freestyle build step proceeds.

## Pipeline

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: '1196,1200',
  criteria: '100% execution AND 95% pass OR payments.pass == 100%',
  scopes: [
    octaneGateScope(name: 'payments', query: 'test={((product_areas={id=1004||id=1005}))}')
  ],
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  markUnstable: false
)
```

Scope queries are ALM Octane REST API query fragments that the plugin applies to the suite
run's child runs. To filter runs by a test's application module, use the `product_areas`
relationship on `test`. Replace `1004` and `1005` with the product area/application module
IDs from your workspace. You can list product areas with:

```text
GET https://your-octane-host/api/shared_spaces/<space_id>/workspaces/<workspace_id>/product_areas?fields=id,name
```

When `suiteRunId` contains multiple IDs, the plugin polls each suite run and combines their
child runs into one global metric set. When one scope query contains multiple product area IDs,
for example `id=1004||id=1005`, Octane returns the union of matching child runs and the plugin
stores the combined metrics under that scope name. Criteria such as `payments.passRate == 100`
therefore evaluates against the combined `payments` scope, not each product area individually.
Create separate scope names if each product area needs its own criteria term.
The Pipeline return map includes `suiteRunIds`, `metrics`, and `scopes`; for example,
`gateResult.scopes.payments.passRate` is the combined pass rate for every run matched by
the `payments` scope query.

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

## Sample Jenkinsfile

A full example with a manual `input` confirmation before the Octane gate lives in
[examples/Jenkinsfile](examples/Jenkinsfile).

## Local Development

```bash
mvn spotless:check test
mvn hpi:run
```

Use Java 17 for Jenkins plugin development. If your system Maven reports
`Unknown packaging: hpi`, use a Jenkins-plugin-compatible Maven distribution rather than a
distribution-packaged Maven runtime that does not load the HPI lifecycle correctly.
