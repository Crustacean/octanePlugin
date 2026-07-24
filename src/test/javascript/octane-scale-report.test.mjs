import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {readFileSync} from "node:fs";
import test from "node:test";

const require = createRequire(import.meta.url);
const renderer = require("../../main/webapp/js/octane-scale-report.js");
const source = readFileSync("src/main/webapp/js/octane-scale-report.js", "utf8");

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

test("preserves SVG text proportions across responsive layouts", () => {
  assert.match(source, /preserveAspectRatio", "xMidYMid meet"/);
  assert.doesNotMatch(source, /preserveAspectRatio", "none"/);
});

test("moves thin donut labels to collision-free callouts", () => {
  const slices = renderer.computeDonutLabelLayout(
      [
        {count: 90, label: "Passed", percentageLabel: "90.00%"},
        {count: 4, label: "Failed", percentageLabel: "4.00%"},
        {count: 3, label: "Blocked", percentageLabel: "3.00%"},
        {count: 2, label: "Skipped", percentageLabel: "2.00%"},
        {count: 1, label: "Running", percentageLabel: "1.00%"}
      ],
      100);

  assert.equal(slices.length, 5);
  assert.equal(slices[0].callout, false);
  assert.equal(slices[0].textAnchor, "middle");
  for (const slice of slices.slice(1)) {
    assert.equal(slice.callout, true);
    assert.ok(Math.abs(slice.labelX - 50) > 46);
    assert.equal(slice.leaderEndX, slice.labelX);
    assert.equal(slice.leaderEndY, slice.labelY);
    assert.ok(
        Math.abs(Math.hypot(slice.leaderStartX - 50, slice.leaderStartY - 50) - 38)
          < 0.001);
  }
  for (const anchor of ["start", "end"]) {
    const positions = slices
        .filter(slice => slice.callout && slice.textAnchor === anchor)
        .map(slice => slice.labelY)
        .sort((left, right) => left - right);
    for (let index = 1; index < positions.length; index += 1) {
      assert.ok(positions[index] - positions[index - 1] >= 8);
    }
  }
});

test("renders leader lines only for offset donut labels", () => {
  assert.match(source, /createSvgElement\("line", "octane-donut-callout-line"\)/);
  assert.match(source, /data-label-mode", slice\.callout \? "callout" : "radial"/);
  assert.match(source, /if \(!slice\.callout\) \{\s*return;\s*\}/);
});

test("applies the shared gap class only to segmented donut wedges", () => {
  assert.match(source, /slice\.fullCircle \? "" : "octane-donut-segment"/);
});
