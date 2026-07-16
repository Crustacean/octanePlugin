import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

const jellyPath =
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
    + "OctaneGateReportAction/index.jelly";
const jelly = readFileSync(jellyPath, "utf8");
const fluidMathSource = jelly
    .split("/* OCTANE_FLUID_BAR_CHART_START */")[1]
    .split("/* OCTANE_FLUID_BAR_CHART_END */")[0];
const context = {isFinite};
vm.runInNewContext(
    `${fluidMathSource}\nthis.maxVisibleBarsForWidth = maxVisibleBarsForWidth;`, context);

test("uses the shared bar capacity formula", () => {
  assert.equal(context.maxVisibleBarsForWidth(600), 57);
  assert.equal(context.maxVisibleBarsForWidth(800), 77);
  assert.equal(context.maxVisibleBarsForWidth(1200), 117);
});

test("always leaves room for at least one bar", () => {
  assert.equal(context.maxVisibleBarsForWidth(0), 1);
  assert.equal(context.maxVisibleBarsForWidth(24), 1);
  assert.equal(context.maxVisibleBarsForWidth(Number.NaN), 1);
});

test("renders a concise overflow count at the x-axis trail", () => {
  assert.match(jelly, /count\.textContent = "\+" \+ hiddenCount/);
  assert.doesNotMatch(jelly, /hiddenCount \+ " more\.\.\."/);
  assert.match(jelly, /\.octane-fluid-bars-dense \.octane-suite-column \{[\s\S]*?margin-right: 2px/);
});
