import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {readFileSync} from "node:fs";
import test from "node:test";

const require = createRequire(import.meta.url);
const renderer = require("../../main/webapp/js/octane-scale-report.js");
const source = readFileSync("src/main/webapp/js/octane-scale-report.js", "utf8");
const jelly = readFileSync(
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
        + "OctaneGateReportAction/index.jelly",
    "utf8");
const emailRenderer = readFileSync(
    "src/main/java/io/jenkins/plugins/octanesuitegatebyembiti/services/"
        + "OctaneReportZoneHtmlRenderer.java",
    "utf8");

test("caps a dense chart at eighty visible bars", () => {
  assert.equal(renderer.computeVisibleBarCount(2000, 500), 80);
  assert.equal(renderer.computeVisibleBarCount(500, 500), 47);
  assert.equal(renderer.computeVisibleBarCount(0, 500), 1);
});

test("reserves exactly twenty-four pixels for the concise overflow marker", () => {
  assert.equal(renderer.OVERFLOW_WIDTH_PX, 24);
  assert.match(source, /"\+" \+ hiddenCount/);
  assert.doesNotMatch(source, /hiddenCount \+ " more"/);
});

test("uses delegated safe DOM rendering without per-bar tooltip trees", () => {
  assert.doesNotMatch(source, /\.innerHTML\s*=/);
  assert.match(source, /textContent = String\(value\)/);
  assert.match(source, /IntersectionObserver/);
  assert.match(source, /ResizeObserver/);
  assert.match(source, /octane-client-bar-hit-target/);
  assert.match(source, /data-page-direction/);
  assert.match(source, /nextCursor/);
  assert.match(source, /disposeState/);
  assert.doesNotMatch(source, /className = "octane-bar-popup"/);
});

test("cancels stale data requests and replays the latest resize demand", () => {
  assert.match(source, /typeof AbortController === "function"/);
  assert.match(source, /function abortRequest\(controller\)/);
  assert.match(source, /abortRequest\(requestController\)/);
  assert.match(source, /abortRequest\(state\.controller\)/);
  assert.match(source, /options\.signal = signal/);
  assert.match(source, /function isAbortError\(error\)/);
  assert.match(source, /pendingCursor = safeCursor/);
  assert.match(source, /function loadPending\(\)/);
  assert.match(source, /requestGeneration\+\+/);
});

test("preserves SVG text proportions across responsive layouts", () => {
  assert.match(source, /preserveAspectRatio", "xMidYMid meet"/);
  assert.doesNotMatch(source, /preserveAspectRatio", "none"/);
});

test("builds bounded donut slices without external label geometry", () => {
  const slices = renderer.computeDonutSlices(
      [
        {count: 90, label: "Passed", percentageLabel: "90.00%"},
        {count: 4, label: "Failed", percentageLabel: "4.00%"},
        {count: 3, label: "Blocked", percentageLabel: "3.00%"},
        {count: 2, label: "Skipped", percentageLabel: "2.00%"},
        {count: 1, label: "Running", percentageLabel: "1.00%"}
      ],
      100);

  assert.equal(slices.length, 5);
  for (const slice of slices) {
    assert.equal("callout" in slice, false);
    assert.equal("labelX" in slice, false);
    assert.match(slice.path, /46\.000/);
  }
});

test("renders a centered total and rigid percentage legend without callouts", () => {
  assert.match(source, /"Total test cases: " \+ total/);
  assert.match(source, /"octane-chart-inner octane-donut-graph"/);
  assert.match(source, /viewBox", "3 3 94 94"/);
  assert.match(source, /"octane-donut-center-value"/);
  assert.match(source, /"octane-donut-center-label", "Total test cases"/);
  assert.match(source, /"table", "octane-donut-legend"/);
  assert.match(source, /"octane-donut-legend-percentage"/);
  assert.doesNotMatch(source, /octane-donut-callout-line/);
  assert.doesNotMatch(source, /data-label-mode/);
});

test("uses the enlarged, thicker donut consistently in live and email reports", () => {
  const previousHoleRadius = 40.6;
  const outerRadius = 46;
  const previousDiameter = 206.817;
  const currentDiameter = 248.1804;

  assert.equal(renderer.DONUT_HOLE_RADIUS, 37.36);
  assert.equal(
      Number(((outerRadius - renderer.DONUT_HOLE_RADIUS)
          / (outerRadius - previousHoleRadius)).toFixed(10)),
      1.6);
  assert.equal(Number((currentDiameter / previousDiameter).toFixed(10)), 1.2);
  assert.match(source, /DONUT_HOLE_RADIUS = 37\.36/);
  assert.match(source, /hole\.setAttribute\("r", String\(DONUT_HOLE_RADIUS\)\)/);
  assert.match(jelly, /octane-donut-hole" cx="50" cy="50" r="37\.36"/);
  assert.match(jelly, /max-height: 248\.1804px/);
  assert.match(jelly, /max-width: 248\.1804px/);
  assert.match(emailRenderer, /r=\\?"37\.36\\?"/);
  assert.match(emailRenderer, /max-height: 248\.1804px/);
  assert.match(emailRenderer, /max-width: 248\.1804px/);
});

test("identifies segmented donut wedges without rendering separator geometry", () => {
  assert.match(source, /slice\.fullCircle \? "" : "octane-donut-segment"/);
  assert.doesNotMatch(source, /stroke-width/);
});
