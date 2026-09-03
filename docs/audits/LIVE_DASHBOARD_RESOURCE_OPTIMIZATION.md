# Live Dashboard Resource Optimization

## Scope

This change targets the live Jenkins report page under high-volume workloads (3,000 or
more defects and up to 80 visible tester bars per chart page). It does not change Octane
polling, report snapshots, or email rendering.

## Event Handling

- Tester-bar hover remains delegated to one `mousemove`, `mouseout`, `focusin`, and
  `focusout` listener on the dashboard. Deferred bars do not allocate tooltip DOM trees or
  listeners.
- Mouse tooltip work is coalesced into one 16 ms trailing update. Viewport scroll/resize
  repositioning is debounced to 100 ms.
- Fluid bar chart resize work is debounced to 80 ms and then committed in one animation
  frame, avoiding repeated synchronous measurement during a resize burst.
- Failure Analysis category and sort actions remain delegated to one listener on the test
  management zone.

## DOM And Layout

- Failure Analysis creates defect rows once per polling payload and caches the nodes on the
  list. Category changes update `hidden` state and ARIA row counts instead of clearing and
  reconstructing thousands of rows.
- Sorting reorders cached row nodes; it does not recreate their status/severity pills.
- A new polling payload invalidates the row cache so changed defects are still rendered.
- X-axis labels use measured text width and the computed bar slot. Dense labels rotate and
  truncate within a bounded margin, preventing labels from increasing the page width.
- Failure Analysis reserves a digit-aware Y-axis value gutter (`2ch` minimum, increasing
  for 3+ digit values), avoiding overlap without a fixed oversized margin.

## Expected Impact

- Tab switches perform no row construction and no table-wide text/style allocation.
- Tooltip placement is capped at roughly one update per animation interval rather than one
  update per raw mouse event.
- Resize bursts produce one bar-layout pass after the burst instead of one pass per event.
- Memory remains bounded to one defect row per rendered payload entry; category tabs do not
  duplicate rows.
