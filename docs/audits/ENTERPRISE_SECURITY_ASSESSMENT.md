# Enterprise Security And Vulnerability Assessment

Date: 2026-07-30

## Scope And Method

This white-box assessment covers Jenkins configuration, Pipeline and Freestyle inputs, ALM Octane
HTTP egress, credentials, build logs, report rendering, persisted report artifacts, browser capture,
and background email work. Evidence includes source review, negative tests, Jenkins authorization
tests, PMD, SpotBugs, the official OSV scanner 2.3.8, and repository CI security workflows.

Trust boundaries:

1. Jenkins administrators control the Octane space mapping or global server entry and Jenkins
   Credentials.
2. Job authors select the administrator-owned server and supply suite, release, sprint, query, and
   criteria values.
3. Octane JSON is untrusted remote input and must remain bounded and escaped.
4. Report endpoints are build-scoped and subject to Jenkins item permissions and crumb rules.

## Findings And Controls

| Risk | Mapping | Control and verification | Status |
| --- | --- | --- | --- |
| Outbound target manipulation | CWE-918 | `OctaneServerUrl` permits only HTTP(S), rejects user info/query/fragment, and locks every request to the configured scheme, host, effective port, and base path. Redirects are disabled. | Controlled |
| Credential disclosure | CWE-200, CWE-798 | Runtime secrets are resolved from Jenkins `StandardUsernamePasswordCredentials` as `ACL.SYSTEM2`; only credential IDs are persisted. Source tests reject credential-like literals and logging does not include request bodies. | Controlled |
| Input abuse and log forging | CWE-20, CWE-117 | Numeric entity IDs, bounded release/optional-sprint selectors, 4,096-character Octane queries, 8,192-character criteria, token/depth limits, and single-line bounded logging are enforced. | Controlled |
| HTML or script injection | CWE-79 | Jelly escaping, explicit email/static-report escaping, and safe DOM text construction cover API and job data. Renderer and frontend tests exercise hostile values. | Controlled |
| Unsafe process or TLS behavior | CWE-78, CWE-295 | Browser capture uses Jenkins `Launcher` argument lists; source audit rejects direct process execution and permissive TLS/hostname bypasses. | Controlled |
| Deserialization or artifact abuse | CWE-502, CWE-400 | Snapshot artifacts are size-bounded, atomically published, and read through depth/reference/array/class allowlists. | Controlled |
| Network, parser, and scheduler exhaustion | CWE-400, CWE-770 | HTTP bodies are capped at 16 MiB, requests are limited to eight per Octane server, retries are bounded, scheduler admission is 1,024, and canceled futures are removed. A cancel/reschedule race found by soak testing was fixed by serializing each task's schedule and cancel transitions. | Fixed |
| Bright status cell contrast | WCAG 1.4.3 | Green, orange, and red email value cells now use black text in both themes; fallback states reset background and text color. | Fixed |

## Jenkins Sandbox And Authorization

- Pipeline-facing objects are data-bound configuration only. No Groovy evaluation, Script Security
  approval bypass, shell interpolation, arbitrary class loading, or security-manager override was
  found.
- Global configuration writes require `Jenkins.ADMINISTER`. Build report reads and state changes use
  Jenkins item permissions, POST handlers, and crumb protection.
- The centrally controlled Octane mapping and Jenkinsfile, plus the legacy configured server list,
  are the egress trust boundaries. Job-specific variables select a mapped shared space and do not
  contain a URL. Anyone allowed to alter the central Jenkinsfile could alter the injected endpoint,
  so that repository requires protected branches and restricted write access. Per-request origin and
  base-path checks and disabled redirects constrain requests after the trusted endpoint is selected.

## Automated Security Evidence

- SpotBugs 4.9.8.2: zero bug instances and zero analysis errors.
- PMD 7.17 complexity gate: zero production methods above complexity 10 after remediation.
- `SourceSecurityAuditTest`, URL validation, input bounds, XSS, authorization, response-size, and
  deserialization tests: passed in the 287-test Java suite.
- Official OSV Scanner 2.3.8: no unresolved findings after applying three documented exceptions in
  `osv-scanner.toml`.
- `.github/workflows/jenkins-security-scan.yml` and `dependency-security.yml` remain mandatory CI
  release gates, including OSV SARIF and NVD-backed OWASP Dependency-Check.

## Dependency Exceptions

All exceptions are explicit and reviewable. `commons-lang:2.6` is provided by Jenkins core and is not
packaged in this plugin HPI. The plugin baseline is Jenkins 2.568.1, which provides Spring Framework
7.0.8 and Spring Security 7.1.0; the former Spring Framework exception is therefore no longer needed.

## Residual Risk

- Corporate network policy should still restrict controller and agent egress independently of the
  application allowlist.
- Protect the central mapping/Jenkinsfile repository with reviewed changes; dynamic endpoint
  injection deliberately replaces the Jenkins System server allowlist for that Pipeline path.
- A representative staging tenant must validate Octane authorization, throttling, SMTP delivery,
  browser execution, and controller sizing.
- Dependency exceptions must not be renewed without a fresh reachability review and a controller
  upgrade plan.
- No security review is a guarantee against future vulnerabilities; CI scans and Jenkins/plugin
  upgrades remain continuous controls.
