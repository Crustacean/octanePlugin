# Checkmarx Remediation, 2026-09-16

Source: `Cx_ScanReport_2026-09-16_125351.pdf`, scan
`b60831d9-e7da-41dd-9da5-0d7827fc7374`: 8 SAST and 40 SCA findings.
This is a source/dependency review and regression-test record, not a new Checkmarx scan.
No findings have been suppressed to make the scan appear clean.

## Source Findings

| Finding (similarity ID) | Change / disposition |
| --- | --- |
| Stored XSS (1088577585) | Artifact endpoints now parse a single JSON object, reject malformed/trailing content, and reserialize with HTML-significant characters escaped. The decoded JSON values remain unchanged. Responses retain JSON MIME type and nosniff, with a restrictive CSP added. |
| Cleartext credentials (1090729939, -1734211326) | The central Octane URL validator rejects HTTP before authentication, including loopback and VPN endpoints. The example Pipeline also fails early. Redirect-following clients are rejected, avoiding credential forwarding to another origin or HTTP. Certificate/hostname checks remain enabled. |
| Uncontrolled allocation (395206107, -357880957) | Both scope export lists are limited to the existing 10,000-detail Pipeline cap, including the default and arbitrarily large requested limits. Full aggregate counts remain intact and truncation is reported. |
| Stored absolute path traversal (23022973) | Metadata must identify exactly `octane-suite-gate/<64 lowercase hex checksum>`. Resolve below the canonical build directory; reject symbolic-link roots, generations, and files. Open artifacts without following links and bound reads to 64 MiB, including files that grow during a read. |
| Java missing HSTS (-1335694275) | Snapshot/data endpoints emit `Strict-Transport-Security: max-age=31536000` when the servlet container reports HTTPS, including conditional responses. No trust is placed in arbitrary client forwarding headers. |
| JavaScript missing HSTS (-204149017) | HSTS is an HTTPS response policy, not a JavaScript/fetch request option. The API response fix applies to this fetch. Site-wide coverage, including static assets and proxy-terminated TLS, still requires the deployment configuration below. |

Artifact directories remain controller-managed; these checks do not make a Jenkins home writable
by untrusted OS users safe. Existing snapshot deserialization allowlists/limits and Jenkins read
permission checks are retained.

## Dependencies

Both POMs now require Jenkins 2.582. Do not install this HPI on an older controller. Core-provided
libraries are upgraded through the controller baseline, not bundled into the plugin classloader.

| Reported dependency | Findings | Resolved version / source |
| --- | ---: | --- |
| Bouncy Castle bcprov / bcpg / bcpkix 1.84 | 25 | API plugin `2.30.1.85.2-304.v4b_5b_62e59a_a_7`; bcprov 1.85.2, bcpg/bcpkix/bcutil 1.85. A direct plugin dependency enforces the installed minimum. |
| Jackson databind 3.2.1 | 2 | Jackson 3 API `3.2.2-96.v599957900a_1a_`, databind 3.2.2. |
| Jetty HTTP / WebSocket 12.1.11 | 2 | Test harness `2582.v92781333ea_78`, Jetty 12.1.12. Test dependencies are not shipped in the HPI; production servlet-container updates remain the Jenkins administrator's responsibility. |
| Spring beans / expression / web 7.0.8 | 5 | Jenkins 2.582 supplies 7.0.9. |
| Spring Security core / crypto 7.1.0 | 3 | Jenkins 2.582 supplies 7.1.1. |
| Ant 1.10.17 | 1 | Jenkins 2.582 supplies 1.10.18. |
| Commons Lang 2.6 | 1 | Absent from the resolved dependency graph with Jenkins 2.582. |
| Commons Collections 3.2.2 | 1 | **Unresolved upstream dependency**, still provided by Jenkins core. See below. |

The resolved versions address 39 of the 40 reported dependency entries according to the report's
affected-version ranges. This does not substitute for a new SCA scan or analysis of new advisories.

### Remaining Commons Collections Finding

Checkmarx ID `Cx78f40514-81ff` concerns self-referential `SetUniqueList.add()` causing stack overflow.
There is no direct `org.apache.commons.collections` / `SetUniqueList` usage in this plugin's source.
Nevertheless, Jenkins core still exposes Commons Collections 3.2.2 on the runtime classpath.
Commons Collections 4 uses different package names and is not a binary-compatible replacement.
Excluding the Maven dependency or adding collections4 would not remove the library from an
installed controller. No such misleading workaround was applied. Obtain an upstream Jenkins
fix or a security-owner reachability assessment/risk acceptance for this remaining finding.

## Deployment Requirements

1. Upgrade Jenkins to at least 2.582 and let its plugin manager install the declared API-plugin
   minimum versions. Independently update any external servlet container used to host Jenkins.
2. Replace all Octane HTTP mapping URLs with HTTPS and install internal CA certificates into the
   controller JVM truststore. There is no insecure fallback.
3. Serve Jenkins over HTTPS. At a TLS-terminating reverse proxy, set
   `Strict-Transport-Security: max-age=31536000` on HTTPS responses for the entire Jenkins origin,
   including static assets and error responses. Redirect HTTP to HTTPS. Configure trusted proxy
   forwarding correctly; do not enable `includeSubDomains` or `preload` without domain-wide review.
4. Verify the externally visible report, data, snapshot, and JavaScript URLs with `curl -I` over
   HTTPS. This repository cannot certify the headers of an unprovided deployment.
5. Re-run Checkmarx SAST/SCA against the updated source/dependency graph. Attach this record when
   reviewing sanitizer recognition and the remaining upstream Commons Collections finding.

## Regression Coverage

- TLS authentication and polling use a real HTTPS local server with a trusted test certificate,
  not trust-all TLS or a production HTTP exception; HTTP URLs and credential redirects are rejected.
- Artifact tests cover valid JSON round trips with hostile HTML text, malformed/non-object/trailing
  JSON, path traversal, symbolic-link files/directories, oversized files, page bounds, and the
  existing serialized-class rejection.
- Scope exports check the 10,000 cap, negative limits, explicit small limits and unmodified totals.
- Response tests check JSON isolation, HTTPS-only HSTS, untrusted forwarding headers, and actual
  Jenkins HTTP 200/304 responses for the report API.
- `src/test/resources/octane-test-tls.p12` is an intentionally public, test-only loopback keypair
  (password `test-only`). Surefire trusts it only in test JVMs. It is never a production credential
  and must not be copied into a deployed truststore or shipped in the HPI.

## Verification Results

Verified on September 17, 2026, against the clean build produced on September 16:

- `mvn -f pom-build.xml clean test spotless:check hpi:hpi dependency:tree
  -DoutputFile=target/security-dependency-tree.txt` completed through dependency-tree generation.
  The clean goal removed the project's generated build output before recompilation; the shared
  Maven dependency cache was not deleted.
- Surefire reports contain 482 tests across 59 suites: 480 passed, zero failures/errors, two
  skipped. The skips are the opt-in enterprise soak test and the injected properties test
  (no properties files exist).
- `TMPDIR=/home/embiti/octanePlugin/target/tmp node --test src/test/javascript/*.test.mjs`:
  all 100 tests passed, with no failures or skips. The shared temporary directory allows the
  sandboxed browser installations to access their test profiles.
- `spotless:check` and `git diff --check` passed.
- The packaged manifest requires Jenkins 2.582, Java 21, and the updated Jackson/Bouncy Castle
  API plugins. Archive inspection confirmed no test certificates, test classes, or bundled
  Jenkins core/test-harness libraries in the HPI or its nested plugin JAR.
- Artifact: `target/octane-suite-gate-by-embiti.hpi`, 600,746 bytes (approximately 587 KiB).
  SHA-256: `398a4c5ec8f89e3e0035e523b6125177e3a519b591f49b367449d44e5595b5b3`.

The topology-cache regression fixture now supplies an assigned tester so that it measures
topology request reuse rather than triggering unrelated missing-owner recovery calls; its
original request-count assertions and the production cache TTL remain unchanged. All 47
Octane client tests pass, including the separate owner-recovery cases.

A fresh Checkmarx scan and live reverse-proxy HSTS verification have not been performed. The
upstream Commons Collections finding above remains unresolved; these test results are not a
clean security-scan certification.

## References

- [Jenkins 2.582 changelog](https://www.jenkins.io/changelog/2.582/)
- [Jenkins September 2 security advisory](https://www.jenkins.io/security/advisory/2026-09-02/)
- [Bouncy Castle 1.85 release](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-85/)
- [Spring Framework advisory](https://spring.io/security/cve-2026-59283/)
- [Spring Security advisory](https://spring.io/security/cve-2026-59276/)
