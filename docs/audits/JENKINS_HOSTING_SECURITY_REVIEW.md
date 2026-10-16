# Jenkins Hosting Security Review

Reviewed 2026-10-03 against the findings in `TASK.md` and the actual call paths,
not just the names identified by the scanner.

## Disposition

The listed findings are false positives in the reviewed code. No authentication,
authorization, CSRF, serialization, or polling behavior needs to be weakened or
changed to address them. Each suppression is on the specific reviewed method or
field, with a reason next to it. There are no class-wide or scanner-wide exclusions.

### Descriptor endpoints

The 41 reported methods in `OctaneSuiteGateStep.DescriptorImpl`,
`OctaneSuiteGateBuilder.DescriptorImpl`, `OctaneEmailReportStep.DescriptorImpl`,
`OctaneDefectGroup.DescriptorImpl`, and `OctaneGateScope.DescriptorImpl` validate
only submitted input or return fixed public enum options. They do not read saved
configuration, enumerate credentials, read workspace files, connect to Octane,
send email, or persist changes.

- `doCheckCredentialsId` checks whether the submitted text is empty. It does not
  resolve the ID or reveal whether a credential exists.
- `doCheckBaseUrl` parses URI syntax only. It performs no DNS lookup or HTTP call.
- `doCheckSpacesMappingFile` normalizes and checks a relative path only. It does
  not open that file. Space/workspace selectors are also checked as text only.
- Criteria parsing is already limited to 8,192 characters, 1,024 tokens and a
  nesting depth of 64; it does not execute the expression. Explicit scope queries
  are bounded to 4,096 characters and are not sent to Octane by validation.
- Group validators construct temporary objects from the supplied name/types and
  compare them with built-in names. Fixed email theme/failure lists are public.

Accordingly, these methods have narrow `jenkins/no-permission-check` and
`jenkins/csrf` suppressions. Requiring administrator access here would needlessly
break validation for ordinary job users without protecting any private data.

### Read-only report endpoints

`doSnapshot`, `doData`, `doScaleReportScript`, and `doTestManagementScript` retain
their existing `Item.READ` check, before reading content or returning a conditional
response. Only the CSRF findings are suppressed:

- Snapshot serializes already-published build data, optionally loading a local
  report artifact into an internal cache. It does not invoke the polling callback.
- Data reads the build's fixed artifact paths; section IDs are validated, cursor
  is clamped, and page size is capped at 200. Existing file-size, symlink and JSON
  validation protections remain intact.
- Scripts come from fixed classpath resource paths, never caller-provided URLs.

GET must remain usable for browser polling and script loading. Manual exit is
different: `doExitOctaneAndContinue` mutates the run and still requires both
`@RequirePOST` and `Run.UPDATE`. Jenkins crumb validation remains enabled.

### Nonsecret fields

The nine reported fields carry no passwords, tokens, or API keys:

| Field | Actual data |
| --- | --- |
| `OctaneSuiteGateStep.passedStatuses` | Configured passing test-outcome names |
| `GateRequest.passedStatuses` | The same outcome vocabulary for `StatusClassifier` |
| `OctaneGateStatusBucket.dataKey` | Public enum identifier, e.g. `passed` |
| `OctaneTestMetricCard.key` | Public report-card identifier |
| `OctaneTestMetricSegment.severityKey` | Public severity/color identifier |
| `OctaneEmailDeliveryCoordinator.Lease.key` | Build ID + workspace path, in-memory lock lookup only |
| `OctaneTestManagementAnalytics.FailureCategory.key` | Category slug matching bars to tabs |
| `OctaneTestManagementAnalytics.DefectDetail.severityColorKey` | Severity palette identifier |
| `OctaneTestManagementAnalytics.MetricQuadrant.key` | Metric identifier, e.g. `defect-compliance` |

These fields have field-level `jenkins/plaintext-storage` suppressions. Converting
them to `Secret` would obscure public report data and risk serialization/rendering
regressions, without securing a credential. Real credential resolution is untouched.

## Regression Coverage

`HostingSecurityReviewTest` exercises real Stapler HTTP routing for all 41
descriptor endpoints with a user lacking job configuration permissions, all four
report GET endpoints with allowed/denied users, and manual exit with valid/invalid
HTTP methods, permissions and crumbs. It verifies read requests do not invoke
polling or alter build XML and preserves public status/presentation serialization.
Existing endpoint tests also check denied responses, conditional responses, security
headers, and malformed stored data.

These tests validate behavior, not the remote CodeQL disposition. Rerun the Jenkins
Security Scan workflow to confirm that it recognizes the supported annotations.
Re-review a suppression if its method gains configuration access, I/O, unbounded
computation, or side effects, or if a field starts carrying secret material.

## Verification Results

- Main project targeted Java run: 21 tests, zero failures/errors/skips
  (`HostingSecurityReviewTest`, `OctaneReportEndpointSecurityTest`,
  `OctaneGateReportActionTest`, `OctaneEmailDeliveryCoordinatorTest`).
- Submission project `./mvnw -B -ntp spotless:check clean verify`: 493 tests,
  zero failures/errors, two skipped; SpotBugs reports zero findings.
- Dashboard asset contract tests: two passed, zero skipped.
- Production changes contain only comments, whitespace, and source-retained
  `SuppressWarnings` annotations; no executable statements or field types changed.
- The reviewed production patch was applied to both the deployment tree and the
  submission project. Pre-existing project-specific differences were preserved.
- Clean builds succeeded for the main HPI (`pom-build.xml`), deployment HPI
  (`deployment/pom.xml`), and submission HPI. All three archives passed integrity
  checks. Maven `clean` removed project build caches; the shared dependency cache
  was not purged. Formatting and `git diff --check` passed.
- CodeQL is not installed locally. The hosted Jenkins Security Scan must still be
  rerun to confirm alert disposition; Maven/SpotBugs results are not a CodeQL scan.

## Sources

- [Jenkins form validation security](https://www.jenkins.io/doc/developer/security/form-validation/)
- [Jenkins Security Scan](https://www.jenkins.io/doc/developer/security/scan/)
- Finding-specific guidance included in `TASK.md`.
