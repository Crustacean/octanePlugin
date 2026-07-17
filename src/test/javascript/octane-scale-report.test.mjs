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
