# Checkmarx workbook remediation, 2026-09-24

## Scope and limitations

Source: `docs/audits/scan-report.xlsx`, sheet `scan-report`, rows 2-327.
SHA-256: `57c4e2154e680664b10bdaa9d53df0313da140c306089bccca8672b1f44132a7`.
The workbook is preserved unchanged. It contains 326 SAST entries: 1 critical,
1 high, 3 medium, 69 low, and 252 informational. All are marked `To Verify`;
315 are recurrent and 11 new. It does not contain dependency/SCA results.

This is source review and regression evidence, not a replacement Checkmarx scan
or approval to suppress every entry. Source line numbers below are the scan's
locations and may move after formatting. Existing defenses are distinguished
from changes made in this remediation. No dependency versions, Jenkins baseline,
credential lookup rules, or global Octane polling behavior were changed.

## Remediated paths

| Workbook finding | Evidence and change |
| --- | --- |
| Locale-dependent comparison, 58 paths | 29 originate at `Util.normalizeStatus`, 18 at `GateMetrics.normalizeMetricName`. Both used the controller's default locale; they now use `Locale.ROOT`. This prevents Turkish casing from changing status/metric identifiers. The other 11 paths already use explicit `Locale.ENGLISH` or `Locale.ROOT` in `OctaneRiskHeatMapBuilder` and `OctaneSuiteAttributions`. Adjacent scope-title/criteria diagnostic casing now uses an explicit locale too. |
| Incorrect permission assignment, rows 63 and 75 | Report directories are created with owner-only POSIX permissions (0700); JSON and compressed snapshots are created atomically with 0600 using `CREATE_NEW` and `NOFOLLOW_LINKS`. An existing report root is restricted to 0700 when publishing, protecting older generations beneath it. Symlink checks remain. On non-POSIX filesystems, ACLs remain inherited from the Jenkins build directory; administrators must restrict that directory to trusted principals. |
| Integer overflow/underflow, rows 66-68 and 71-74 | Section/cursor/limit bounds remain enforced. Page length is now bounded by remaining elements before adding to the cursor, avoiding an intermediate overflowing addition. Tests cover minimum/maximum integers, negative values, empty pages, and invalid sections. |
| Numeric conversion, row 153 | The 64 MiB artifact limit is now an `int`; the bounded read no longer needs a narrowing cast. The size limit and deserialization byte budget are unchanged. |
| Recursion family, 14 paths | Most reported paths are finite overload delegation or traversal of a bounded expression tree. Review found an adjacent omission: unary `+`/`-` recursion did not use the 64-level group budget. Unary expressions now share that budget, including mixtures with parentheses. Existing 8,192-character and 1,024-token limits remain. |

POSIX creation attributes are set during creation, not by temporarily creating
world-readable files and then calling chmod. See the Java
[Files API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html).
Protocol identifier casing uses the locale-independent overload recommended by
the [String API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#toLowerCase()).

## Highest-severity findings: existing defenses verified

### Row 2: stored XSS, critical

The actual reported path is artifact input in `OctaneReportArtifactStore` to the
`OctaneGateReportAction.doData` response output stream, not an unescaped browser
`innerHTML` assignment. All index/section responses parse a JSON object, reject
trailing tokens, and re-encode through `OctaneReportJson`, escaping `<`, `>`, `&`,
and apostrophes. The endpoint checks read permission and sends JSON MIME type,
`nosniff`, and `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; sandbox`.

Added an HTTP integration regression that writes raw script/image attack strings
into the actual persisted index and section artifacts, calls both Stapler
endpoints, and checks headers, encoded bytes, and unchanged decoded text.
Existing artifact tests also reject HTML, arrays, null, and extra JSON documents.
The browser regression verifies labels remain inert after initial rendering and
refresh. This path is a candidate for a reviewed Not Exploitable disposition;
the scanner must be rerun with these defenses and evidence available.

### Row 3: cleartext submission, high

The reported sink is the logout POST builder, carrying the Octane session cookie.
`OctaneServerUrl` already requires HTTPS, rejects user-info, and constrains requests
to the configured origin and base path. `OctaneClient.requestBuilder` validates
the URL before adding cookies; `send` validates again. Redirect-following clients
are rejected. Client secrets/session cookies are stored as Jenkins `Secret` internally;
serialization uses credential identifiers rather than storing secret values.

Added a TLS logout test that confirms the cookie reaches the HTTPS logout
endpoint but does not reach an HTTP server named by a 307 redirect. Existing
tests cover plaintext configuration rejection and authentication redirects.
No HTTP compatibility bypass or TLS trust bypass was added. This specific
cleartext path is a candidate for a reviewed Not Exploitable disposition.

### Rows 4-6: missing HSTS, medium

`OctaneReportSecurityHeaders` is a Jenkins `HttpServletFilter` extension, not a
Spring controller. It already sets HSTS on secure report responses, including
views, scripts, and dispatch errors. JSON endpoints also call the shared helper.
Tests cover secure/insecure requests, spoofed forwarding headers, filter paths,
and JSON security headers. `response.json()` in JavaScript is not where HSTS is
configured: HSTS is an HTTP response header. Adding a request header or Spring
dependency would not fix that finding. See Jenkins'
[HttpServletFilter API](https://javadoc.jenkins.io/jenkins/util/HttpServletFilter.html).

For TLS termination at a reverse proxy, configure trusted forwarding at the
container/proxy or add HSTS at the HTTPS edge. Do not trust arbitrary incoming
`X-Forwarded-Proto` values in plugin code. Verify the deployed HTTPS response as
well as the tests before marking these findings Not Exploitable.

## Other reviewed security paths

| Rows / query | Disposition and reason |
| --- | --- |
| 7, 91: serializable sensitive data | `credentialsId` is a Jenkins credential reference, not a password/token. Retain it for resumable Pipelines; making it transient would break restart behavior. Secret values are resolved through the Jenkins Credentials API, not stored in these fields. |
| 65, 70: resource shutdown | Script input streams are already in try-with-resources. The servlet response output is container-owned and must not be prematurely closed by this endpoint. |
| 77, 95, 141-143: recursion | Constructor/overload delegation terminates; methods with the same name are not automatically self-recursive. Keep the tested overload contracts. |
| 96, 108, 207, 239: ignored compatibility errors | Octane relationship/endpoint probes intentionally try alternate schema projections. Do not remove fallback support or log raw responses/secrets. Final required polling failures still propagate. |
| 100, 102, 107, 124, 127, 223, 224, 258, 265 | Best-effort logout, stale-generation cleanup, and logging after Pipeline shutdown must not replace the primary failure/result. `closeSession` calls `close` and clears its reference. These are not unclosed success paths. Operational diagnostics can be improved separately with bounded, redacted messages. |
| 98, 132: obsolete functions | Jenkins ACL `checkPermission` protects report reads and manual exit; retain authorization checks. The scan is not justification for removing them. |
| 115: return in finally | The lambda returns the result of conditional map removal; it does not return from the enclosing finally block or suppress the original exception. |
| 118: non-cryptographic random | Randomness is retry backoff jitter, not authentication, a secret, or a security identifier. |
| 147: ESAPI banned API | `isSecure()` uses the servlet container's trusted TLS state. It is appropriate for deciding whether to set HSTS. |
| 193, 198, 214, 233, 259, 267: ESAPI banned API | `Properties.getProperty` reads fixed SMTP host/port/SSL keys from Jenkins Mailer configuration, not user-selected system properties or passwords. No blanket API substitution is warranted. |
| 155, 262: missing switch default | Exhaustive enum switches should retain compiler exhaustiveness checking instead of silently accepting a new state via default. |
| 182, 183: client potential XSS | Category bar/pill attributes feed fixed DOM APIs and equality comparisons; defect text uses `textContent`. Extended the Chromium test to click hostile category keys through both delegated handlers and refresh data, asserting correct filtering and no injected elements or script execution. |

## Informational triage

The following remaining families are code-quality/lifecycle review items, not
326 independent demonstrated vulnerabilities. No blanket suppression was added.
Keep them open for query-level review unless their individual evidence is accepted:

| Family | Count | Review guidance |
| --- | ---: | --- |
| Unused variable | 42 | Includes intentional ignored exceptions and compatibility parameters; deleting an API parameter can break callers. |
| Expression always false / true | 23 / 1 | Includes null-normalizing constructors, Jenkins deserialization paths, and runtime flags. Scanner inference alone does not establish unreachable behavior. |
| Dead code | 44 | Retain compatibility/lifecycle branches until call-site and persisted-data coverage proves removal safe. |
| Generic throws / catch declarations | 20 / 11 | Jenkins callbacks and lifecycle cleanup often have broad contracts. Narrow only with evidence for every caller/failure path. |
| Insufficient exception logging | 55 | Do not log credentials, cookies, raw API responses, or arbitrary remote error text to satisfy a generic rule. Preserve final error propagation and bounded diagnostics. |
| Empty methods | 8 | Includes optional listener/default callback contracts. An intentional no-op is not a security bypass by itself. |
| Confusing naming | 4 | Style-only; no security-sensitive behavior change proposed. |

## Verification

Added regressions cover locale-independent status/metric interpretation, unary
nesting limits, POSIX permissions and republishing, integer paging extremes,
persisted-JSON HTTP responses, TLS logout redirects, and real browser category
selection/refresh with hostile text. Existing HSTS, deserialization, path traversal,
symlink, HTTPS, and report-layout coverage is included in the full suites.

Validated against base commit `19c73f5` plus this working-tree remediation:

- `mvn -B -ntp clean verify`: **BUILD SUCCESS**. 497 tests reported, 495 passed,
  zero failures/errors, two skips. Skips are the opt-in 24-hour enterprise soak
  and the generated properties-file test (no properties file exists).
- SpotBugs: **0 bugs, 0 errors**. Its first pass identified two missing parent-path
  null guards in the new permission helpers; these were fixed and the entire
  clean verification rerun successfully, without adding suppressions.
- Spotless: passed for all 151 Java files and the POM.
- `node --test src/test/javascript/*.test.mjs`: **101 passed, 0 skipped**,
  including Chromium security and responsive layout tests.
- `git diff --check`: passed.
- Root and deployment build outputs cleaned using Maven. Dependencies in the
  shared Maven repository were retained. The full build ran in an isolated
  `/tmp/octane-scan-validation.2Ocofg` source copy to avoid IDE output interference;
  `src/main`, Java tests, and the POM were compared with the working tree.
- `deployment/src/main` and `deployment/pom.xml` synchronized to the authoritative
  root versions, removing differences left behind by the earlier rollback.
  Deployment remains a production-source scan copy, without build artifacts.
- Final HPI and test/static-analysis reports copied to `target/`.

Artifact: `target/octane-suite-gate-by-embiti.hpi`, 604,288 bytes.
SHA-256: `76915eb035d55a90ee1f7827ffe3f6e922d096e59deff33a80a96d13990e73bd`.

No Checkmarx executable was available locally. A new Checkmarx scan and
security-owner disposition of the framework/quality findings remain required;
this report does not claim that all 326 scan entries are closed. Windows ACL
inheritance and the deployed reverse-proxy HTTPS headers still require
environment-specific verification.
