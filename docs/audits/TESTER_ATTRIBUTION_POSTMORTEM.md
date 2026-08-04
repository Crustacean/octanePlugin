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
5. A later compatibility expansion accepted parent `run_by`, `assigned_to`, `assignee`, and nested
   test owner as grouping aliases. That made the repository contract ambiguous again and allowed
   execution or test ownership data to compete with the suite's configured **Default run by**.
6. A topology cached while the parent identity was temporarily absent was returned without another
   parent lookup, so a transient blank value could keep rendering as `Unassigned` during polling.

This explains `Unassigned (78834)`: the parent suite owner was not the sole source of the grouping
key, and the child path reached the fallback before a stable parent identity was applied.

## Permanent Boundary

- Parent suite payloads are mapped to the immutable `OctaneSuiteTopologyCache.Topology` DTO. Its
  `suiteOwnerName` comes strictly from parent `default_run_by`, falling back only to the direct
  parent suite `owner` relationship.
- Parent or child `run_by`, `native_tester`, `assigned_to`, `assignee`, test owner, and child owner
  can never supply `suiteOwnerName`.
- A cached topology with a blank owner is rechecked against the parent suite endpoint until a
  nonblank `default_run_by` or suite `owner` is available. Blank values are never session-locked.
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

The automated tests cover `default_run_by = "Jane Doe"`, direct suite-owner fallback, changing
Jenkins/manual child executors, and cached parent payloads that initially omit assignment. Negative
tests prove `assigned_to`, `assignee`, parent/child `run_by`, and test owners cannot become grouping
keys. Separate model tests prove that changing execution actor data cannot alter the immutable
suite owner and vice versa.
