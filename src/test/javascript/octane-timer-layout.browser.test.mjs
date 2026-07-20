import assert from "node:assert/strict";
import {spawn, spawnSync} from "node:child_process";
import {readFileSync} from "node:fs";
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
      "geckodriver",
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
      process.kill(-driver.pid, "SIGKILL");
    } catch (error) {
      // The browser process group already exited after deleting the session.
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

function fixtureHtml() {
  const cards = [
    "Testing Time Remaining",
    "Status Check",
    "Execution Progress",
    "Execution Pass Rate"
  ].map((title, index) => timerCard(index, title)).join("");
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
        document.querySelectorAll(".octane-chart-card"),
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

const browserAvailable = executableAvailable("geckodriver") && executableAvailable("firefox");

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
