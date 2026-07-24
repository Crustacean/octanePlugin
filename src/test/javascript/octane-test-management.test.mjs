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
  assert.match(
      source,
      /renderCategorySwitcher\(switcher, categories, payload\.totalDefects\)/);
  assert.match(
      source,
      /var allLabel = "All " \+ failureCategoryTotal\(categories, totalDefects\)/);
  assert.match(
      source,
      /Number\.isFinite\(reportedTotal\) && reportedTotal >= 0/);
  assert.match(
      source,
      /total \+ nonNegative\(category\.open\) \+ nonNegative\(category\.closed\)/);
  assert.match(source, /category\.label/);
  assert.match(source, /button\.title = category\.label \|\| "Category"/);
  assert.match(source, /data-selected-category/);
  assert.match(source, /selected !== "all" && !available/);
  assert.match(source, /data-management-category-filter/);
  assert.match(source, /setSelectedCategory\(zone, category\)/);
  assert.match(source, /__octaneTestManagementOnCategorySelect/);
});

test("reveals the clicked failure bar and matching tab after individual focus opens", () => {
  assert.match(
      jelly,
      /function expandFailureCategory\(categoryBar, category\)\s*\{[\s\S]*?expandCard\(card\);[\s\S]*?OctaneTestManagement\.revealFailureCategory\(\s*testManagementZone, category\);/);
  assert.match(source, /function scheduleFailureCategoryReveal\(zone, category\)/);
  assert.match(source, /global\.requestAnimationFrame\(function \(\) \{/);
  assert.match(source, /function revealFailureCategory\(zone, category\)/);
  assert.match(
      source,
      /card\.classList\.contains\("octane-expanded"\)/);
  assert.match(
      source,
      /categoryElement\(chart, "data-management-category", category\)/);
  assert.match(
      source,
      /categoryElement\(switcher, "data-management-category-filter", category\)/);
  assert.equal(
      (source.match(/scrollHorizontalItemIntoView\(/g) || []).length >= 3,
      true);
  assert.match(source, /container\.scrollWidth <= container\.clientWidth \+ 1/);
  assert.match(
      source,
      /container\.scrollTo\(\{behavior: "auto", left: nextScrollLeft\}\)/);
  assert.match(
      source,
      /revealFailureCategory: scheduleFailureCategoryReveal/);
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
      /\.octane-management-failure-chart\s*\{[^}]*overflow-x: hidden;[^}]*scrollbar-width: none;/s);
  assert.match(
      jelly,
      /\.octane-chart-card\[data-card-key="test-management-failures"\]\.octane-expanded\s*\.octane-management-failure-chart\s*\{[^}]*overflow-x: auto;[^}]*overscroll-behavior-inline: contain;[^}]*scrollbar-color: var\(--octane-management-scrollbar-thumb\) transparent;[^}]*scrollbar-gutter: stable;[^}]*scrollbar-width: thin;/s);
  assert.match(
      jelly,
      /\.octane-chart-card\[data-card-key="test-management-failures"\]\.octane-expanded\s*\.octane-management-failure-chart::-webkit-scrollbar\s*\{[^}]*height: 6px;/s);
  assert.match(
      jelly,
      /\.octane-chart-card\[data-card-key="test-management-failures"\]\.octane-expanded\s*\.octane-management-failure-chart::-webkit-scrollbar-track\s*\{[^}]*background: transparent;/s);
  assert.match(
      jelly,
      /\.octane-chart-card\[data-card-key="test-management-failures"\]\.octane-expanded\s*\.octane-management-failure-chart::-webkit-scrollbar-thumb\s*\{[^}]*background: var\(--octane-management-scrollbar-thumb\);[^}]*border-radius: 999px;/s);
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
      /@container octane-management-metrics\s*\(max-width: 20rem\) or \(max-height: 14rem\)[\s\S]*?\.octane-management-metric-tile\s*\{[^}]*padding: clamp\(2px, 1\.8cqi, 0\.45rem\);[^}]*text-align: center;/s);
  assert.match(
      jelly,
      /@container octane-management-metrics\s*\(max-width: 20rem\) or \(max-height: 14rem\)[\s\S]*?\.octane-management-metric-title\s*\{[^}]*font-size: clamp\(0\.46rem, min\(3\.6cqi, 4\.2cqh\), 0\.62rem\);[^}]*font-weight: 400;/s);
  assert.match(
      jelly,
      /@container octane-management-metrics\s*\(max-width: 20rem\) or \(max-height: 14rem\)[\s\S]*?\.octane-management-metric-value\s*\{[^}]*font-size: clamp\(0\.64rem, min\(6\.2cqi, 7cqh\), 1\.15rem\);[^}]*font-weight: 400;/s);
  assert.match(
      jelly,
      /@container octane-management-metrics\s*\(max-width: 15rem\) or \(max-height: 10rem\)[\s\S]*?\.octane-management-metric-value\s*\{[^}]*font-size: clamp\(0\.56rem, min\(5\.5cqi, 6cqh\), 0\.88rem\)/s);
});

test("clips every metric quadrant and keeps tester rows on one line", () => {
  assert.match(
      jelly,
      /\.octane-management-metric-tile\s*\{[^}]*overflow: hidden;[^}]*padding: clamp\(2px, 2\.2cqi, 0\.9rem\);/s);
  assert.match(
      source,
      /tile\.setAttribute\(\s*"data-management-metric-key",\s*String\(metric\.key \|\| ""\)\.trim\(\)\.toLowerCase\(\)\);/s);
  assert.match(
      jelly,
      /\.octane-management-metric-tile\[data-management-metric-key="tester-volume"\][\s\S]*?\.octane-management-metric-items li,[\s\S]*?data-management-metric-key="tester-defects"[\s\S]*?\.octane-management-metric-items li\s*\{[^}]*flex-wrap: nowrap;[^}]*overflow: hidden;/s);
  assert.match(
      jelly,
      /\.octane-management-metric-tile\[data-management-metric-key="tester-volume"\][\s\S]*?\.octane-management-metric-items span,[\s\S]*?data-management-metric-key="tester-defects"[\s\S]*?\.octane-management-metric-items span\s*\{[^}]*flex: 1 1 auto;[^}]*min-width: 0;/s);
  assert.match(
      jelly,
      /\.octane-management-metric-items span\s*\{[^}]*overflow: hidden;[^}]*text-overflow: ellipsis;[^}]*text-wrap: nowrap;[^}]*white-space: nowrap;/s);
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
    ["--octane-system-red", "#FF3B30", "#FF453A"],
    ["--octane-system-yellow", "#FFCC00", "#FFD60A"],
    ["--octane-system-orange", "#FF9500", "#FF9F0A"],
    ["--octane-severity-low", "#5AC8FA", "#64D2FF"],
    ["--octane-system-purple", "#AF52DE", "#BF5AF2"],
    ["--octane-system-gray", "#8E8E93", "#8E8E93"]
  ].forEach(([property, light, dark]) => {
    assert.match(jelly, new RegExp(`${property}: ${light}`));
    if (light !== dark) {
      assert.match(jelly, new RegExp(`${property}: ${dark}`));
    }
  });
  assert.match(jelly, /--octane-severity-critical: var\(--octane-system-red\)/);
  assert.match(jelly, /--octane-severity-very-high: var\(--octane-system-yellow\)/);
  assert.match(jelly, /--octane-severity-high: var\(--octane-system-orange\)/);
  assert.match(jelly, /--octane-severity-medium: var\(--octane-system-purple\)/);
  assert.match(jelly, /--octane-severity-unspecified: var\(--octane-system-gray\)/);
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

test("coalesces repeated polling updates into one connected-frame render", () => {
  assert.match(source, /function scheduleRender\(zone\)/);
  assert.match(source, /zone\.__octaneTestManagementRenderFrame != null/);
  assert.match(source, /global\.requestAnimationFrame\.bind\(global\)/);
  assert.match(source, /return global\.setTimeout\(callback, 16\)/);
  assert.match(source, /zone\.isConnected !== false/);
  assert.match(
      source,
      /function update\(zone, payload\)[\s\S]*?zone\.__octaneTestManagementPayload = payload \|\| \{\};[\s\S]*?scheduleRender\(zone\);/);
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
