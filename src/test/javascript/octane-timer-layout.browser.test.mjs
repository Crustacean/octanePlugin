import assert from "node:assert/strict";
import {spawn, spawnSync} from "node:child_process";
import {existsSync, readFileSync} from "node:fs";
import {createServer} from "node:net";
import test from "node:test";

const jellyPath =
    "src/main/resources/io/jenkins/plugins/octanesuitegatebyembiti/actions/"
    + "OctaneGateReportAction/index.jelly";
const jelly = readFileSync(jellyPath, "utf8");
const styleMatch = jelly.match(/<style>([\s\S]*?)<\/style>/);
assert.ok(styleMatch, "The report page must contain its dashboard stylesheet");

const viewports = [
  {height: 640, name: "compact", width: 360},
  {height: 900, name: "tablet", width: 768},
  {height: 900, name: "desktop", width: 1440},
  {height: 1440, name: "wide", width: 2560}
];
const snapGeckodriver = "/snap/firefox/current/usr/lib/firefox/geckodriver";
const geckodriverExecutable = existsSync(snapGeckodriver)
    ? snapGeckodriver
    : "geckodriver";

function executableAvailable(name) {
  return spawnSync("sh", ["-c", `command -v ${name}`], {stdio: "ignore"}).status === 0;
}

function delay(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function availablePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close(error => error ? reject(error) : resolve(address.port));
    });
  });
}

async function webdriverRequest(baseUrl, method, path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: {"Content-Type": "application/json"},
    method,
    signal: AbortSignal.timeout(10000)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || (payload.value && payload.value.error)) {
    throw new Error(
        `WebDriver ${method} ${path} failed: ${JSON.stringify(payload.value || payload)}`);
  }
  return payload.value;
}

async function waitForWebdriver(baseUrl, driver) {
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) {
    if (driver.exitCode !== null) {
      throw new Error(`geckodriver exited before startup with code ${driver.exitCode}`);
    }
    try {
      const response = await fetch(`${baseUrl}/status`, {signal: AbortSignal.timeout(1000)});
      if (response.ok) {
        return;
      }
    } catch (error) {
      // The driver has not opened its socket yet.
    }
    await delay(100);
  }
  throw new Error("Timed out waiting for geckodriver");
}

async function withFirefox(callback) {
  const port = await availablePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const driver = spawn(
      geckodriverExecutable,
      ["--host", "127.0.0.1", "--port", String(port), "--log", "fatal"],
      {detached: true, stdio: "ignore"});
  let sessionId = "";
  try {
    await waitForWebdriver(baseUrl, driver);
    const session = await webdriverRequest(baseUrl, "POST", "/session", {
      capabilities: {
        alwaysMatch: {
          browserName: "firefox",
          "moz:firefoxOptions": {args: ["-headless"]}
        }
      }
    });
    sessionId = session.sessionId;
    await webdriverRequest(
        baseUrl, "POST", `/session/${sessionId}/timeouts`, {script: 5000});
    await callback({baseUrl, sessionId});
  } finally {
    if (sessionId) {
      await Promise.race([
        webdriverRequest(baseUrl, "DELETE", `/session/${sessionId}`).catch(() => {}),
        delay(3000)
      ]);
    }
    try {
      driver.kill("SIGKILL");
    } catch (error) {
      // The driver already exited after deleting the session.
    }
    driver.unref();
  }
}

function timerCard(index, title) {
  return `
    <section class="octane-chart-card octane-timer-card" data-active-view="timer"
        data-card-key="timer-${index}">
      <div class="octane-flip-viewport">
        <div class="octane-flip-face octane-flip-face-timer">
          <div class="octane-flip-face-header">
            <div>
              <h2 class="octane-card-title">${title}</h2>
              <div class="octane-muted">Responsive graph</div>
            </div>
          </div>
          <div class="octane-flip-face-body">
            <div class="octane-timer-wrap">
              <svg class="octane-timer-donut" viewBox="0 0 240 240" role="img"
                  aria-label="${title}">
                <circle class="octane-timer-track" cx="120" cy="120" r="92"></circle>
                <circle class="octane-timer-progress" cx="120" cy="120" r="92"
                    pathLength="100" stroke="#34C759" stroke-dasharray="72 100"></circle>
                <text class="octane-timer-value" x="120" y="118">72</text>
                <text class="octane-timer-unit" x="120" y="139">percent</text>
              </svg>
            </div>
          </div>
        </div>
      </div>
    </section>`;
}

function managementCard(index, title) {
  const categoryNavigation = index === 2
      ? `<div class="octane-card-actions">
          <div class="octane-management-failure-tab-nav"
              data-management-failure-tab-nav="true"
              role="group" aria-label="Defect group filters">
            <button class="octane-management-category-scroll" type="button"
                data-management-category-scroll="-1" data-visible="true"
                aria-label="Scroll defect groups left">
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M12.5 4.5L7 10l5.5 5.5"></path>
              </svg>
            </button>
            <div class="octane-management-failure-switcher"
                data-management-failure-switcher="true">
              ${[
                "All",
                "Environment Configuration",
                "Automation Framework",
                "Data Quality",
                "API Contract",
                "Infrastructure",
                "Product Defect",
                "Third Party"
              ].map((category, categoryIndex) => `
                <button class="octane-management-category-toggle" type="button"
                    aria-pressed="${categoryIndex === 0}">
                  ${category}
                </button>`).join("")}
            </div>
            <button class="octane-management-category-scroll" type="button"
                data-management-category-scroll="1" data-visible="true"
                aria-label="Scroll defect groups right">
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M7.5 4.5L13 10l-5.5 5.5"></path>
              </svg>
            </button>
          </div>
        </div>`
      : "";
  const chart = index === 2
      ? `<div class="octane-management-failure-layout">
          <div class="octane-management-plot-layout
              octane-management-failure-axis-layout">
            <div class="octane-management-y-axis-title">Defects</div>
            <div class="octane-management-y-labels">
              <span>10</span><span>5</span><span>0</span>
            </div>
            <div class="octane-management-failure-chart">
              ${Array.from({length: 8}, (_, category) => `
                <button class="octane-management-failure-group" type="button">
                  <span class="octane-management-failure-bars">
                    <span class="octane-management-failure-bar"
                        style="--octane-bar-color: #FF453A;
                            --octane-bar-size: ${30 + category * 5}%"></span>
                    <span class="octane-management-failure-bar"
                        style="--octane-bar-color: #34C759;
                            --octane-bar-size: ${20 + category * 4}%"></span>
                  </span>
                  <span class="octane-management-failure-label">
                    Long dynamic failure category ${category + 1}
                  </span>
                </button>`).join("")}
            </div>
          </div>
          <div class="octane-management-defect-detail-panel">
            <div class="octane-management-defect-list" role="table">
              <div class="octane-management-defect-header" role="row">
                ${["Defect ID", "Defect Description", "Status", "Severity"]
                  .map((label, column) => `
                    <div class="octane-management-defect-header-cell" role="columnheader"
                        ${column === 0 ? 'aria-sort="ascending"' : ""}>
                      <button class="octane-management-defect-sort" type="button">
                        <span class="octane-management-defect-sort-label">${label}</span>
                        <span class="octane-management-defect-sort-indicator"
                            aria-hidden="true"></span>
                      </button>
                    </div>`).join("")}
              </div>
              ${["Critical", "Very High", "High", "Medium", "Low", "Unspecified"]
                .map((severity, defect) => `
                  <div class="octane-management-defect-row" role="row">
                    <span class="octane-management-defect-id">#D-${defect + 1000}</span>
                    <span class="octane-management-defect-description">
                      Checkout payment authorization failed for a long regional account name
                    </span>
                    <span class="octane-management-defect-pill"
                        style="--octane-pill-color: #FF453A">Open</span>
                    <span class="octane-management-defect-pill"
                        style="--octane-pill-color: #9D1D34">${severity}</span>
                  </div>`).join("")}
            </div>
          </div>
        </div>`
      : index === 3
        ? `<div class="octane-management-metrics-grid">
            <article class="octane-management-metric-tile octane-management-tone-bad">
              <h3 class="octane-management-metric-title">Defect Compliance</h3>
              <div class="octane-management-metric-value">12 open</div>
              <div class="octane-management-metric-detail">2 / 7 defect criteria met</div>
            </article>
            <article class="octane-management-metric-tile octane-management-tone-bad">
              <h3 class="octane-management-metric-title">Open vs Closed</h3>
              <div class="octane-management-metric-value">12 open</div>
              <div class="octane-management-metric-detail">18 closed</div>
            </article>
            <article class="octane-management-metric-tile octane-management-tone-neutral"
                data-management-metric-key="tester-volume">
              <h3 class="octane-management-metric-title">Top Testers by Volume</h3>
              <div class="octane-management-metric-value">5 testers</div>
              <div class="octane-management-metric-detail">3 below 80% execution</div>
              <ul class="octane-management-metric-items">
                <li><span>tester.alpha.with.an.extremely.long.enterprise.identity.and.region@example.com</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">57 tests</span><span class="octane-management-metric-item-separator">|</span><span class="octane-management-metric-item-secondary">100%</span></strong></li>
                <li><span>tester.beta.with.an.extremely.long.enterprise.identity.and.region@example.com</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">46 tests</span><span class="octane-management-metric-item-separator">|</span><span class="octane-management-metric-item-secondary">80%</span></strong></li>
                <li><span>Tester Gamma</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">45 tests</span><span class="octane-management-metric-item-separator">|</span><span class="octane-management-metric-item-secondary">70%</span></strong></li>
                <li><span>Tester Delta</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">44 tests</span><span class="octane-management-metric-item-separator">|</span><span class="octane-management-metric-item-secondary">60%</span></strong></li>
                <li><span>Tester Epsilon</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">40 tests</span><span class="octane-management-metric-item-separator">|</span><span class="octane-management-metric-item-secondary">50%</span></strong></li>
              </ul>
            </article>
            <article class="octane-management-metric-tile octane-management-tone-bad"
                data-management-metric-key="tester-defects">
              <h3 class="octane-management-metric-title">Top Testers by Open Defects</h3>
              <div class="octane-management-metric-value">12 open</div>
              <div class="octane-management-metric-detail">Highest open workload</div>
              <ul class="octane-management-metric-items">
                <li><span>tester.alpha.with.an.extremely.long.enterprise.identity.and.region@example.com</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">105 open</span></strong></li>
                <li><span>tester.beta.with.an.extremely.long.enterprise.identity.and.region@example.com</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">23 open</span></strong></li>
                <li><span>Tester Gamma</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">8 open</span></strong></li>
                <li><span>Tester Delta</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">2 open</span></strong></li>
                <li><span>Tester Epsilon</span><strong class="octane-management-metric-item-value"><span class="octane-management-metric-item-primary">1 open</span></strong></li>
              </ul>
            </article>
          </div>`
        : `<div class="octane-management-chart">
          <div class="octane-management-plot-layout">
            <div class="octane-management-y-axis-title">Tests Executed</div>
            <div class="octane-management-y-labels">
              <span>10</span><span>5</span><span>0</span>
            </div>
            <div class="octane-management-state-bars">
              ${Array.from({length: 10}, (_, part) => `
                <div class="octane-management-state-column">
                  <span class="octane-management-state-segment"
                      style="--octane-segment-color: #34C759;
                          --octane-segment-size: ${20 + part * 6}%"></span>
                </div>`).join("")}
            </div>
            <div class="octane-management-x-labels">
              <span>Start</span><span>Middle</span><span>End</span>
            </div>
          </div>
        </div>`;
  return `
    <section class="octane-chart-card octane-test-management-card"
        data-card-key="test-management-${index}">
      <div class="octane-card-header">
        <div class="octane-management-header-copy">
          <h2 class="octane-card-title">${title}</h2>
          <div class="octane-management-subtitle-line">
            <div class="octane-management-legend">
              <span class="octane-management-legend-item">
                <span class="octane-management-legend-swatch"
                    style="--octane-legend-color: #34C759"></span>
                <span>Responsive series</span>
              </span>
            </div>
          </div>
        </div>
        ${categoryNavigation}
      </div>
      <div class="octane-management-card-body">
        ${chart}
      </div>
    </section>`;
}

function fixtureHtml() {
  const cards = [
    "Testing Session Monitor",
    "Status Check",
    "Execution Progress",
    "Execution Pass Rate"
  ].map((title, index) => timerCard(index, title)).join("");
  const managementCards = [
    "Testing Against Schedule",
    "Execution per Sprint Parts",
    "Test Failure Analysis",
    "Testing Metrics"
  ].map((title, index) => managementCard(index, title)).join("");
  return `<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root {
            --background: #101218;
            --input-border: #343944;
            --panel-border-color: #343944;
            --text-color: #f3f6fb;
            --text-color-secondary: #9ea9bf;
          }
          html, body { margin: 0; min-height: 100%; }
          body { background: #101218; box-sizing: border-box; padding: 0.5rem; }
          ${styleMatch[1]}
          .octane-dashboard { margin-top: 0; }
        </style>
      </head>
      <body>
        <main class="octane-dashboard" id="octane-dashboard">
          <div class="octane-timer-zone octane-card-zone" id="octane-timer-zone">
            ${cards}
          </div>
          <div class="octane-test-management-zone octane-card-zone"
              id="octane-test-management-zone">
            ${managementCards}
          </div>
        </main>
      </body>
    </html>`;
}

async function execute(driver, script, args = []) {
  return webdriverRequest(
      driver.baseUrl,
      "POST",
      `/session/${driver.sessionId}/execute/sync`,
      {args, script});
}

async function executeAfterPaint(driver, script, args = []) {
  return webdriverRequest(
      driver.baseUrl,
      "POST",
      `/session/${driver.sessionId}/execute/async`,
      {
        args,
        script: `
          var suppliedArguments = Array.prototype.slice.call(arguments);
          var done = suppliedArguments.pop();
          var testArguments = suppliedArguments;
          var started = performance.now();
          requestAnimationFrame(function () {
            requestAnimationFrame(function () {
              try {
                done({
                  duration: performance.now() - started,
                  value: (function () { ${script} }).apply(null, testArguments)
                });
              } catch (error) {
                done({error: String(error && error.stack || error)});
              }
            });
          });`
      });
}

async function setViewport(driver, viewport) {
  await webdriverRequest(
      driver.baseUrl,
      "POST",
      `/session/${driver.sessionId}/window/rect`,
      {height: viewport.height, width: viewport.width, x: 0, y: 0});
}

async function setMode(driver, mode, expandedIndex = 0) {
  await execute(driver, `
    var zone = document.getElementById("octane-timer-zone");
    var cards = Array.prototype.slice.call(zone.querySelectorAll(".octane-chart-card"));
    var mode = arguments[0];
    var expandedIndex = arguments[1];
    zone.classList.toggle("octane-zone-focused", mode === "focused");
    cards.forEach(function (card, index) {
      card.classList.toggle(
          "octane-expanded", mode === "expanded" && index === expandedIndex);
    });
    return true;`, [mode, expandedIndex]);
}

async function graphMetrics(driver) {
  const result = await executeAfterPaint(driver, `
    return Array.prototype.map.call(
        document.querySelectorAll("#octane-timer-zone .octane-chart-card"),
        function (card) {
          var body = card.querySelector(".octane-flip-face-body");
          var graph = card.querySelector(".octane-timer-donut");
          var cardRect = card.getBoundingClientRect();
          var bodyRect = body.getBoundingClientRect();
          var graphRect = graph.getBoundingClientRect();
          var style = getComputedStyle(graph);
          return {
            bodyHeight: bodyRect.height,
            bodyWidth: bodyRect.width,
            cardHeight: cardRect.height,
            cardWidth: cardRect.width,
            className: card.className,
            display: style.display,
            graphHeight: graphRect.height,
            graphWidth: graphRect.width,
            maxHeight: style.maxHeight,
            maxWidth: style.maxWidth,
            opacity: Number(style.opacity),
            resolvedHeight: style.height,
            resolvedWidth: style.width,
            withinBody:
                graphRect.left >= bodyRect.left - 1
                && graphRect.right <= bodyRect.right + 1
                && graphRect.top >= bodyRect.top - 1
                && graphRect.bottom <= bodyRect.bottom + 1,
            visibility: style.visibility
          };
        });`);
  if (result.error) {
    throw new Error(result.error);
  }
  assert.ok(result.duration < 500, `Graph layout took ${result.duration.toFixed(1)}ms`);
  return result.value;
}

async function normalZoneMetrics(driver) {
  const result = await executeAfterPaint(driver, `
    function metricsFor(zoneId) {
      var zone = document.getElementById(zoneId);
      var zoneRect = zone.getBoundingClientRect();
      return {
        cardHeights: Array.prototype.map.call(
            zone.querySelectorAll(":scope > .octane-chart-card"),
            function (card) {
              return card.getBoundingClientRect().height;
            }),
        height: zoneRect.height
      };
    }
    return {
      management: metricsFor("octane-test-management-zone"),
      timer: metricsFor("octane-timer-zone")
    };`);
  if (result.error) {
    throw new Error(result.error);
  }
  assert.ok(result.duration < 500, `Zone layout took ${result.duration.toFixed(1)}ms`);
  return result.value;
}

async function constrainedManagementBarMetrics(driver) {
  const result = await executeAfterPaint(driver, `
    var state = document.querySelector(".octane-management-state-bars");
    var failure = document.querySelector(".octane-management-failure-chart");
    state.style.width = "72px";
    state.style.maxWidth = "72px";
    state.style.justifySelf = "start";
    failure.style.width = "72px";
    failure.style.maxWidth = "72px";
    failure.style.justifySelf = "start";
    var label = failure.querySelector(".octane-management-failure-label");
    var labelStyle = getComputedStyle(label);
    var failureRect = failure.getBoundingClientRect();
    var failureGroup = failure.querySelector(".octane-management-failure-group");
    var failureGroupRect = failureGroup.getBoundingClientRect();
    var failureGroupStyle = getComputedStyle(failureGroup);
    var failureGridRows = failureGroupStyle.gridTemplateRows.trim().split(" ");
    var failureAxisRow = parseFloat(failureGridRows[failureGridRows.length - 1]);
    var failureBarsRect = failureGroup
        .querySelector(".octane-management-failure-bars")
        .getBoundingClientRect();
    var failureLabelRect = label.getBoundingClientRect();
    return {
      failureAxisY: failureRect.bottom - failureAxisRow,
      failureAxisRow: failureAxisRow,
      failureBarBottoms: Array.prototype.map.call(
          failure.querySelectorAll(".octane-management-failure-bar"),
          function (bar) { return bar.getBoundingClientRect().bottom; }),
      failureBarWidths: Array.prototype.map.call(
          failure.querySelectorAll(".octane-management-failure-bar"),
          function (bar) { return bar.getBoundingClientRect().width; }),
      failureClientWidth: failure.clientWidth,
      failureGridRows: failureGroupStyle.gridTemplateRows,
      failureGroupBottom: failureGroupRect.bottom,
      failureGroupHeight: failureGroupRect.height,
      failureLabelBottom: failureLabelRect.bottom,
      failureLabelHeight: failureLabelRect.height,
      failurePlotBottom: failureBarsRect.bottom,
      failureScrollWidth: failure.scrollWidth,
      labelClientWidth: label.clientWidth,
      labelOverflow: labelStyle.overflow,
      labelScrollWidth: label.scrollWidth,
      labelTextOverflow: labelStyle.textOverflow,
      labelWhiteSpace: labelStyle.whiteSpace,
      stateBarWidths: Array.prototype.map.call(
          state.querySelectorAll(".octane-management-state-column"),
          function (bar) { return bar.getBoundingClientRect().width; }),
      stateClientWidth: state.clientWidth,
      stateScrollWidth: state.scrollWidth
    };`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

async function managementAxisSpacingMetrics(driver) {
  const result = await executeAfterPaint(driver, `
    var rootFontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
    var legacyPlotOffset = (1.15 + 1.9 + (0.04 * 2)) * rootFontSize;
    return Array.prototype.map.call(
        document.querySelectorAll(
            "#octane-test-management-zone .octane-management-plot-layout"),
        function (layout) {
          var title = layout.querySelector(".octane-management-y-axis-title");
          var labels = layout.querySelector(".octane-management-y-labels");
          var plot = layout.querySelector(
              ".octane-management-svg-wrap, .octane-management-state-bars, "
              + ".octane-management-failure-chart");
          var layoutRect = layout.getBoundingClientRect();
          var plotRect = plot.getBoundingClientRect();
          var style = getComputedStyle(layout);
          return {
            columnGap: parseFloat(style.columnGap),
            gridColumns: style.gridTemplateColumns,
            labelWidth: labels.getBoundingClientRect().width,
            plotGain: legacyPlotOffset - (plotRect.left - layoutRect.left),
            plotWidth: plotRect.width,
            titleWidth: title.getBoundingClientRect().width
          };
        });`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

async function compactManagementMetricLayout(driver) {
  const result = await executeAfterPaint(driver, `
    return Array.prototype.map.call(
        document.querySelectorAll(".octane-management-metric-tile"),
        function (tile) {
          var tileRect = tile.getBoundingClientRect();
          var tileStyle = getComputedStyle(tile);
          var descendants = tile.querySelectorAll(
              ".octane-management-metric-title, .octane-management-metric-value, "
              + ".octane-management-metric-detail, .octane-management-metric-items");
          var withinTile = Array.prototype.every.call(descendants, function (element) {
            var rect = element.getBoundingClientRect();
            return rect.left >= tileRect.left - 1
                && rect.right <= tileRect.right + 1
                && rect.top >= tileRect.top - 1
                && rect.bottom <= tileRect.bottom + 1;
          });
          var title = tile.querySelector(".octane-management-metric-title");
          var value = tile.querySelector(".octane-management-metric-value");
          var rows = Array.prototype.map.call(
              tile.querySelectorAll(".octane-management-metric-items li"),
              function (row) {
                var label = row.querySelector("span");
                var itemValue = row.querySelector("strong");
                var rowStyle = getComputedStyle(row);
                var labelStyle = getComputedStyle(label);
                var labelRect = label.getBoundingClientRect();
                var valueRect = itemValue.getBoundingClientRect();
                return {
                  display: rowStyle.display,
                  flexWrap: rowStyle.flexWrap,
                  labelOverflow: labelStyle.overflow,
                  labelTextOverflow: labelStyle.textOverflow,
                  labelWhiteSpace: labelStyle.whiteSpace,
                  labelIsTruncated: label.scrollWidth > label.clientWidth,
                  valueInline: Math.abs(labelRect.top - valueRect.top) <= 1
                };
              });
          return {
            height: tileRect.height,
            key: tile.getAttribute("data-management-metric-key") || "",
            overflow: tileStyle.overflow,
            paddingBottom: parseFloat(tileStyle.paddingBottom),
            paddingLeft: parseFloat(tileStyle.paddingLeft),
            paddingRight: parseFloat(tileStyle.paddingRight),
            paddingTop: parseFloat(tileStyle.paddingTop),
            rows: rows,
            titleFontWeight: getComputedStyle(title).fontWeight,
            titleTextAlign: getComputedStyle(title).textAlign,
            valueFontSize: parseFloat(getComputedStyle(value).fontSize),
            valueFontWeight: getComputedStyle(value).fontWeight,
            valueTextAlign: getComputedStyle(value).textAlign,
            width: tileRect.width,
            withinTile: withinTile
          };
        });`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

async function managementDefectListLayout(driver) {
  const result = await executeAfterPaint(driver, `
    var card = document.querySelector(
        '[data-card-key="test-management-2"]');
    card.classList.add("octane-expanded");
    var rows = card.querySelectorAll(".octane-management-defect-row");
    var metrics = Array.prototype.map.call(rows, function (row) {
      var identifier = row.querySelector(".octane-management-defect-id");
      var description = row.querySelector(".octane-management-defect-description");
      var pills = row.querySelectorAll(".octane-management-defect-pill");
      var rowRect = row.getBoundingClientRect();
      var idRect = identifier.getBoundingClientRect();
      var descriptionRect = description.getBoundingClientRect();
      var pillRects = Array.prototype.map.call(
          pills, function (pill) { return pill.getBoundingClientRect(); });
      return {
        columns: getComputedStyle(row).gridTemplateColumns.trim().split(" ").length,
        descriptionAfterId: descriptionRect.left >= idRect.right - 1,
        descriptionBeforePills:
            descriptionRect.right <= pillRects[0].left + 1,
        pillWidths: pillRects.map(function (rect) { return rect.width; }),
        rowContainsContent:
            idRect.left >= rowRect.left - 1
            && descriptionRect.left >= rowRect.left - 1
            && pillRects[pillRects.length - 1].right <= rowRect.right + 1,
        rowOverflow: row.scrollWidth - row.clientWidth
      };
    });
    card.classList.remove("octane-expanded");
    return metrics;`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

async function managementDefectHeaderLayout(driver) {
  const result = await executeAfterPaint(driver, `
    var card = document.querySelector(
        '[data-card-key="test-management-2"]');
    card.classList.add("octane-expanded");
    var header = card.querySelector(".octane-management-defect-header");
    var row = card.querySelector(".octane-management-defect-row");
    var headerCells = header.querySelectorAll(
        ".octane-management-defect-header-cell");
    var rowCells = row.children;
    var aligned = Array.prototype.every.call(
        headerCells,
        function (cell, index) {
          var headerRect = cell.getBoundingClientRect();
          var rowRect = rowCells[index].getBoundingClientRect();
          return Math.abs(headerRect.left - rowRect.left) <= 1
              && Math.abs(headerRect.width - rowRect.width) <= 1;
        });
    var activeIndicator = headerCells[0].querySelector(
        ".octane-management-defect-sort-indicator");
    var inactiveIndicator = headerCells[1].querySelector(
        ".octane-management-defect-sort-indicator");
    var headerRects = Array.prototype.map.call(headerCells, function (cell) {
      var rect = cell.getBoundingClientRect();
      return {left: rect.left, width: rect.width};
    });
    var rowRects = Array.prototype.map.call(rowCells, function (cell) {
      var rect = cell.getBoundingClientRect();
      return {left: rect.left, width: rect.width};
    });
    var metrics = {
      activeArrowMoreVisible:
          Number(getComputedStyle(activeIndicator).opacity)
              > Number(getComputedStyle(inactiveIndicator).opacity),
      activeSort: headerCells[0].getAttribute("aria-sort"),
      aligned: aligned,
      columns: getComputedStyle(header).gridTemplateColumns.trim().split(" ").length,
      headerRects: headerRects,
      position: getComputedStyle(header).position,
      rowRects: rowRects
    };
    card.classList.remove("octane-expanded");
    return metrics;`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

async function managementCategoryNavigationLayout(driver) {
  const result = await executeAfterPaint(driver, `
    var card = document.querySelector('[data-card-key="test-management-2"]');
    card.classList.add("octane-expanded");
    var header = card.querySelector(".octane-card-header");
    var navigation = card.querySelector("[data-management-failure-tab-nav]");
    var switcher = card.querySelector("[data-management-failure-switcher]");
    var arrows = navigation.querySelectorAll("[data-management-category-scroll]");
    var buttons = switcher.querySelectorAll(".octane-management-category-toggle");
    var headerRect = header.getBoundingClientRect();
    var navigationRect = navigation.getBoundingClientRect();
    var buttonRects = Array.prototype.map.call(
        buttons, function (button) { return button.getBoundingClientRect(); });
    var firstTop = buttonRects[0].top;
    switcher.style.scrollBehavior = "auto";
    switcher.scrollLeft = 0;
    var before = switcher.scrollLeft;
    switcher.scrollLeft = switcher.scrollWidth;
    var after = switcher.scrollLeft;
    var metrics = {
      arrowSizes: Array.prototype.map.call(arrows, function (arrow) {
        var rect = arrow.getBoundingClientRect();
        return {height: rect.height, width: rect.width};
      }),
      headerContainsNavigation:
          navigationRect.left >= headerRect.left - 1
          && navigationRect.right <= headerRect.right + 1,
      navigationHeight: navigationRect.height,
      oneLine: buttonRects.every(function (rect) {
        return Math.abs(rect.top - firstTop) <= 1;
      }),
      overflow: switcher.scrollWidth > switcher.clientWidth,
      scrolled: after > before,
      switcherHeight: switcher.getBoundingClientRect().height
    };
    card.classList.remove("octane-expanded");
    return metrics;`);
  if (result.error) {
    throw new Error(result.error);
  }
  return result.value;
}

function assertVisibleGraphs(metrics, label, minimumSize = 24) {
  assert.equal(metrics.length, 4, `${label}: all four timer graphs must render`);
  for (const [index, metric] of metrics.entries()) {
    assert.equal(metric.display, "block", `${label}, graph ${index}: display`);
    assert.equal(metric.visibility, "visible", `${label}, graph ${index}: visibility`);
    assert.ok(metric.opacity > 0, `${label}, graph ${index}: opacity`);
    assert.ok(
        metric.graphWidth >= minimumSize && metric.graphHeight >= minimumSize,
        `${label}, graph ${index}: ${metric.graphWidth}x${metric.graphHeight}`);
    assert.ok(
        Math.abs(metric.graphWidth - metric.graphHeight) <= 1.5,
        `${label}, graph ${index}: graph must remain square ${JSON.stringify(metric)}`);
    assert.ok(
        metric.withinBody,
        `${label}, graph ${index}: graph must fit its body ${JSON.stringify(metric)}`);
  }
}

const browserAvailable =
    (existsSync(snapGeckodriver) || executableAvailable("geckodriver"))
    && executableAvailable("firefox");

test(
    "all timer graphs render and scale in normal, focused, and expanded modes",
    {skip: !browserAvailable, timeout: 120000},
    async () => {
      await withFirefox(async driver => {
        const fixture = Buffer.from(fixtureHtml()).toString("base64");
        await webdriverRequest(
            driver.baseUrl,
            "POST",
            `/session/${driver.sessionId}/url`,
            {url: `data:text/html;base64,${fixture}`});

        const desktopSizes = {};
        for (const viewport of viewports) {
          await setViewport(driver, viewport);

          await setMode(driver, "normal");
          const normal = await graphMetrics(driver);
          assertVisibleGraphs(normal, `${viewport.name} normal`);
          const zones = await normalZoneMetrics(driver);
          assert.ok(
              Math.abs(zones.timer.height - zones.management.height) <= 1,
              `${viewport.name}: timer and management zones differ: ${JSON.stringify(zones)}`);
          assert.deepEqual(
              zones.timer.cardHeights.map(Math.round),
              zones.management.cardHeights.map(Math.round),
              `${viewport.name}: timer and management card rows must match`);
          assert.ok(
              zones.management.cardHeights.every(height => Math.abs(height - 280) <= 1),
              `${viewport.name}: management cards must remain at 280px in normal mode`);
          const barMetrics = await constrainedManagementBarMetrics(driver);
          assert.ok(
              barMetrics.stateBarWidths.every(width => width >= 8 && width <= 100),
              `${viewport.name}: state bar width escaped bounds: ${JSON.stringify(barMetrics)}`);
          assert.ok(
              barMetrics.failureBarWidths.every(width => width >= 8 && width <= 100),
              `${viewport.name}: failure bar width escaped bounds: ${JSON.stringify(barMetrics)}`);
          assert.ok(
              barMetrics.failureBarBottoms.every(
                  bottom => Math.abs(bottom - barMetrics.failureAxisY) <= 1),
              `${viewport.name}: failure bars are not anchored above the x-axis: `
                  + JSON.stringify(barMetrics));
          assert.ok(
              barMetrics.stateScrollWidth > barMetrics.stateClientWidth,
              `${viewport.name}: state x-axis did not overflow cleanly`);
          assert.ok(
              barMetrics.failureScrollWidth > barMetrics.failureClientWidth,
              `${viewport.name}: failure x-axis did not overflow cleanly`);
          assert.equal(barMetrics.labelOverflow, "hidden");
          assert.equal(barMetrics.labelTextOverflow, "ellipsis");
          assert.equal(barMetrics.labelWhiteSpace, "nowrap");
          assert.ok(barMetrics.labelScrollWidth > barMetrics.labelClientWidth);
          const axisMetrics = await managementAxisSpacingMetrics(driver);
          assert.equal(axisMetrics.length, 3);
          assert.ok(
              axisMetrics.every(metric => Math.abs(metric.columnGap - 1.44) <= 0.2),
              `${viewport.name}: management axis gap differs from tester progress: `
                  + JSON.stringify(axisMetrics));
          assert.ok(
              axisMetrics.every(metric => metric.gridColumns.split(" ").length === 3),
              `${viewport.name}: management axis grid lost its three tracks: `
                  + JSON.stringify(axisMetrics));
          assert.ok(
              axisMetrics.every(metric => metric.labelWidth < 30.4),
              `${viewport.name}: tick labels retained the old 1.9rem reservation: `
                  + JSON.stringify(axisMetrics));
          assert.ok(
              axisMetrics.every(metric => metric.plotGain >= 6),
              `${viewport.name}: plot did not absorb the reclaimed axis width: `
                  + JSON.stringify(axisMetrics));
          if (viewport.name === "compact") {
            const metricTiles = await compactManagementMetricLayout(driver);
            assert.equal(metricTiles.length, 4);
            assert.ok(
                metricTiles.every(metric => metric.withinTile),
                `Compact metric content crossed a quadrant: ${JSON.stringify(metricTiles)}`);
            assert.ok(
                metricTiles.every(metric => metric.valueFontSize <= 21.6),
                `Compact metric value text did not scale down: ${JSON.stringify(metricTiles)}`);
            assert.ok(
                metricTiles.every(metric => metric.overflow === "hidden"),
                `Compact metric quadrants do not clip overflow: ${JSON.stringify(metricTiles)}`);
            assert.ok(
                metricTiles.every(metric =>
                  Math.min(
                      metric.paddingTop,
                      metric.paddingRight,
                      metric.paddingBottom,
                      metric.paddingLeft) >= 2),
                `Compact metric padding dropped below 2px: ${JSON.stringify(metricTiles)}`);
            assert.ok(
                metricTiles.every(metric =>
                  (metric.key === "tester-volume" || metric.key === "tester-defects"
                    ? metric.titleTextAlign === "start"
                    : metric.titleTextAlign === "center")
                    && metric.valueTextAlign === "center"
                    && metric.titleFontWeight === "600"
                    && metric.valueFontWeight === "700"),
                `Compact metric typography did not scale and align by tile type: `
                    + JSON.stringify(metricTiles));
            const testerRows = metricTiles
                .filter(metric =>
                  metric.key === "tester-volume" || metric.key === "tester-defects")
                .flatMap(metric => metric.rows);
            assert.ok(testerRows.length > 0);
            assert.ok(
                testerRows.every(row =>
                  row.display === "grid"
                    && row.flexWrap === "nowrap"
                    && row.labelOverflow === "hidden"
                    && row.labelTextOverflow === "ellipsis"
                    && row.labelWhiteSpace === "nowrap"
                    && row.valueInline),
                `Tester metric rows wrapped or escaped: ${JSON.stringify(testerRows)}`);
            assert.ok(
                testerRows.some(row => row.labelIsTruncated),
                `Tester metric ellipsis was not exercised: ${JSON.stringify(testerRows)}`);
          }
          const defectRows = await managementDefectListLayout(driver);
          assert.equal(defectRows.length, 6);
          assert.ok(
              defectRows.every(row => row.columns === 4),
              `${viewport.name}: defect rows lost their four-column layout: `
                  + JSON.stringify(defectRows));
          assert.ok(
              defectRows.every(
                  row => row.descriptionAfterId && row.descriptionBeforePills),
              `${viewport.name}: defect text crossed a column boundary: `
                  + JSON.stringify(defectRows));
          assert.ok(
              defectRows.every(row => row.rowContainsContent && row.rowOverflow <= 1),
              `${viewport.name}: defect content escaped its row: ${JSON.stringify(defectRows)}`);
          const pillWidths = defectRows.flatMap(row => row.pillWidths);
          assert.ok(
              Math.max(...pillWidths) - Math.min(...pillWidths) <= 1,
              `${viewport.name}: defect pills are not uniform: ${JSON.stringify(pillWidths)}`);
          const defectHeader = await managementDefectHeaderLayout(driver);
          assert.equal(defectHeader.activeArrowMoreVisible, true);
          assert.equal(defectHeader.activeSort, "ascending");
          assert.equal(defectHeader.columns, 4);
          assert.equal(defectHeader.position, "sticky");
          assert.equal(
              defectHeader.aligned,
              true,
              `${viewport.name}: defect sort columns are misaligned: `
                  + JSON.stringify(defectHeader));
          const categoryNavigation = await managementCategoryNavigationLayout(driver);
          assert.equal(
              categoryNavigation.overflow,
              true,
              `${viewport.name}: defect group strip did not overflow`);
          assert.equal(
              categoryNavigation.oneLine,
              true,
              `${viewport.name}: defect group tabs wrapped: ${JSON.stringify(categoryNavigation)}`);
          assert.equal(
              categoryNavigation.scrolled,
              true,
              `${viewport.name}: defect group strip did not scroll`);
          assert.equal(
              categoryNavigation.headerContainsNavigation,
              true,
              `${viewport.name}: defect group controls escaped the top bar`);
          assert.ok(
              categoryNavigation.navigationHeight
                  <= categoryNavigation.switcherHeight + 2,
              `${viewport.name}: defect group arrows distorted the top bar: `
                  + JSON.stringify(categoryNavigation));
          assert.ok(
              categoryNavigation.arrowSizes.every(
                  size => size.width <= 20 && size.height <= 20),
              `${viewport.name}: defect group arrows are oversized: `
                  + JSON.stringify(categoryNavigation));
          for (const metric of normal) {
            assert.ok(
                metric.graphWidth <= 221,
                `${viewport.name} normal: graph exceeds the 220px card cap`);
          }

          await setMode(driver, "focused");
          const focused = await graphMetrics(driver);
          assertVisibleGraphs(focused, `${viewport.name} focused`);

          const expanded = [];
          for (let cardIndex = 0; cardIndex < 4; cardIndex += 1) {
            await setMode(driver, "expanded", cardIndex);
            const expandedMetrics = await graphMetrics(driver);
            assertVisibleGraphs(expandedMetrics, `${viewport.name} expanded ${cardIndex}`);
            expanded.push(expandedMetrics[cardIndex]);
          }

          if (viewport.name === "desktop") {
            desktopSizes.normal = normal[0].graphWidth;
            desktopSizes.focused = focused[0].graphWidth;
            desktopSizes.expanded = expanded[0].graphWidth;
            desktopSizes.expandedMetric = expanded[0];
          }
        }

        assert.ok(
            desktopSizes.focused > desktopSizes.normal * 1.2,
            `Focused graph did not grow: ${JSON.stringify(desktopSizes)}`);
        assert.ok(
            desktopSizes.expanded > desktopSizes.focused * 1.2,
            `Expanded graph did not grow: ${JSON.stringify(desktopSizes)}`);
      });
    });
