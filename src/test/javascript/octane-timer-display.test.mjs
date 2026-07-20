import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

const jellyPath =
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
    + "OctaneGateReportAction/index.jelly";
const jelly = readFileSync(jellyPath, "utf8");
const timerDisplaySource = jelly
    .split("/* OCTANE_TIMER_DISPLAY_START */")[1]
    .split("/* OCTANE_TIMER_DISPLAY_END */")[0];

function cssRule(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = jelly.match(new RegExp(`(?:^|\\n)\\s*${escapedSelector}\\s*\\{([^}]*)\\}`));
  assert.ok(match, `Missing CSS rule for ${selector}`);
  return match[1];
}

const context = {
  clamp: (value, minimum, maximum) => Math.min(maximum, Math.max(minimum, value))
};
vm.runInNewContext(
    `${timerDisplaySource}
this.timeoutCountdownRemainingMillis = timeoutCountdownRemainingMillis;
this.timerDisplayParts = timerDisplayParts;`,
    context);

function displayParts(milliseconds) {
  return {...context.timerDisplayParts(milliseconds)};
}

test("formats the task examples with the largest unit and its immediate sub-unit", () => {
  assert.deepEqual(
      displayParts(119 * 60 * 1000),
      {
        accessibleLabel: "1 hour and 59 minutes",
        compactLabel: "h + m",
        fullLabel: "hours + minutes",
        value: "1:59"
      });
  assert.deepEqual(
      displayParts(1500 * 60 * 1000),
      {
        accessibleLabel: "1 day and 1 hour",
        compactLabel: "d + h",
        fullLabel: "days + hours",
        value: "1:01"
      });
});

test("switches units exactly at hour, day, and week boundaries", () => {
  assert.equal(displayParts(8 * 24 * 60 * 60 * 1000).value, "1:01");
  assert.equal(displayParts(8 * 24 * 60 * 60 * 1000).fullLabel, "weeks + days");
  assert.equal(displayParts(7 * 24 * 60 * 60 * 1000).value, "1:00");
  assert.equal(displayParts(24 * 60 * 60 * 1000).value, "1:00");
  assert.equal(displayParts(24 * 60 * 60 * 1000).fullLabel, "days + hours");
  assert.equal(displayParts(60 * 60 * 1000).value, "1:00");
  assert.equal(displayParts(60 * 60 * 1000).fullLabel, "hours + minutes");
  assert.equal(displayParts(3599 * 1000).value, "59:59");
  assert.equal(displayParts(3599 * 1000).fullLabel, "minutes + seconds");
});

test("rounds a partial final second up and locks depleted time at zero", () => {
  assert.equal(displayParts(1).value, "0:01");
  assert.equal(displayParts(0).value, "0:00");
  assert.equal(displayParts(-1000).value, "0:00");
  assert.equal(displayParts(0).fullLabel, "minutes + seconds");
  assert.equal(displayParts(0).compactLabel, "m + s");
});

test("counts down one continuous base plus extended timeout window", () => {
  const minute = 60 * 1000;
  const remaining = elapsedMinutes => context.timeoutCountdownRemainingMillis(
      0,
      120 * minute,
      30 * minute,
      elapsedMinutes * minute);

  assert.equal(remaining(0), 150 * minute);
  assert.equal(remaining(120), 30 * minute);
  assert.equal(remaining(130), 20 * minute);
  assert.equal(remaining(150), 0);
  assert.equal(remaining(151), 0);
  assert.equal(
      context.timeoutCountdownRemainingMillis(0, 120 * minute, 0, 120 * minute),
      0);
});

test("renders full and compact labels with component and mobile breakpoints", () => {
  const timerWrapRule = cssRule(".octane-timer-wrap");
  const timerDonutRule = cssRule(".octane-timer-donut");

  assert.match(jelly, /data-timer-unit-compact="true"/);
  assert.match(timerWrapRule, /container-name:\s*octane-timer-display/);
  assert.match(timerWrapRule, /flex:\s*1 1 auto/);
  assert.match(timerWrapRule, /height:\s*100%/);
  assert.match(timerWrapRule, /min-width:\s*0/);
  assert.match(timerWrapRule, /width:\s*100%/);
  assert.match(timerDonutRule, /aspect-ratio:\s*1 \/ 1/);
  assert.match(timerDonutRule, /height:\s*min\(100%,\s*220px\)/);
  assert.match(timerDonutRule, /max-height:\s*220px/);
  assert.match(timerDonutRule, /max-width:\s*220px/);
  assert.match(
      cssRule(".octane-zone-focused .octane-timer-donut"),
      /height:\s*min\(100%,\s*38vh,\s*38vw\)/);
  assert.match(
      cssRule(".octane-chart-card.octane-expanded .octane-timer-donut"),
      /height:\s*min\(100%,\s*76vh,\s*76vw\)/);
  assert.match(jelly, /@container octane-timer-display \(max-width: 18rem\)/);
  assert.match(jelly, /@media \(max-width: 480px\)/);
  assert.match(jelly, /\.octane-timer-unit-compact\s*{\s*display:\s*none;/);
});
