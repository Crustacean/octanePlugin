# Checkmarx Remediation, 2026-09-22

Source: `Cx_ProjectReport_2026-09-22_111240.pdf`, scan
`3b192c66-781c-418a-8497-be1c923affa7`: five SAST and two SCA findings.
This summary report does not include complete source/sink traces. The changes below address
the reported boundaries and add regression tests; they are not a fresh Checkmarx certification.
No finding is suppressed or hidden by removing dependencies from the scan POM.

## Finding Disposition

| Finding | Implementation / remaining requirement |
| --- | --- |
| Ivy 2.5.3, CVE-2026-26032 | Both POMs manage Ivy 2.6.0, the Apache-published fix. Ivy enters through test-scoped `pipeline-groovy-lib`, not the production HPI. |
| Commons Collections 3.2.2, Cx78f40514-81ff | Still supplied by Jenkins core 2.582 with `provided` scope. There is no published 3.2.3. Collections4 has different package names and cannot replace Jenkins' runtime dependency. This finding remains open. |
| Two cleartext transmission findings | The client retains the API secret and session cookies as Jenkins `Secret` fields. The runner passes the credential's `Secret` without first converting it to a String. HTTPS and same-origin checks run before building authentication payloads and again before sending requests; redirects remain disabled. Authentication failures omit response bodies, and other API errors mask known secrets and cookie values before truncation. |
| Stored XSS | One HTML-safe JSON encoder now covers artifact publication, artifact reads, snapshot responses, and test-management data. It escapes HTML-significant characters in JSON keys and values without changing decoded data. Existing safe `textContent` rendering remains in place and is exercised in a real browser with hostile remote strings through initial rendering and refresh. |
| Two missing HSTS findings | A Jenkins `HttpServletFilter` extension covers plugin report pages, JSON endpoints, scripts, static assets, redirects and errors before dispatch. Secure responses get `max-age=31536000; includeSubDomains`; insecure responses do not. The filter preserves Jenkins authorization, MIME types, and existing CSP. |

## Boundary Decisions

- `OctaneReportArtifactStore` persists report metrics, not API credentials. Report JSON is encoded
  for safe transport; arbitrary report content is not wrapped in `Secret` or lossy HTML entities.
  Existing artifact path, size, symlink and deserialization protections remain intact.
- Octane authentication requires the original API secret inside an HTTPS request. Jenkins'
  encrypted-at-rest representation cannot be sent instead. `getPlainText()` is limited to use
  boundaries; Jenkins Credentials/XStream persist encrypted `Secret` values. The client and its
  session cookies are not persisted into report artifacts. A `Secret` does not promise encrypted
  process memory or eliminate the transient plaintext required by TLS/client libraries.
  Malformed JSON errors also omit parser exception causes, which can quote the raw response and
  defeat redaction of the outer message; regression tests inspect the entire reported stack trace.
- HSTS belongs on server responses, not in `OctaneGateRunner` (an API client/orchestrator) or in
  browser `fetch` request headers. The filter uses only the servlet container's `isSecure()`
  decision, never an arbitrary client-supplied `X-Forwarded-Proto` header.
- JSON escaping does not make decoded values safe for `innerHTML`. Untrusted labels must still
  use `textContent`; snapshot HTML fragments must continue to come from escaping server renderers.
  The JSON CSP must not be applied to report pages or JavaScript responses.

## Remaining Commons Collections Dependency

The requested Test B cannot fully pass while the supported Jenkins core provides Collections
3.2.2. Excluding that artifact from Maven would not remove it from an installed controller;
bundling Collections4 would not fix callers using `org.apache.commons.collections`.
There is no direct Collections3/`SetUniqueList` use in plugin source. An upstream Jenkins/library
fix or a documented security-owner reachability assessment is needed. The scan input retains
the dependency so it stays visible. The Ivy 2.5.3 part of Test B can be satisfied independently.

## Deployment

1. Serve Jenkins and Octane over HTTPS with trusted certificates. Configure the TLS-terminating
   proxy/container correctly; there is no HTTP or trust-all fallback for Octane credentials.
2. **HSTS is an origin-wide browser policy even when set on a plugin path.** Its
   `includeSubDomains` directive requires HTTPS readiness for all subdomains of the Jenkins host.
   Review this before deploying. At the reverse proxy, apply the same policy to all HTTPS
   responses, including core Jenkins paths and errors, and redirect HTTP to HTTPS.
3. Verify externally visible report, snapshot, data and script response headers, including 304
   responses. Unit tests cannot prove an unprovided production reverse proxy is configured well.
4. Scan the refreshed `deployment/` source tree. It contains production source/resources/web
   assets, the full primary POM and Maven settings, not tests, test keys, logs or build output.
5. Re-run Checkmarx SAST/SCA and review any remaining source/sink traces with this record.

## Verification

Verified on September 22, 2026:

- `mvn -B spotless:apply clean verify` succeeded using the primary POM. The clean goal deleted
  generated `target/` output; the shared Maven dependency cache was not purged. All 490 Java
  tests completed with zero failures/errors and two expected skips (the opt-in enterprise soak
  test and an injected test for properties files, of which this project has none).
- SpotBugs reported zero bugs/errors, and Spotless passed. The helper POM's `spotless:check`
  also passed. `git diff --check` and the updated skill's validation passed.
- `TMPDIR=/home/embiti/octanePlugin/target/tmp node --test src/test/javascript/*.test.mjs`:
  101 passed, zero failures/skips. This includes the hostile-label initial-render/refresh test
  and existing responsive dashboard/browser tests.
- Credential tests use real local HTTPS, verify encrypted Jenkins credential XML, reject HTTP,
  and check both reflected-secret diagnostics and the complete malformed-response stack trace.
  Report tests verify JSON round trips, normal data/snapshot delivery, script loading, filter
  registration, secure/insecure header handling, and preservation of Jenkins CSP.
- Fresh dependency trees are in `target/checkmarx-primary-dependencies.txt` and
  `target/checkmarx-helper-dependencies.txt`. Both resolve `org.apache.ivy:ivy:2.6.0:test`, with
  no Ivy 2.5.3. Both still resolve `commons-collections:commons-collections:3.2.2:provided`.
  **Acceptance Test B remains partially unmet**, as explained above; no dependency is suppressed.
- Archive inspection confirms the HPI contains only the plugin JAR as a bundled library, no Ivy,
  Commons Collections, test classes or test TLS keys. Its nested JAR matches the generated plugin
  JAR and contains the registered security filter. The manifest still requires Jenkins 2.582
  and Java 21; this does not lower the supported Jenkins baseline.
- `deployment/` was refreshed and compared against `src/main/` and `pom.xml`: 97 files,
  approximately 1.6 MiB, with no test tree, keys, build output, logs or scan reports.
- Artifact: `target/octane-suite-gate-by-embiti.hpi`, 604,264 bytes (about 590 KiB).
  SHA-256: `7fdef9e06e3ffc2fd298b3980f4db54ef1b92565f5a4afb80a1df95d84945ca0`.

A new Checkmarx scan and live reverse-proxy validation were not run. These results are not a
claim that all seven findings are closed; the controller-provided Commons Collections finding
requires upstream remediation or an explicit security-owner disposition.

## References

- [Apache Ivy security: fixed in 2.6.0](https://ant.apache.org/ivy/security.html)
- [Apache Commons Collections releases and package compatibility](https://commons.apache.org/proper/commons-collections/)
- [Collections3 published version metadata](https://repo.maven.apache.org/maven2/commons-collections/commons-collections/maven-metadata.xml)
- [Jenkins secret handling](https://www.jenkins.io/doc/developer/security/secrets/)
- [Jenkins HTTP filter extension API](https://javadoc.jenkins.io/jenkins/util/HttpServletFilter.html)
