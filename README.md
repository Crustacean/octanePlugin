# Octane Suite Gate

Jenkins plugin that waits for an existing ALM Octane suite run to reach a configured
quality gate before the next Pipeline stage or Freestyle build step proceeds.

## Pipeline

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  suiteRunId: '1196',
  criteria: '100% execution AND 95% pass OR payments.pass == 100%',
  scopes: [
    octaneGateScope(name: 'payments', query: "test EQ {product_area EQ {id EQ 2001}}")
  ],
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  markUnstable: false
)
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
