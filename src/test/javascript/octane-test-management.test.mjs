import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const source = readFileSync(
    "src/main/webapp/js/octane-test-management.js",
    "utf8");
const jelly = readFileSync(
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
        + "OctaneGateReportAction/index.jelly",
    "utf8");

test("renders ten discrete execution intervals in bottom-up status order", () => {
  assert.match(
      source,
      /STATE_KEYS = \["passed", "failed", "blocked", "skipped"\]/);
  assert.match(source, /payload && payload\.executionIntervals/);
  assert.match(source, /Math\.max\(0, nonNegative\(point\[key\]\) - previous\[key\]\)/);
  assert.match(source, /intervals\.forEach\(function \(interval, partIndex\)/);
  assert.match(jelly, /flex-direction: column-reverse/);
  assert.doesNotMatch(
      source,
      /STATE_KEYS = \[[^\]]*(?:inProgress|planned)[^\]]*\]/);
});

test("renders dynamic failure clusters and keeps a valid category selected", () => {
  assert.match(source, /payload\.failureCategories/);
  assert.match(source, /category\.label/);
  assert.match(source, /button\.title = category\.label \|\| "Category"/);
  assert.match(source, /data-selected-category/);
  assert.match(source, /selected !== "all" && !available/);
  assert.match(source, /data-management-category-filter/);
  assert.match(source, /setSelectedCategory\(zone, category\)/);
  assert.match(source, /__octaneTestManagementOnCategorySelect/);
});

test("keeps focused defect groups on one line with conditional scroll controls", () => {
  assert.match(jelly, /data-management-failure-tab-nav="true"/);
  assert.match(jelly, /data-management-category-scroll="-1"/);
  assert.match(jelly, /data-management-category-scroll="1"/);
  assert.match(jelly, /aria-label="Scroll defect groups left"/);
  assert.match(jelly, /aria-label="Scroll defect groups right"/);
  assert.match(
      jelly,
      /\.octane-management-failure-switcher\s*\{[^}]*flex-wrap: nowrap;[^}]*overflow-x: auto;/s);
  assert.match(
      jelly,
      /\.octane-management-category-toggle\s*\{[^}]*flex: 0 0 auto;[^}]*white-space: nowrap;/s);
  assert.match(
      jelly,
      /\.octane-management-category-scroll\[data-visible="true"\]\s*\{[^}]*display: inline-flex;/s);
  assert.match(source, /function updateCategoryScrollControls\(container\)/);
  assert.match(source, /navigation\.clientWidth - \(columnGap \* 2\)/);
  assert.match(source, /container\.scrollWidth > availableWithoutControls \+ 1/);
  assert.match(source, /previous\.disabled = !hasOverflow \|\| container\.scrollLeft <= 1/);
  assert.match(source, /next\.disabled = !hasOverflow \|\| container\.scrollLeft >= maximum - 1/);
  assert.match(source, /new global\.ResizeObserver/);
  assert.match(source, /container\.scrollBy\(\{/);
  assert.match(source, /data-management-category-scroll/);
  assert.match(source, /scrollIntoView\(\{/);
});

test("builds content through safe DOM APIs and no HTML injection", () => {
  assert.doesNotMatch(source, /\.innerHTML\s*=/);
  assert.match(source, /textContent =/);
  assert.match(source, /document\.createElement/);
});

test("exposes responsive focus, scaling, and card controls", () => {
  assert.match(jelly, /\.octane-test-management-zone\.octane-zone-focused/);
  assert.match(
      jelly,
      /\.octane-test-management-card\s*\{[^}]*flex: 1 1 calc\(25% - 0\.75rem\)/s);
  assert.match(
      jelly,
      /\.octane-test-management-zone\.octane-zone-focused[\s\S]*nth-of-type\(3\)/);
  assert.match(jelly, /container-type: size/);
  assert.match(jelly, /font-size: clamp\(/);
  assert.match(jelly, /data-card-key="test-management-burndown"/);
  assert.match(jelly, /data-card-key="test-management-current-state"/);
  assert.match(jelly, /data-card-key="test-management-failures"/);
  assert.match(jelly, /data-card-key="test-management-metrics"/);
  assert.equal(new Set(
      (jelly.match(/data-card-key="test-management-[^"]+"/g) || [])
          .map((match) => match.slice("data-card-key=\"".length, -1))).size, 4);
});

test("uses legend-only headers and explicitly labelled vertical axes", () => {
  assert.match(jelly, /octane-management-subtitle-line/);
  assert.equal(
      (jelly.match(/data-management-legend="true"/g) || []).length >= 3,
      true);
  assert.match(jelly, /Testing Against Schedule/);
  assert.match(jelly, /Execution per Sprint Parts/);
  assert.doesNotMatch(jelly, /Burn-down Chart/);
  assert.doesNotMatch(jelly, /Current Execution State/);
  assert.doesNotMatch(jelly, /Defect Root-Cause Breakdown/);
  assert.match(jelly, /octane-management-y-axis-title">Total Test cases</);
  assert.match(jelly, /octane-management-y-axis-title">Tests Executed</);
  assert.match(jelly, /octane-management-y-axis-title">Defects</);
  assert.match(
      jelly,
      /\.octane-management-subtitle-line\s*\{[^}]*flex-wrap: nowrap;[^}]*overflow-x: auto;/s);
});

test("matches timer heights and preserves bounded scrollable bar tracks", () => {
  assert.match(
      jelly,
      /\.octane-timer-zone:not\(\.octane-zone-focused\)[\s\S]*?height: 280px;[\s\S]*?max-height: 280px;[\s\S]*?min-height: 280px;/);
  assert.match(
      jelly,
      /\.octane-test-management-zone:not\(\.octane-zone-focused\)[\s\S]*?> \.octane-test-management-card:not\(\.octane-expanded\)/);
  assert.match(
      jelly,
      /\.octane-management-state-bars\s*\{[^}]*--octane-management-bar-gap: clamp\(2px, 1cqw, 40px\);[^}]*--octane-management-bar-width: clamp\(8px, 4cqw, 100px\);[^}]*overflow-x: auto;/s);
  assert.match(
      jelly,
      /\.octane-management-state-column\s*\{[^}]*max-width: 100px;[^}]*min-width: 8px;/s);
  assert.match(
      jelly,
      /\.octane-management-failure-chart\s*\{[^}]*overflow-x: auto;/s);
  assert.match(
      jelly,
      /\.octane-management-failure-bar\s*\{[^}]*max-width: 100px;[^}]*min-width: 8px;/s);
  assert.match(
      jelly,
      /\.octane-management-failure-label\s*\{[^}]*overflow: hidden;[^}]*text-overflow: ellipsis;[^}]*white-space: nowrap;/s);
});

test("keeps standard grid axes, rounded lower quadrants, and capsule pills", () => {
  assert.match(
      jelly,
      /\.octane-management-plot-layout\s*\{[^}]*--octane-management-axis-gap: 0\.09rem;[^}]*column-gap: var\(--octane-management-axis-gap\)[^}]*grid-template-columns:\s*1\.35rem max-content minmax\(0, 1fr\)/s);
  assert.match(
      jelly,
      /\.octane-bar-graph\s*\{[^}]*column-gap: 0\.09rem;[^}]*grid-template-columns: 1\.35rem max-content minmax\(0, 1fr\)/s);
  assert.match(
      jelly,
      /\.octane-management-state-bars\s*\{[^}]*border-bottom: 1px solid/s);
  assert.match(jelly, /\.octane-management-failure-chart::before/);
  assert.match(jelly, /\.octane-management-failure-chart::after/);
  assert.match(
      jelly,
      /\.octane-management-failure-axis-layout\s*\{[^}]*--octane-management-failure-axis-row: 1\.65rem/s);
  assert.match(
      jelly,
      /\.octane-management-failure-group\s*\{[^}]*grid-template-rows:\s*minmax\(0, 1fr\) var\(--octane-management-failure-axis-row\)/s);
  assert.match(
      jelly,
      /\.octane-management-failure-chart::after\s*\{[^}]*bottom: calc\(var\(--octane-management-failure-axis-row\) - 1px\)/s);
  assert.match(
      jelly,
      /\.octane-management-metric-tile:nth-child\(3\)[^}]*border-bottom-left-radius: 14px/s);
  assert.match(
      jelly,
      /\.octane-management-metric-tile:nth-child\(4\)[^}]*border-bottom-right-radius: 14px/s);
  assert.match(
      jelly,
      /\.octane-management-defect-pill\s*\{[^}]*border-radius: 9999px[^}]*padding:\s*clamp\(0\.25rem, 1cqi, 0\.4rem\)\s*clamp\(0\.4rem, 1\.5cqi, 0\.75rem\)/s);
});

test("scales testing metric typography inside compact quadrant containers", () => {
  assert.match(
      jelly,
      /\.octane-management-metrics-grid\s*\{[^}]*container-name: octane-management-metrics;[^}]*container-type: size;/s);
  assert.match(
      jelly,
      /@container octane-management-metrics\s*\(max-width: 20rem\) or \(max-height: 14rem\)[\s\S]*?\.octane-management-metric-value\s*\{[^}]*font-size: clamp\(0\.72rem, min\(7cqi, 8cqh\), 1\.35rem\)/s);
  assert.match(
      jelly,
      /@container octane-management-metrics\s*\(max-width: 15rem\) or \(max-height: 10rem\)[\s\S]*?\.octane-management-metric-value\s*\{[^}]*font-size: clamp\(0\.64rem, min\(6\.5cqi, 7cqh\), 1rem\)/s);
});

test("normalizes and renders human-readable defect status and severity pills", () => {
  assert.match(source, /function canonicalSeverity\(value\)/);
  assert.match(source, /return "Very High"/);
  assert.match(source, /return "Unspecified"/);
  assert.match(source, /function displayedSeverity\(defect\)/);
  assert.match(source, /defect && defect\.severityLabel/);
  assert.match(
      source,
      /return configuredLabel \|\| canonicalSeverity\(defect && defect\.severity\)/);
  assert.match(source, /var severityLabel = displayedSeverity\(defect\)/);
  assert.match(source, /function canonicalStatus\(defect\)/);
  assert.match(source, /return defect\.open \? "Open" : "Closed"/);
  assert.doesNotMatch(source, /category\.(?:openColor|closedColor)/);
  assert.match(
      source,
      /statusLabel === "Open" \? colors\.open : colors\.closed/);
  assert.match(
      source,
      /defect\.severityColorKey \|\| defect\.severity \|\| severityLabel/);
  assert.match(source, /status\.setAttribute\("aria-label", "Status: " \+ statusLabel\)/);
  assert.match(
      source,
      /severity\.setAttribute\("aria-label", "Severity: " \+ severityLabel\)/);
});

test("resolves semantic chart and metric colors from the active theme", () => {
  assert.match(source, /blocked: "--octane-status-blocked"/);
  assert.match(source, /closed: "--octane-color-good"/);
  assert.match(source, /failed: "--octane-status-failed"/);
  assert.match(source, /open: "--octane-color-bad"/);
  assert.match(source, /passed: "--octane-status-passed"/);
  assert.match(source, /global\.getComputedStyle\(zone\)\.getPropertyValue\(propertyName\)/);
  assert.match(source, /colorsFor\(payload, zone\)/);
  assert.match(source, /critical: "--octane-severity-critical"/);
  assert.match(source, /veryhigh: "--octane-severity-very-high"/);
  assert.match(source, /high: "--octane-severity-high"/);
  assert.match(source, /low: "--octane-severity-low"/);
  assert.match(source, /medium: "--octane-severity-medium"/);
  assert.match(source, /unspecified: "--octane-severity-unspecified"/);
  [
    ["--octane-severity-critical", "#FF3B30", "#FF453A"],
    ["--octane-severity-very-high", "#FFCC00", "#FFD60A"],
    ["--octane-severity-high", "#FF9500", "#FF9F0A"],
    ["--octane-severity-low", "#5AC8FA", "#64D2FF"],
    ["--octane-severity-medium", "#AF52DE", "#BF5AF2"],
    ["--octane-severity-unspecified", "#8E8E93", "#8E8E93"]
  ].forEach(([property, light, dark]) => {
    assert.match(jelly, new RegExp(`${property}: ${light}`));
    assert.match(jelly, new RegExp(`${property}: ${dark}`));
  });
  assert.match(
      jelly,
      /\.octane-management-tone-good\s*\{[^}]*background: var\(--octane-color-good\);[^}]*color: var\(--octane-color-on-emphasis\);/s);
  assert.match(
      jelly,
      /\.octane-management-tone-bad\s*\{[^}]*background: var\(--octane-color-bad\);[^}]*color: var\(--octane-color-on-emphasis\);/s);
  assert.match(
      jelly,
      /\.octane-management-category-toggle\[aria-pressed="true"\]\s*\{[^}]*background: var\(--octane-color-neutral\);[^}]*color: var\(--octane-color-on-emphasis\);/s);
  assert.doesNotMatch(
      jelly,
      /\.octane-management-tone-(?:good|bad)\s*\{[^}]*(?:#34C759|#FF3B30|#ffffff)/s);
});

test("keeps defect ids, descriptions, and uniform pills in explicit columns", () => {
  assert.match(source, /octane-management-defect-id/);
  assert.match(
      source,
      /row\.appendChild\(identifier\);\s*row\.appendChild\(description\);\s*row\.appendChild\(pills\)/s);
  assert.match(
      jelly,
      /\.octane-management-defect-row\s*\{[^}]*display: grid;[^}]*grid-template-columns:\s*minmax\(3\.5rem, max-content\) minmax\(0, 1fr\) max-content;/s);
  assert.match(
      jelly,
      /--octane-management-pill-width:\s*clamp\(6\.25rem, calc\(11ch \+ 1\.5rem\), 8\.5rem\)/s);
  assert.match(
      jelly,
      /\.octane-management-defect-pills\s*\{[^}]*grid-template-columns:\s*repeat\(2, var\(--octane-management-pill-width\)\)/s);
  assert.match(
      jelly,
      /\.octane-management-defect-pill\s*\{[^}]*inline-size: var\(--octane-management-pill-width\)[^}]*text-align: center;/s);
  assert.match(
      jelly,
      /@container octane-management-defects \(max-width: 32rem\)/);
  assert.match(
      jelly,
      /@container octane-management-defects \(max-width: 22rem\)/);
});

test("places test management between timer and reporting zones", () => {
  const timerIndex = jelly.indexOf("id=\"octane-timer-zone\"");
  const managementIndex = jelly.indexOf("id=\"octane-test-management-zone\"");
  const reportIndex = jelly.indexOf("id=\"octane-report-zone\"");

  assert.ok(timerIndex >= 0);
  assert.ok(managementIndex > timerIndex);
  assert.ok(reportIndex > managementIndex);
});
