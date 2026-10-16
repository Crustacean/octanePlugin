# Jenkins Hosting Recommendations

## Changes

Review items refer to the current `TASK.md` and submission commit
`c1381d3814ec06ffaaf8f58c66caaa98ea7947e2`.

| Item | Disposition |
| --- | --- |
| 1 | Removed the direct `structs` dependency; existing Pipeline dependencies supply their own requirements. |
| 2 | Removed six plugin-version overrides after aligning the main/deployment BOM with the submission project's BOM. Retained three test-library security overrides, explained below. |
| 3 | Removed the explicit HPI plugin declaration, including the duplicate main/deployment packaging execution. HPI packaging uses the parent lifecycle; IDE lifecycle metadata follows the parent's HPI version. |
| 4 | Removed the JaCoCo version pin. Kept the existing always-on coverage executions so existing verification still generates reports; the parent supplies the version. |
| 5 | Removed the Spotless version pin. Preserved Google Java formatting to avoid unrelated repository-wide style changes. |
| 6 | Both Freestyle and Pipeline configuration fragments have native repeatable delete buttons and drag headers for scopes and defect groups. |
| 7 | Added the native credentials selector and a permission-checked, POST-only dropdown endpoint for API credentials. |
| 8 | User approved `symbol-bar-chart-outline plugin-ionicons-api`. Replaced the legacy `graph.gif` icon and added the BOM-managed Ionicons API dependency. |
| 9 | API credentials now resolve against the executing Run, matching URL credential resolution. Preserved shared-ID precedence and the no-Run root lookup used by existing callers. |
| 10 | User confirmed Secret Text IDs remain mandatory. Corrected the label, selector, validation, help and direct-step documentation; no literal-URL fallback was introduced. |
| 11 | Octane clients use Jenkins' proxy-aware HTTP client builder. Each gate connection captures current proxy settings and releases its owned transport on close. Injected transports remain caller-owned. |

## Dependency Rationale

The removed plugin pins are covered by BOM `7093.v37de7b_4a_8a_4f`. The main, helper,
deployment and submission POMs now use parent `6.2211.v27f680c93c53`. Their existing
artifact identities and Jenkins baselines remain unchanged.

The following overrides address the earlier dependency-security remediation, rather
than compilation failures. They are test-scoped transitives and are not bundled in the HPI:

| Library | Unmanaged upstream version | Retained version | Introduced by |
| --- | --- | --- | --- |
| commons-configuration2 | 2.11.0 | 2.15.0 | pipeline-utility-steps test dependency |
| ivy | 2.5.3 | 2.6.0 | Pipeline test plugins / pipeline-groovy-lib |
| rhino | 1.7.7.2 | 1.7.14.1 | Pipeline model test plugins / JSON schema validation |

Successful compilation without these overrides is not evidence that reverting earlier
security remediation is appropriate. Remove each override when its upstream test
dependency supplies an equivalent or newer version. This change does not claim a new
dependency scanner or hosted CodeQL result.

## Configuration And Security Contracts

- `baseUrl`, `shared_url`, and nonblank `specific_url` contain Secret Text credential IDs.
  The Secret Text value contains an HTTPS host root, not an authentication/API endpoint.
- URL dropdowns list Secret Text credentials; API dropdowns list Username with password
  credentials. Both require `Item.CONFIGURE` in job context or `Jenkins.ADMINISTER` at root,
  and require POST with a valid crumb. They never return decrypted secrets.
- Pure validators remain pure. URL validation now checks credential-ID syntax only;
  runtime lookup validates the resolved HTTPS URL before use.
- Runtime credential lookup includes the job's folder ancestry, not sibling folders.
  An accessible `octane-api-client` still takes precedence over the selected API ID.
- Proxy, proxy authentication and no-proxy settings are read when a gate opens its client.
  Existing connections retain their configuration for their lifetime. TLS validation,
  authentication body protections and `Redirect.NEVER` are unchanged.
- Deployment sources are refreshed from `src/main`; this also removes stale root-only
  Freestyle credential lookup and stale URL validation in the previous scan copy.
- The reviewer-linked parent project's example mapping now contains `octane-shared-url`
  rather than a literal URL. Its README and credential-handoff fixture match that contract.

## Regression Tests

- `OctaneReportIconTest`: verifies the installed Ionicons symbol and the actual build-page
  report link render the three-bar SVG with theme-inherited `currentColor`.
- `OctaneConfigurationUiTest`: real form rendering, reorder/save, delete all/save,
  Pipeline fragment controls, typed dropdowns, folder isolation and permissions.
- `OctaneFolderCredentialsTest`: authentication with folder-only API and URL credentials,
  sibling-folder rejection and folder-over-root shared-ID precedence.
- `OctaneProxyTest`: HTTPS CONNECT reaches the configured proxy without API credentials;
  updated no-proxy settings bypass an unavailable proxy; proxy credentials, timeout and
  redirect policy are preserved.
- `HostingSecurityReviewTest`: real HTTP selector endpoints reject GET, missing crumbs
  and insufficient permissions while returning only typed IDs to authorized callers.
  Existing report and manual-exit authorization tests remain in place.

## Verification (2026-10-16)

- Main `./mvnw -B -ntp clean verify`: 520 tests, zero failures/errors, two expected skips.
- Submission `./mvnw -B -ntp clean verify`: 498 tests, zero failures/errors, two expected skips.
  These complete suites ran before the final approved icon change. The skips are the
  opt-in enterprise soak and the injected properties check when no properties files exist.
- After adding the icon, targeted `clean verify` passed on both Jenkins baselines:
  main 10 tests without skips; submission 20 tests including injected plugin checks,
  with one expected properties-check skip. These runs explicitly fail if no tests execute.
- Both verification paths passed SpotBugs with zero bugs/errors and passed Spotless.
- `TMPDIR=target/tmp node --test --test-concurrency=1 src/test/javascript/*.test.mjs`:
  103 tests passed, including browser graph visibility/scaling checks.
- Parent `dashboard_credentials_test.groovy` and `pipeline_handoff_test.groovy`: passed
  using the cached Groovy 2.4.21 runner.
- After the final IDE-metadata alignment, main and deployment
  `clean package spotless:check` passed with tests intentionally skipped for packaging.
  The helper `pom-build.xml` Spotless check also passed.
- `git diff --check` passed in main, submission and the parent project. Deployment's
  `src/main` and POM match the main project. All three HPI ZIP integrity checks passed;
  each contains only its own plugin JAR in `WEB-INF/lib`, with no test libraries.

| Output | Jenkins baseline | Bytes |
| --- | --- | ---: |
| `octanePlugin/target/octane-suite-gate-by-embiti.hpi` | 2.582 | 649144 |
| `octanePlugin/deployment/target/octane-suite-gate-by-embiti.hpi` | 2.582 | 649158 |
| `octane-status-tracker-plugin/target/octane-status-tracker.hpi` | 2.568.3 | 647063 |

Each manifest declares `ionicons-api:94.vcc3065403257`, resolved by the BOM rather than
an explicit version override. Jenkins must have that dependency installed. No hosted
Jenkins submission scan or external Octane-environment test was performed in this run.

## References

- [Credentials consumer guide](https://github.com/jenkinsci/credentials-plugin/blob/master/docs/consumer.adoc)
- [Proxy-aware HTTP builder](https://javadoc.jenkins.io/hudson/ProxyConfiguration.html#newHttpClientBuilder())
- [Jenkins repeatableProperty implementation](https://github.com/jenkinsci/jenkins/blob/jenkins-2.568.3/core/src/main/resources/lib/form/repeatableProperty.jelly)
- [Jenkins parent POM](https://github.com/jenkinsci/plugin-pom)
- [Ionicons API plugin](https://plugins.jenkins.io/ionicons-api/)
