# Octane Suite Gate

Jenkins plugin that waits for an existing ALM Octane suite run to reach a configured
quality gate before the next Pipeline stage or Freestyle build step proceeds.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the installed-plugin design, runtime
flow, Octane API calls, metrics model, and criteria evaluation behavior.

## Requirements

- Jenkins `2.568.1` or newer
- Java 21 or newer for the Jenkins controller, agents, and plugin development

## Pipeline

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  sharedSpaceId: '1001',
  workspaceId: '2002',
  suiteRunId: '1196,1200,1204',
  defectGroups: [
    octaneDefectGroup(
        name: 'major',
        types: 'Critical, Very High, High, Unspecified'),
    octaneDefectGroup(name: 'minor', types: 'Low, Medium')
  ],
  criteria: '(regressions.executionRate == 100 AND regressions.passRate >= 95) '
      + 'AND (critical.executionRate == 100 AND critical.passRate == 100) '
      + 'AND (defects.major < 10% AND defects.minor < 20%) '
      + 'AND (defects.Unspecified == 0%)',
  scopes: [
    octaneGateScope(name: 'critical', suiteRunId: '1204,1210')
  ],
  pollIntervalSeconds: 30,
  timeoutMinutes: 120,
  timeoutMinutesExtended: 30,
  markUnstable: false
)
```

### Defect criteria

`defectGroups` gives a case-insensitive name to one or more open ALM Octane defect
severities. Supported types are `Critical`, `Very High`, `High`, `Medium`, `Low`, and
`Unspecified`. Group names and type values are case-insensitive, so `defects.MAJOR` and
`defects.major` resolve to the same group. Individual severities are always available without
creating a group; write `VeryHigh`, `very_high`, or `very-high` for the two-word severity.

`defects.<name>` is the number of matching open defects divided by the total defects raised,
multiplied by 100. Total defects raised is deduplicated by defect ID and includes both open and
closed defects retained in the gate's defect ledger. Closing a defect therefore lowers its open
severity rate without lowering the denominator. Append `Count` for a raw open defect count, such
as `defects.majorCount < 3` or `defects.UnspecifiedCount == 0`.

A severity can be included in a group and referenced individually. For example, if `major`
contains `Unspecified`, one open unspecified defect contributes to both `defects.major` and
`defects.Unspecified` when both expressions are present. These are independent views of the
same defect data: the defect is not duplicated inside either metric or in report totals.

Defect criteria trigger defect polling even when `riskHeatMap` is `false`. If current defect
data cannot be fetched, the gate stops with a clear evaluation error rather than treating the
missing data as zero.

When `suiteRunId` contains multiple IDs, the plugin polls each suite run and combines their
child runs into one regression metric set. A scope can also name suite run IDs. The plugin polls
those suite runs independently and stores their combined child-run metrics under that scope
name. If a suite run appears in both the regression `suiteRunId` value and a scoped `suiteRunId`,
the scoped suite run owns that ID for criteria and report calculations. For a `critical` scope,
that means the ID contributes to the critical bucket and is excluded from the regression bucket.
The Pipeline return map includes `suiteRunIds`, `metrics`, `regressions`, and `scopes`; for example,
`gateResult.scopes.critical.passRate` is the combined pass rate for every child run in
the critical suite runs. The return map also includes `scopeDetails`, `runs`, and
`suiteRuns` so logs and Pipeline code can inspect the IDs and statuses that fed each metric
bucket. For example, `gateResult.scopeDetails.critical.suiteRunIds` contains the critical
suite run IDs, and `gateResult.scopeDetails.critical.runIds` contains the Octane child run IDs
that fed the critical metrics.

As an alternative to explicit IDs, `suiteRunId` and suite-run-backed scopes accept either a release
name or two comma-separated names in the form `Release Name, Sprint Name`. A release name by itself
supports Kanban workspaces and discovers every suite run attached to that release without applying
a sprint filter. The plugin resolves matching suite runs during preflight and repeats discovery on
every poll. Newly assigned suite runs join the metric pool automatically; deleted or unreachable
runs are removed with an audit log entry. If preflight finds no matches, the gate keeps polling
until its timeout and logs that the native Jenkins Abort/Cancel action can stop the build.

If critical ownership removes every regression suite run, regression comparisons are removed
from the logical criteria expression before evaluation. The effective expression is stored in
`gateResult.criteria` and used by the report and email. Consequently, `{{CRITERIA}}` and the
criteria evaluation table show only rules that were actually evaluated; they do not repeat
regression rules merely because those rules were present in the Jenkinsfile.

Build logs start with the suite run IDs under consideration, then each poll prints compact
metric lines for the regression suite runs and each suite-run-backed scope. For example:

```text
Waiting for ALM Octane suite run(s)
Regressions suite runs: 450312, 450309
Critical suite runs: 450306
Regressions suite runs: execution 0.00%, pass 0.00%, total 4, executed 0, passed 0, failed 0, skipped 0, running 4.
Critical suite runs: execution 100.00%, pass 100.00%, total 2, executed 2, passed 2, failed 0, skipped 0, running 0.

Regressions suite runs: execution 50.00%, pass 100.00%, total 4, executed 2, passed 2, failed 0, skipped 0, running 2.
Critical suite runs: execution 100.00%, pass 100.00%, total 2, executed 2, passed 2, failed 0, skipped 0, running 0.
```

Each build also gets an **Octane Gate Report** link. The report refreshes while the gate is
polling and remains available after the build finishes. It renders a resizable, draggable
countdown donut for testing time remaining, a Status Check countdown donut, a donut
chart for the total status distribution, and a vertical bar chart for each suite run. Suite-run
bar heights are relative to the suite run with the most tests in that section. The
countdown donuts animate smoothly between displayed second/minute changes and use high-resolution
SVG rings with a subtle halo stroke to reduce jagged circular edges. Status colors are:

- Passed: `#009900`
- Failed: `#990000`
- Blocked: `#631919`
- Skipped: `#ffb74d`
- Running: `#808080`

`timeoutMinutesExtended` is optional and defaults to `0`. When it is `0`, the gate keeps the
standard behavior. When it is greater than `0`, the gate continues polling after the primary
`timeoutMinutes` window and does not leave the stage just because execution reaches `100%`.
During this extended time, the report shows an **Exit Octane and Continue** button. That button
stops waiting early, but it does not bypass the configured criteria; the latest Octane data is
still evaluated before the build proceeds, fails, or becomes unstable.

To email the report image in a later Pipeline stage, use `octaneEmailReport` after
`octaneSuiteGate`:

```groovy
octaneEmailReport(
  to: 'qa-team@example.com,dev-team@example.com',
  cc: 'qa-leads@example.com',
  bcc: 'qa-audit@example.com',
  projectName: 'Business Payments Secure Checkout',
  domainName: 'FS_TRIBE_DOMAIN',
  from: 'jenkins-notifications@example.com',
  replyTo: 'qa-team@example.com',
  subject: "Octane Gate Report - ${env.JOB_NAME} #${env.BUILD_NUMBER}",
  body: '''Hello Team,

The automated job for {{PROJECT_NAME}} tests has run and is {{GATE_RESULT}}.

Set criteria: {{CRITERIA}}

Click here to {{REPORT_LINK}}.

See below the execution details:

{{EXECUTION_DETAILS}}

{{REPORT_SCREENSHOT}}

Thanks.
QA Automation Team''',
  onFailure: 'UNSTABLE',
  theme: 'DARK'
)
```

The step captures only the `octane-report-zone` chart area, saves it in the workspace as
`.octane-suite-gate/report-email/octane-report-zone.png`, archives it by default, and embeds it in
the HTML email through a Content-ID attachment sent by Jenkins Mailer. The email includes the
project/domain details, execution totals, overall criteria verdict, exact expression, a link to
the build's Octane Gate Report, and an ordered table of each atomic comparison with its actual
value and `OK` or `NOT OK` result. An `OR` expression can therefore contain a `NOT OK` row while
the overall verdict is still `PASS`.

The body supports the tokens `{{PROJECT_NAME}}`, `{{DOMAIN_NAME}}`, `{{GATE_RESULT}}`,
`{{CRITERIA}}`, `{{REPORT_LINK}}`, `{{EXECUTION_DETAILS}}`, and `{{REPORT_SCREENSHOT}}`. The
generated data tables use stable column proportions and natural row heights, so criteria can grow
from one row to many without overlapping or distorting the screenshot. If the body contains no
tokens, it is retained as introductory text and the standard report template is generated after
it.

To send failure and timeout reports from a separate email stage, wrap the gate stage in
`catchError(catchInterruptions: false, ...)`, retain `gateResult.passed` in an environment flag,
and allow deployment only when that flag is `true`, as shown in both example Jenkinsfiles.
`onFailure` controls notification failures:

- `UNSTABLE`: mark the build unstable and continue. This is the default.
- `FAILURE`: fail the stage/build.
- `WARN`: print a warning and continue.

Optional parameters are `cc`, `bcc`, `projectName`, `domainName`, `from`, `replyTo`, `browserPath`,
`theme`, `viewportWidth`, and `archiveScreenshot`. If `projectName` is omitted, the Jenkins job
display name is used. For Gmail SMTP, `from` should be the authenticated Gmail/Workspace account
or an alias that account is authorized to send as. When `from` is omitted, the plugin uses the
authenticated E-mail Notification SMTP username before considering Jenkins' default
sender. It rejects Jenkins' `nobody@nowhere` placeholder instead of reporting a misleading
successful handoff. `theme` accepts `LIGHT`, `DARK`, or `SYSTEM` and defaults to `LIGHT`.
`SYSTEM` follows the operating-system preference of the Jenkins agent service account.
Chrome or Chromium must be available on the Jenkins agent, or `browserPath` must point to it.
On Windows, use a Groovy-escaped agent-local path such as
`browserPath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'`. The Jenkins service
account, not the interactive desktop user, must be able to start that browser. Browser validation
and screenshot capture are time-bounded so a stuck Chrome process fails according to `onFailure`
instead of holding the Pipeline indefinitely. Console messages identify whether the step is
validating Chrome, capturing, archiving, or sending through Jenkins Mailer.

The report step uses Jenkins' standard **E-mail Notification** SMTP configuration. Test those
settings under **Manage Jenkins > System**. For Gmail, use `smtp.gmail.com` with TLS on port `587`,
or SSL on port `465`, the complete Google account address, and an app password. A connection or
authentication failure happens before Gmail evaluates the HTML body, so changing the report to
plain text will not repair that class of failure. After a successful SMTP handoff, check Gmail's
Sent, All Mail, Spam, and delivery-status messages if the report is not in the recipient's inbox.

Query-backed scopes remain supported for compatibility. Query scopes are ALM Octane REST API
query fragments applied to the regression suite runs' child runs:

```groovy
octaneGateScope(name: 'legacyArea', query: 'test={((product_areas={id=1004}))}')
```

Configure Octane servers from **Manage Jenkins > System**. Store Octane API keys as
Jenkins username/password credentials, with the username as `client_id` and the password as
`client_secret`.

### External Jenkinsfile3 configuration

[`examples/Jenkinsfile3`](examples/Jenkinsfile3) loads its runtime configuration from
[`examples/variables.yaml`](examples/variables.yaml) with the Pipeline Utility Steps plugin's
`readYaml` step. Set a Jenkins string parameter named `PARAMS_FILE` to select a different YAML file
in the build workspace. Matching Jenkins Job UI parameters take priority over YAML values; omitted
YAML values fall back to the neutral defaults in the Jenkinsfile.

The YAML selects the globally configured Octane connection through `OCTANE_SERVER_ID`.
[`examples/clusters.json`](examples/clusters.json) maps that server ID to its shared-space and
workspace IDs, so pipeline users do not need to provide those IDs separately. Set `CLUSTERS_FILE`
as a Jenkins parameter or YAML value to select another mapping file. Keep client IDs and secrets in
Jenkins Credentials rather than in either configuration file. `OCTANE_SUITE_RUN_ID` is an alias for
the regression suite-run source, while `OCTANE_DEFINED_SCOPE` supplies the optional defined-scope
selections consumed by the report analytics.

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
   - **API key credentials**: the Jenkins credential created in the previous step
4. Click **Test Base URL** to verify Jenkins can reach the URL. The result shows
   **OK** for HTTP 2xx/3xx responses and **Not OK** for HTTP 4xx/5xx responses
   or connection errors.

The base URL should be the host root used by Octane authentication and API requests, such as:

- `https://your-octane-host/authentication/sign_in`
- `https://your-octane-host/api/...`

### 3. Reference the server from a Jenkinsfile

Once the server is configured, the pipeline supplies the Octane workspace and suite run ID:

```groovy
octaneSuiteGate(
  serverId: 'octane-prod',
  sharedSpaceId: '1001',
  workspaceId: '2002',
  suiteRunId: params.OCTANE_REGRESSION_SUITE_RUN_ID,
  criteria: 'regressions.executionRate == 100 AND regressions.passRate >= 95',
  timeoutMinutesExtended: 0
)
```

`suiteRunId` may be a single ID, a comma/space-separated ID list such as `1196,1200`, a Kanban
release selector such as `Release 2.4`, or a Scrum release/sprint selector such as
`Release 2.4, Sprint 3`. Dynamic selectors are queried again on each interval so the dashboard and
criteria use the current matching suite-run pool.
It may also be omitted when a nonempty `critical` suite-run scope is configured. In that mode,
regression criteria and report sections are skipped while critical and defect criteria continue to
be evaluated. If a suite run appears in both `suiteRunId` and the `critical` scope, the critical
scope owns it and it is removed from the regression bucket.
`sharedSpaceId` and `workspaceId` are required because suite runs are workspace-scoped
in ALM Octane.

## Sample Jenkinsfiles

Examples with a manual `input` confirmation before the Octane gate:

- [examples/Jenkinsfile](examples/Jenkinsfile): uses a `critical` suite-run scope.
- [examples/Jenkinsfile2](examples/Jenkinsfile2): uses only regression suite metrics, with no scope.

## Local Development

```bash
./mvnw spotless:check test
./mvnw hpi:run
```

Use Java 21 for Jenkins plugin development. The Maven wrapper pins Maven 3.9.11 so the Jenkins HPI
lifecycle and its custom dependency types are loaded consistently across development and CI hosts.
