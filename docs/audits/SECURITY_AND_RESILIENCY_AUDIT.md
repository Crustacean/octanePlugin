# Security And Resiliency Audit

Date: 2026-07-21

Scope: Octane Suite Gate by Embiti, including Stapler endpoints, Jenkins credentials use, ALM
Octane HTTP access, report persistence, polling, screenshots, email delivery, and Pipeline return
values.

## Executive Summary

The audit found two high-severity and six medium-severity defense gaps. The highest-risk issues were
unfiltered Java deserialization of a build-owned compatibility snapshot and unbounded remote response
processing. The implemented changes add context-specific deserialization filtering, strict resource
limits, redirect-free administrator-owned Octane destinations, query-atom validation, bounded
pagination, per-build email delivery locks, and bounded Pipeline/report surfaces.

No use of `eval`, runtime script compilation, shell command construction, plaintext credential
persistence, or credential logging was found. Browser commands use Jenkins `Launcher.cmds(List)` and
therefore do not pass user input through a shell. Octane credentials are resolved through the Jenkins
Credentials API under `ACL.SYSTEM2`; plaintext exists only transiently while constructing the
authentication request.

Private and loopback Octane hosts remain supported because on-premises corporate Octane is a valid
deployment model. The SSRF boundary is enforced by requiring either a protected, centrally
controlled space mapping/Jenkinsfile or an administrator-owned legacy server configuration,
validating the final URI, prohibiting embedded credentials/query/fragment data, and refusing
redirects. Job-specific variable files select a mapped space but cannot supply a URL. Write access
to the central Pipeline repository is privileged because it can change the dynamically injected
endpoint.

## Security Findings

### OSG-2026-001: Unfiltered compatibility snapshot deserialization

- Severity: High
- CWE: CWE-502, Deserialization of Untrusted Data
- Impact: A modified `octane-snapshot.bin.gz` could cause classes outside the report model to be
  instantiated during report loading. Exploitation requires write access to a build directory or a
  compromised storage path, but the consequence can include controller-side code execution when a
  suitable gadget is present.
- Fix: `OctaneReportArtifactStore` now installs a stream-specific `ObjectInputFilter`. It permits
  only plugin model/entity classes and required JDK value/collection classes, and rejects excessive
  depth, references, arrays, and stream bytes. Compressed artifact files are size checked before
  reading.
- Verification: A compatibility snapshot still round-trips; a serialized `java.io.File` payload is
  rejected before object construction completes.

### OSG-2026-002: Unbounded Octane response and pagination processing

- Severity: High
- CWE: CWE-400, Uncontrolled Resource Consumption
- Impact: A compromised, malfunctioning, or misdirected Octane endpoint could return an unlimited
  response body or repeated full pages, exhausting controller heap or holding poll workers
  indefinitely across multiple jobs.
- Fix: JSON responses are streamed with a 16 MiB hard limit. Child-run pagination retains only IDs
  explicitly requested and stops when a page makes no progress. Defect pagination remains bounded by
  the configured limit, now capped at 10,000.
- Verification: Tests reject an oversized body and terminate a repeated 200-row unrelated page after
  one request.

### OSG-2026-003: Octane query injection through entity IDs

- Severity: Medium
- CWE: CWE-943, Improper Neutralization in Data Query Logic
- Impact: Suite, run, test, or defect IDs containing Octane query operators could alter an internally
  generated query and return data outside the intended gate scope.
- Fix: User-facing workspace and suite IDs are restricted to 1-18 digits. Internal query atoms accept
  only bounded alphanumeric, underscore, and hyphen IDs, accommodating test fixtures and server IDs
  without accepting Octane query syntax. Custom scope and defect queries remain explicit features.
- Verification: A suite ID containing an injected `OR` expression is rejected before any query is
  sent.

### OSG-2026-004: Redirectable connectivity probe and weak URL validation

- Severity: Medium
- CWE: CWE-918, Server-Side Request Forgery
- Impact: An administrator connectivity test could follow a redirect to a second destination, and a
  malformed configured URI could contain userinfo, query, or fragment data.
- Fix: Base URLs require HTTP(S), a host, and no userinfo/query/fragment. The test endpoint is
  `@RequirePOST`, checks `Jenkins.ADMINISTER`, uses a 10-second timeout, and never follows redirects.
  Runtime clients repeat strict URL validation.
- Residual trust: Jenkins administrators may intentionally configure private corporate hosts. This is
  equivalent to other administrator-managed service integrations and is not exposed to Pipeline URL
  input.

### OSG-2026-005: Report endpoint fail-open state and reader/writer contention

- Severity: Medium
- CWE: CWE-862, Missing Authorization; CWE-667, Improper Locking
- Impact: Permission checks were skipped if a detached action had no `Run`, and synchronized JSON or
  artifact reads could block live snapshot publication under concurrent dashboard refreshes.
- Fix: report/data/script reads require an attached run and `Item.READ`; manual exit requires
  `Run.UPDATE`. Missing-run state fails closed. Immutable snapshot and metadata references are
  volatile, and expensive endpoint rendering/file reads no longer hold the action monitor.

### OSG-2026-006: Screenshot and attachment cross-talk within one build

- Severity: Medium
- CWE: CWE-362, Concurrent Execution Using Shared Resource
- Impact: Final and interval emails for the same build used identical HTML, screenshot, and Chrome
  profile paths. Overlap could replace the image between capture and Mailer attachment consumption.
- Fix: `OctaneEmailDeliveryCoordinator` holds a fair, reference-counted lock across capture,
  optional archive, body rendering, and SMTP handoff. The key includes build externalizable ID and
  workspace. Entries are removed after the final waiter to avoid a registry leak.
- Verification: Concurrent delivery for the same build blocks until the first lease closes, then the
  registry returns to zero entries.

### OSG-2026-007: Unbounded criteria, ledger, screenshot, and log-capture inputs

- Severity: Medium
- CWE: CWE-400, Uncontrolled Resource Consumption
- Impact: Deep criteria recursion, indefinite defect history, extreme screenshot dimensions, or
  verbose subprocess output could consume controller or agent memory.
- Fix: Criteria are limited to 8,192 characters, 1,024 tokens, and 64 nesting levels. Defect history
  is capped at 10,000 IDs while existing IDs continue to refresh. Poll/timeout/viewport dimensions
  are bounded. Screenshot height is capped at 16,384 pixels. Browser-probe output is bounded, and
  inline Mailer screenshots are capped at 25 MiB while normal Jenkins logging remains available.

### OSG-2026-008: Unbounded Pipeline/CPS result graph

- Severity: Medium
- CWE: CWE-400, Uncontrolled Resource Consumption
- Impact: Returning every child run in `runs`, `suiteRuns`, and every scope could copy more than
  100,000 records into Pipeline CPS state, multiplying heap and persistence cost.
- Fix: aggregate metrics remain complete, but detailed Pipeline records are capped at 10,000.
  `runCount`, `suiteRunCount`, and `detailsTruncated` make the behavior explicit. Scope detail budget
  is divided across configured scopes.
- Compatibility note: jobs that process more than 10,000 raw child details should use aggregate
  fields or the paged report data endpoint rather than treating the Pipeline return map as an export
  API.

## Access Control Review

| Surface | Required authority | Result |
| --- | --- | --- |
| Report snapshot/data/script | Build `Item.READ` | Enforced and fail closed |
| Exit Octane and Continue | Build `Run.UPDATE`, POST crumb | Enforced |
| Global server configuration | `Jenkins.ADMINISTER` | Explicitly enforced |
| Credentials selector and server test | `Jenkins.ADMINISTER` | Enforced |
| Connectivity test | `Jenkins.ADMINISTER`, POST crumb | Enforced; redirects disabled |
| Pipeline gate/email execution | Jenkins Pipeline execution context | No standalone Stapler mutation |

Descriptor field checks are read-only validators. They do not reveal credential values or execute
network requests. Jenkins guidance recommends returning a neutral validation result to unauthorized
users rather than turning every validator into an authentication redirect; the state-changing and
network-active surfaces above carry explicit checks.

## Concurrency And Load Analysis

### Target Model

- 30 concurrent gates.
- More than 700 suite runs per gate.
- Up to 150 child tests per suite, or 105,000 child records per poll per job.
- 3.15 million child records across one simultaneous poll wave.

### Bounded Components

| Component | Bound / behavior |
| --- | --- |
| Octane requests | Fair per-server semaphore, default 8 in flight, configurable 1-64 |
| Topology cache | 30-second TTL, 20,000-entry LRU, single-flight loading |
| Query fan-out | 40 IDs per query, 200 records per page |
| JSON response | 16 MiB per response |
| Suite IDs per gate/scope | 1,000 |
| Defect retention | 10,000 unique defects per gate |
| Criteria | 8 KiB, 1,024 tokens, 64 nesting levels |
| Pipeline detail | 10,000 raw run records plus complete aggregates |
| Live report DOM | 80-bar active window with paged data |
| Progress email scheduler | 4 delivery workers, 256 active schedules |
| Octane poll workers | 32 daemon workers, 256 queued polls |
| Screenshot | 3,840 x 16,384 maximum surface |
| Same-build email work | One capture-to-SMTP operation at a time |

### Poll And Finalization Lifecycle

The Pipeline step persists a one-poll state machine. Jenkins Timer schedules short wake-ups; a
dedicated bounded daemon pool performs the blocking poll. `OctanePollRefreshCoordinator` coalesces normal and interval-email
refresh requests, and cancellation closes the client/session and cancels outstanding futures. Retry
count is three with bounded exponential backoff and `Retry-After` support. Email scheduler entries
and delivery locks are removed on cancellation/completion.

Manual exit remains a crumb-protected POST requiring `Run.UPDATE`. The first accepted request records
an immutable per-build timestamp under the existing manual-exit lock, wakes the poll state machine,
and makes repeated submissions idempotent. The dashboard freezes its testing timer at that timestamp
and hides the command while finalization is pending. This presentation state does not decide the gate
result: the runner still performs and evaluates the authoritative final Octane poll before completing
the Pipeline step.

### Remaining Bottleneck

The poll reducer still retains the complete `RunRecord` graph while criteria, tester bars, defect
linkage, and report artifacts are produced. A 105,000-record single-job fixture is covered, and the
existing 30-job fixture validates concurrent compact mapping at 25,000 records/job. Running all 30
jobs at 105,000 records each concurrently remains a capacity test for production-like hardware, not
a reasonable unit-test allocation. For that ceiling, provision controller heap from measured JFR
data and stagger poll phases; a future page-consumer reducer can remove the retained-record floor.

## Verification Matrix

- Strict URL and query-atom rejection.
- Oversized response rejection and no-progress pagination termination.
- Compatibility snapshot round-trip and foreign-class rejection.
- Criteria size/depth limits and unchanged criteria semantics.
- Defect retention cap with refresh of known IDs.
- Per-build email serialization and registry cleanup.
- Pipeline detail truncation metadata.
- Screenshot dimension limits.
- Existing 30-job compact report acceptance plus one 700 x 150 dense fixture.
- Full Maven suite: 199 tests, 0 failures, 0 errors, 1 skipped injected harness check.
- SpotBugs 4.9.8.2: 0 findings and 0 analysis errors after a clean compilation.
- Spotless, `git diff --check`, and clean HPI packaging all pass.

## References

- Jenkins, [Securely implementing form validation](https://www.jenkins.io/doc/developer/security/form-validation/)
- Jenkins, [Miscellaneous API usage recommendations](https://www.jenkins.io/doc/developer/security/misc/)
- Oracle, [Java serialization filtering](https://docs.oracle.com/en/java/javase/21/core/java-serialization-filters.html)
