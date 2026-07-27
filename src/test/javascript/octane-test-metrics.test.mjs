import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

const jellyPath =
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
    + "OctaneGateReportAction/index.jelly";
const jelly = readFileSync(jellyPath, "utf8");
const labelSource = jelly
    .split("/* OCTANE_TEST_METRIC_LABELS_START */")[1]
    .split("/* OCTANE_TEST_METRIC_LABELS_END */")[0];

function metricSegment({availableWidth, fullLabel, fullWidth, shortLabel, shortWidth}) {
  let text = "";
  const label = {
    title: "",
    get scrollWidth() {
      return text === fullLabel ? fullWidth : shortWidth;
    },
    get textContent() {
      return text;
    },
    set textContent(value) {
      text = value;
    }
  };
  return {
    getAttribute(name) {
      return name === "data-full-label" ? fullLabel : shortLabel;
    },
    getBoundingClientRect() {
      return {width: availableWidth};
    },
    label,
    querySelector() {
      return label;
    }
  };
}

function contextFor(segments) {
  const root = {
    querySelectorAll(selector) {
      if (selector === "[data-test-metric-segment]") {
        return segments;
      }
      return [];
    }
  };
  const context = {
    dashboard: root,
    requestFrame: callback => callback(),
    window: {addEventListener() {}}
  };
  vm.runInNewContext(
      `${labelSource}
this.fitTestMetricSegmentLabels = fitTestMetricSegmentLabels;`,
      context);
  return {context, root};
}

test("keeps full defect labels when the proportional segment is wide enough", () => {
  const segment = metricSegment({
    availableWidth: 110,
    fullLabel: "Major (12)",
    fullWidth: 82,
    shortLabel: "M (12)",
    shortWidth: 44
  });
  const {context, root} = contextFor([segment]);

  context.fitTestMetricSegmentLabels(root);

  assert.equal(segment.label.textContent, "Major (12)");
  assert.equal(segment.label.title, "Major (12)");
});

test("uses the compact defect label when the rendered segment is narrow", () => {
  const segment = metricSegment({
    availableWidth: 42,
    fullLabel: "Unspecified (5)",
    fullWidth: 116,
    shortLabel: "U (5)",
    shortWidth: 34
  });
  const {context, root} = contextFor([segment]);

  context.fitTestMetricSegmentLabels(root);

  assert.equal(segment.label.textContent, "U (5)");
  assert.equal(segment.label.title, "Unspecified (5)");
});

test("retains responsive and polling hooks for refreshed metric markup", () => {
  assert.match(jelly, /new window\.ResizeObserver/);
  assert.match(jelly, /initializeTestMetricSegments\(panel\)/);
  assert.match(jelly, /initializeTestMetricSegments\(dashboard\)/);
  assert.match(jelly, /grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(jelly, /grid-template-rows: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(jelly, /\.octane-test-metric-card \{[\s\S]*?overflow: hidden/);
});
