# Tester Attribution Regression Post-Mortem

## Impact

Automated child runs could appear under `Unassigned (<suite-run-id>)`, a Jenkins executor, or
another child-level identity instead of the parent suite run's **Default run by** person. The same
incorrect bucket could flow into progress bars, tester details, report screenshots, and email
aggregations. Automation totals were also at risk because grouping identity and execution identity
shared one ambiguous data model.

## Root Cause

The Octane JSON tree was not mutated in place. The leak was semantic:

1. `RunRecord` convenience constructors copied one supplied person into both `runByName` and
   `assignedToName`. A child executor could therefore become a display owner before aggregation.
2. Child-run parsing populated `assignedToName` from child assignment, owner, or test-owner fields.
3. Scoped-run attribution retained that child value when a parent run-ID match was unavailable.
4. Earlier fallback code inferred a suite grouping from child assignments and cached the inferred
   value. A transient `run_by` value such as Jenkins, or an absent child assignment, could therefore
   persist as the UI grouping key.
5. A compatibility expansion mixed valid parent relationship aliases with child execution and test
   ownership fallbacks. Removing every alias then over-corrected the problem: Octane instances that
   expose parent **Default run by** as parent `run_by`, `assigned_to`, or `assignee` could no longer
   resolve it.
6. A topology cached while the parent identity was temporarily absent was returned without another
   parent lookup, so a transient blank value could keep rendering as `Unassigned` during polling.
7. The compatibility lookup stopped at the suite-run entity. Octane defines **Default run by** on
   the test-suite plan and may omit it from suite-run projections, while retaining it on the test
   suite referenced by the parent run's `test` relationship. The client never followed that
   relationship, so a valid planned human assignment still collapsed to `Unassigned`.

This explains `Unassigned (78834)`: the parent suite owner was not the sole source of the grouping
key, and the child path reached the fallback before a stable parent identity was applied.

### Octane 16.2.100 compatibility note

`Unassigned (<number>)` is not a distinct tester returned by Octane. On 16.2.100, a suite-run
projection can omit every supported parent assignment relationship (`default_run_by`, human
`run_by`, `assigned_to`, `assignee`, and `owner`) even though its child runs remain available. The
plugin historically rendered that missing assignment as `Unassigned (` plus the suite-run ID plus
`)`, which made each affected suite look like a different tester. Chart and email aggregation now
normalize blank assignments, plain `Unassigned`, and every case-insensitive
`Unassigned (<numeric-id>)` variant to one `Unassigned` identity before grouping. The numeric suffix
is therefore treated only as legacy diagnostic context and never as part of a tester key.

## Permanent Boundary

- Parent suite payloads are mapped to the immutable `OctaneSuiteTopologyCache.Topology` DTO. Its
  `suiteOwnerName` comes from parent `default_run_by`, human parent `run_by`, parent `assigned_to`,
  parent `assignee`, related test-suite Default run by, or direct parent `owner`, in that precedence
  order. These are parent-schema compatibility paths for the same suite attribution boundary.
- Child `run_by`, child `native_tester`, child `assigned_to`, child `assignee`, test owner, and child
  owner can never supply `suiteOwnerName`. Known system identities are also rejected from parent
  `run_by` before the remaining parent fallbacks are evaluated.
- A cached topology with a blank owner is rechecked against the parent suite endpoint until a
  nonblank parent assignment alias or suite `owner` is available. Blank values are never
  session-locked.
- Before falling back to generic suite `owner`, an unresolved parent follows its `test`
  relationship and reads Default run by from the aggregate `tests` resource or the `test_suites`
  subtype resource. This preserves the suite-plan attribution when a server omits it from the
  suite-run projection.
- The first nonblank parent owner is locked per server, credential, shared space, workspace, and
  suite-run key for the lifetime of the polling client.
- Child payload parsing records `run_by`, or `native_tester` as its compatibility fallback, only as
  `executionActorName`. It never supplies a suite owner and no longer requests child/test-owner
  data for grouping.
- UI, chart, tester, risk, management, and email aggregators use `getSuiteOwnerName()` only.
- Automation calculations use `getExecutionActorName()` only.
- Immutable copy methods return updated child records without modifying the parent topology or the
  original record.

The persisted `runByName` and `assignedToName` field names remain unchanged so previously saved
Jenkins report snapshots continue to deserialize.

## Regression Proof

The automated tests cover `default_run_by = "Jane Doe"`, human parent `run_by`, parent
`assigned_to`/`assignee` aliases, related test-suite Default run by, direct suite-owner fallback,
changing Jenkins/manual child executors, and cached parent payloads that initially omit assignment.
Negative tests prove child assignment fields, child `run_by`, system parent `run_by`, and test
owners cannot become grouping keys. Separate model tests prove that changing execution actor data
cannot alter the immutable suite owner and vice versa.
