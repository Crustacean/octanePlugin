# Checkmarx Critical through Medium remediation

## Scope

Source: `docs/audits/scan-report(1).xlsx`, rows 2-6.
SHA-256: `26345b4f8bcf60efea3ed1b30fc690f0ae6153427ffe3bd2ba4536af98ab946c`.
Base commit: `081dae5`, plus this working-tree remediation.

The workbook contains 284 findings: 1 Critical, 0 Very High, 1 High, 3 Medium,
22 Low, and 257 Informational. Only the five Critical/High/Medium findings are
in scope. They are recurrent SAST findings marked `To Verify`. The workbook
contains no dependency/SCA findings. It is preserved unchanged.

## Changes and Dispositions

| Row / severity / query | Source review and remediation | Closure requirement |
| --- | --- | --- |
| 2 / Critical / Stored_XSS | The reported sink was the raw output-stream write in `OctaneGateReportAction.doData`. Artifact reads now return parsed `ObjectNode` data, not response bytes. `doData` and `doSnapshot` use Jackson serialization at the response boundary through `OctaneReportJson.writeTo`. HTML-sensitive characters in keys and nested values are escaped without changing their decoded text. JSON MIME type, `nosniff`, JSON-only CSP, read authorization, artifact size/path bounds, and rejection of non-object/trailing data remain enforced. No servlet writer is closed by the serializer. Existing browser rendering uses text nodes for remote labels. | Rerun SAST against the updated production source. If retained, review the parser/serializer and browser tests in the complete source-to-sink trace. |
| 3 / High / Cleartext_Submission_of_Sensitive_Information | The reported sink is the logout request builder. Removed plaintext-secret constructor inputs from `OctaneClient`; all constructors now require Jenkins `Secret`. The production runner already passes `Secret` directly. Every request now has an explicit HTTPS check in `requireAllowedRequest`, in addition to configured-origin/base-path checks, before attaching a cookie and again before dispatch. Redirect-following clients remain rejected. Logout discards the retained session cookie even when sending fails or is interrupted. | Rerun SAST. TLS integration tests verify that authentication/logout redirects never forward credentials to HTTP. No TLS trust bypass, HTTP fallback, or exception suppression was added. |
| 4 / Medium / Spring_Missing_HSTS_Header | The flagged class is annotated `hudson.Extension` and implements `jenkins.util.HttpServletFilter`. It is not a Spring controller. Keep this native filter: removing it would lose coverage for Jelly views, static assets, dispatch errors, and redirects. Action endpoints now also set HSTS explicitly through `StaplerResponse2` before authorization/response branching. Both paths share the same policy constant. No Spring dependency was added. | Framework-specific false-positive candidate. Have an authorized reviewer examine the Jenkins extension and HTTPS tests, then record a per-result disposition. Do not disable the query globally. |
| 5 / Medium / JavaScript Missing_HSTS_Header | The flagged `response.json()` in `octane-scale-report.js` reads a browser Fetch response; it does not generate HTTP responses. Its data/snapshot/script endpoints are covered by the Stapler and filter policies. No fake client-side HSTS request header or replacement of `response.json()` was introduced. | Client/server misclassification candidate. Verify the deployed HTTPS response and submit this evidence for per-result review. |
| 6 / Medium / Java Missing_HSTS_Header | `doData` explicitly applies the Stapler response policy before success, conditional-cache, invalid-section, missing-data, and permission branches. Both script endpoints now apply it directly too. JSON CSP is not applied to executable scripts or report pages. | Rerun SAST and verify the deployed TLS boundary as described below. |

The Octane client constructor signature and artifact-store return types changed.
Repository callers and test doubles have been migrated; any external Java code
directly instantiating these internal implementation classes must migrate too.
Pipeline configuration, JSON schemas, polling/criteria behavior, dependency
versions, and Jenkins baseline are unchanged.

## Result IDs for Review

| Workbook row | Checkmarx result ID |
| --- | --- |
| 2 | `m1o73BF/FjD76Yctu+tnAgybAj0=` |
| 3 | `AdPQc3rNN9qsXM9UxTocBWxhRLA=` |
| 4 | `YyocMdyboKNXatRE2ex3Wy612vo=` |
| 5 | `WiJCbOy479YX9rHC+LnkmZqijNA=` |
| 6 | `Z3g9T8MruZk95HrDNyj6OqQz3KM=` |

Use each row's original workbook link for the scan and complete data-flow trace.
Checkmarx distinguishes `To Verify` from confirmed `Not Exploitable`; changing a
disposition requires review and a note, not just a successful build.
See [Checkmarx SAST Results Viewer](https://docs.checkmarx.com/en/34965-253660-sast-results-viewer.html)
and [Triaging SAST Results](https://docs.checkmarx.com/en/34965-338662-triaging-sast-results.html).

## Deployment Verification Still Required

HSTS is `max-age=31536000; includeSubDomains` on requests the servlet container
recognizes as secure. HTTP does not get HSTS, and an arbitrary caller-provided
`X-Forwarded-Proto: https` does not bypass that check. This follows
[RFC 6797, section 7.2](https://www.rfc-editor.org/rfc/rfc6797.html#section-7.2).

For HTTPS terminated at a proxy, configure trusted forwarding at the container
or set HSTS at the HTTPS edge, including error responses. Restrict direct backend
access; do not make plugin code trust arbitrary forwarding headers. See
[Jenkins reverse-proxy guidance](https://www.jenkins.io/doc/book/system-administration/reverse-proxy-configuration-troubleshooting/).
The existing `includeSubDomains` policy requires HTTPS readiness for subdomains.

Using an authorized report session, inspect the public HTTPS responses for the
report page, `snapshot`, `data`, a section page, both script endpoints, and an
invalid section. Confirm HSTS and `nosniff`, including a conditional 304 response;
confirm JSON endpoints retain JSON MIME type and CSP. Public ingress configuration
and a fresh Checkmarx scan cannot be verified from the supplied workbook alone.
No scanner state, severity, exclusion, or suppression has been changed here.

## Verification

- New serializer test covers nested hostile keys/values, unchanged decoded data,
  and servlet writer ownership.
- New Stapler endpoint tests cover secure/insecure requests, spoofed forwarding
  headers, successful JSON/scripts, 304/400/404 responses, denied read permissions,
  and malformed persisted data rejected before any response body is written.
- Client tests enforce Secret-only constructor inputs and verify secure logout,
  cookie release (including failed logout), repeated close, and rejection of
  credential-bearing redirects.
- Existing real HTTP artifact-injection and Chromium hostile-label/refresh tests
  remain part of the full suites.
- Final build/test results and artifact fingerprint will be recorded after validation.
