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

## Local Development

```bash
mvn spotless:check test
mvn hpi:run
```

Use Java 17 for Jenkins plugin development. If your system Maven reports
`Unknown packaging: hpi`, use a Jenkins-plugin-compatible Maven distribution rather than a
distribution-packaged Maven runtime that does not load the HPI lifecycle correctly.
