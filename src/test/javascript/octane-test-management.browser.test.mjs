import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {mkdtempSync, readFileSync, rmSync, writeFileSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import test from "node:test";
import {pathToFileURL} from "node:url";

const source = readFileSync("src/main/webapp/js/octane-test-management.js", "utf8");
const chromiumAvailable =
    spawnSync("sh", ["-c", "command -v google-chrome"], {stdio: "ignore"}).status === 0;

function defects(category, count, startId) {
  return Array.from({length: count}, (_, index) => ({
    category,
    defect: {
      description: `${category} defect ${index + 1}`,
      id: String(startId + index),
      open: index % 2 === 0,
      severity: "High",
      severityColorKey: "high",
      severityLabel: "High",
      status: index % 2 === 0 ? "Open" : "Closed"
    }
  })).map(entry => entry.defect);
}

function category(key, label, count, startId) {
  const categoryDefects = defects(key, count, startId);
  return {
    closed: categoryDefects.filter(defect => !defect.open).length,
    defects: categoryDefects,
    key,
    label,
    open: categoryDefects.filter(defect => defect.open).length
  };
}

function fixtureHtml() {
  const payload = {
    failureCategories: [
      category("ui", "UI", 5, 1),
      category("api", "API", 3, 6),
      category("database", "Database", 4, 9),
      category("network", "Network", 2, 13)
    ],
    totalDefects: 14
  };
  const refreshedPayload = {
    failureCategories: [
      category("ui", "UI", 6, 20),
      category("api", "API", 3, 30),
      category("database", "Database", 4, 40),
      category("network", "Network", 3, 50)
    ],
    totalDefects: 16
  };
  return `<!doctype html><html><body>
    <section id="management-zone">
      <article class="octane-test-management-card"
          data-card-key="test-management-failures">
        <div data-management-legend></div>
        <div data-management-failure-switcher data-selected-category="all"
            data-selected-status="all"></div>
        <div data-management-failures>
          <div class="octane-management-failure-axis-layout"></div>
          <div data-management-y-labels></div>
          <div data-management-failure-bars></div>
        </div>
        <div data-management-defect-list data-sort-column="id"
            data-sort-direction="ascending"></div>
      </article>
    </section>
    <script>${source}</script>
    <script>
      var zone = document.getElementById("management-zone");
      var card = zone.querySelector('[data-card-key="test-management-failures"]');
      OctaneTestManagement.mount(zone, ${JSON.stringify(payload)}, {
        onCategorySelect: function () {
          card.classList.add("octane-expanded");
        }
      });

      document.body.setAttribute(
          "data-all-label",
          zone.querySelector('[data-management-category-filter="all"]').textContent);

      zone.querySelector('[data-management-category-filter="ui"]').click();
      var pillRows = Array.prototype.filter.call(
          zone.querySelectorAll(".octane-management-defect-row"),
          function (row) { return !row.hidden; });
      document.body.setAttribute("data-pill-count", String(pillRows.length));
      document.body.setAttribute("data-pill-categories", pillRows.map(function (row) {
        return row.getAttribute("data-management-defect-category");
      }).join(","));

      zone.querySelector('[data-management-category="database"]').click();
      var barRows = Array.prototype.filter.call(
          zone.querySelectorAll(".octane-management-defect-row"),
          function (row) { return !row.hidden; });
      document.body.setAttribute("data-bar-count", String(barRows.length));
      document.body.setAttribute("data-bar-categories", barRows.map(function (row) {
        return row.getAttribute("data-management-defect-category");
      }).join(","));

      zone.querySelector('[data-management-category-filter="api"]').click();
      var focusedPillRows = Array.prototype.filter.call(
          zone.querySelectorAll(".octane-management-defect-row"),
          function (row) { return !row.hidden; });
      document.body.setAttribute("data-focused-pill-count", String(focusedPillRows.length));
      document.body.setAttribute(
          "data-focused-pill-categories",
          focusedPillRows.map(function (row) {
            return row.getAttribute("data-management-defect-category");
          }).join(","));

      zone.querySelector('[data-management-category-filter="all"]').click();
      var allRows = Array.prototype.filter.call(
          zone.querySelectorAll(".octane-management-defect-row"),
          function (row) { return !row.hidden; });
      var switcher = zone.querySelector("[data-management-failure-switcher]");
      document.body.setAttribute("data-all-count", String(allRows.length));
      document.body.setAttribute(
          "data-all-selected",
          zone.querySelector('[data-management-category-filter="all"]')
              .getAttribute("aria-pressed"));
      document.body.setAttribute(
          "data-all-status", switcher.getAttribute("data-selected-status"));

      zone.querySelector('[data-management-category-filter="network"]').click();
      OctaneTestManagement.update(zone, ${JSON.stringify(refreshedPayload)});
      setTimeout(function () {
        var refreshedRows = Array.prototype.filter.call(
            zone.querySelectorAll(".octane-management-defect-row"),
            function (row) { return !row.hidden; });
        document.body.setAttribute("data-refresh-count", String(refreshedRows.length));
        document.body.setAttribute(
            "data-refresh-categories",
            refreshedRows.map(function (row) {
              return row.getAttribute("data-management-defect-category");
            }).join(","));
        document.body.setAttribute(
            "data-refresh-selected",
            zone.querySelector('[data-management-category-filter="network"]')
                .getAttribute("aria-pressed"));
        var refreshedBar = zone.querySelector(".octane-management-failure-group");
        var refreshedRow = zone.querySelector(".octane-management-defect-row");
        OctaneTestManagement.update(
            zone, JSON.parse(${JSON.stringify(JSON.stringify(refreshedPayload))}));
        setTimeout(function () {
          document.body.setAttribute(
              "data-unchanged-bar-reused",
              String(refreshedBar === zone.querySelector(".octane-management-failure-group")));
          document.body.setAttribute(
              "data-unchanged-row-reused",
              String(refreshedRow === zone.querySelector(".octane-management-defect-row")));
        }, 50);
      }, 50);
    </script>
  </body></html>`;
}

test(
    "delegated pill and vertical-bar clicks filter the rendered defect rows by category",
    {skip: !chromiumAvailable, timeout: 30000},
    () => {
      const directory = mkdtempSync(join(tmpdir(), "octane-category-filter-"));
      const fixturePath = join(directory, "fixture.html");
      writeFileSync(fixturePath, fixtureHtml(), "utf8");
      try {
        const result = spawnSync(
            "google-chrome",
            [
              "--headless=new",
              "--no-sandbox",
              "--disable-gpu",
              `--user-data-dir=${join(directory, "profile")}`,
              "--virtual-time-budget=1000",
              "--dump-dom",
              pathToFileURL(fixturePath).href
            ],
            {encoding: "utf8", maxBuffer: 4 * 1024 * 1024, timeout: 20000});

        assert.equal(result.status, 0, result.error || result.stderr);
        assert.match(result.stdout, /data-all-label="All 14"/);
        assert.match(result.stdout, /data-pill-count="5"/);
        assert.match(result.stdout, /data-pill-categories="ui,ui,ui,ui,ui"/);
        assert.match(result.stdout, /data-bar-count="4"/);
        assert.match(
            result.stdout,
            /data-bar-categories="database,database,database,database"/);
        assert.match(result.stdout, /data-focused-pill-count="3"/);
        assert.match(result.stdout, /data-focused-pill-categories="api,api,api"/);
        assert.match(result.stdout, /data-all-count="14"/);
        assert.match(result.stdout, /data-all-selected="true"/);
        assert.match(result.stdout, /data-all-status="all"/);
        assert.match(result.stdout, /data-refresh-count="3"/);
        assert.match(result.stdout, /data-refresh-categories="network,network,network"/);
        assert.match(result.stdout, /data-refresh-selected="true"/);
        assert.match(result.stdout, /data-unchanged-bar-reused="true"/);
        assert.match(result.stdout, /data-unchanged-row-reused="true"/);
      } finally {
        rmSync(directory, {force: true, recursive: true});
      }
    });
