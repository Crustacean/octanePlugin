(function (global) {
  "use strict";

  var SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  var TIMELINE_BOUNDS = {
    bottom: 360,
    left: 80,
    right: 920,
    top: 20,
    width: 1000
  };
  var SYSTEM_COLORS = {
    blue: "#4391F5",
    gray: "#8E8E93",
    green: "#30D158",
    lightGreen: "#34C759",
    orange: "#FF9F0A",
    purple: "#BF5AF2",
    red: "#FF453A"
  };
  var DEFAULT_COLORS = {
    blocked: SYSTEM_COLORS.orange,
    closed: SYSTEM_COLORS.lightGreen,
    executed: SYSTEM_COLORS.blue,
    failed: SYSTEM_COLORS.red,
    inProgress: SYSTEM_COLORS.blue,
    open: SYSTEM_COLORS.red,
    passed: SYSTEM_COLORS.green,
    planned: SYSTEM_COLORS.gray,
    skipped: SYSTEM_COLORS.purple
  };
  var THEME_COLOR_PROPERTIES = {
    blocked: "--octane-status-blocked",
    closed: "--octane-color-good",
    executed: "--octane-color-neutral",
    failed: "--octane-status-failed",
    inProgress: "--octane-color-neutral",
    open: "--octane-color-bad",
    passed: "--octane-status-passed",
    planned: "--octane-status-no-run",
    skipped: "--octane-status-skipped"
  };
  var STATE_KEYS = ["passed", "failed", "blocked", "skipped"];
  var STATE_LABELS = {
    blocked: "Blocked",
    failed: "Failed",
    passed: "Passed",
    skipped: "Skipped"
  };
  var DEFAULT_SEVERITY_COLORS = {
    critical: "#FF3B30",
    high: "#FF9500",
    low: "#5AC8FA",
    medium: "#AF52DE",
    unspecified: "#8E8E93",
    veryhigh: "#FFCC00"
  };
  var SEVERITY_COLOR_PROPERTIES = {
    critical: "--octane-severity-critical",
    high: "--octane-severity-high",
    low: "--octane-severity-low",
    medium: "--octane-severity-medium",
    unspecified: "--octane-severity-unspecified",
    veryhigh: "--octane-severity-very-high"
  };
  var DEFECT_SORT_COLUMNS = [
    {key: "id", label: "Defect ID"},
    {key: "description", label: "Defect Description"},
    {key: "status", label: "Status"},
    {key: "severity", label: "Severity"}
  ];

  function finiteNumber(value, fallback) {
    var number = Number(value);
    return Number.isFinite(number) ? number : fallback;
  }

  function nonNegative(value) {
    return Math.max(0, finiteNumber(value, 0));
  }

  function array(value) {
    return Array.isArray(value) ? value : [];
  }

  function clear(element) {
    while (element && element.firstChild) {
      element.removeChild(element.firstChild);
    }
  }

  function text(element, value) {
    if (element) {
      element.textContent = value == null ? "" : String(value);
    }
  }

  function createElement(tagName, className) {
    var element = document.createElement(tagName);
    if (className) {
      element.className = className;
    }
    return element;
  }

  function createSvgElement(tagName, className) {
    var element = document.createElementNS(SVG_NAMESPACE, tagName);
    if (className) {
      element.setAttribute("class", className);
    }
    return element;
  }

  function canonicalSeverity(value) {
    var normalized = String(value || "")
        .toLowerCase()
        .replace(/[^a-z0-9]/g, "");
    if (normalized.indexOf("critical") >= 0) {
      return "Critical";
    }
    if (normalized.indexOf("veryhigh") >= 0) {
      return "Very High";
    }
    if (normalized.indexOf("high") >= 0) {
      return "High";
    }
    if (normalized.indexOf("medium") >= 0) {
      return "Medium";
    }
    if (normalized.indexOf("low") >= 0) {
      return "Low";
    }
    return "Unspecified";
  }

  function displayedSeverity(defect) {
    var configuredLabel = String(defect && defect.severityLabel || "").trim();
    return configuredLabel || canonicalSeverity(defect && defect.severity);
  }

  function canonicalStatus(defect) {
    if (defect && typeof defect.open === "boolean") {
      return defect.open ? "Open" : "Closed";
    }
    return String(defect && (defect.status || defect.phase) || "")
        .toLowerCase()
        .match(/closed|fixed|done|resolved|rejected/)
        ? "Closed"
        : "Open";
  }

  function naturalDefectIdCompare(left, right) {
    return String(left && left.id || "").localeCompare(
        String(right && right.id || ""),
        undefined,
        {numeric: true, sensitivity: "base"});
  }

  function descriptionInitial(defect) {
    var description = String(defect && defect.description || "").trim();
    var firstWord = description.split(/\s+/, 1)[0] || "";
    return firstWord.charAt(0).toLocaleLowerCase();
  }

  function defectSeveritySortRank(defect) {
    var configuredRank = Number(defect && defect.severitySortRank);
    if (Number.isFinite(configuredRank) && configuredRank > 0) {
      return configuredRank;
    }
    switch (canonicalSeverity(defect && defect.severity)) {
      case "Critical":
        return 1;
      case "Very High":
        return 2;
      case "High":
        return 3;
      case "Low":
        return 4;
      case "Medium":
        return 5;
      default:
        return 6;
    }
  }

  function compareFailureDefects(left, right, column) {
    switch (column) {
      case "description":
        return descriptionInitial(left).localeCompare(descriptionInitial(right));
      case "status":
        return (canonicalStatus(left) === "Open" ? 0 : 1)
            - (canonicalStatus(right) === "Open" ? 0 : 1);
      case "severity":
        return defectSeveritySortRank(left) - defectSeveritySortRank(right);
      default:
        return naturalDefectIdCompare(left, right);
    }
  }

  function sortFailureDefects(defects, column, direction) {
    var sortColumn = DEFECT_SORT_COLUMNS.some(function (candidate) {
      return candidate.key === column;
    }) ? column : "id";
    var multiplier = direction === "descending" ? -1 : 1;
    return array(defects)
        .map(function (defect, index) {
          return {defect: defect, index: index};
        })
        .sort(function (left, right) {
          var comparison =
              compareFailureDefects(left.defect, right.defect, sortColumn);
          return comparison === 0
              ? left.index - right.index
              : comparison * multiplier;
        })
        .map(function (entry) {
          return entry.defect;
        });
  }

  function defectSortState(table) {
    var column = table.getAttribute("data-sort-column") || "id";
    var direction = table.getAttribute("data-sort-direction") || "ascending";
    return {
      column: DEFECT_SORT_COLUMNS.some(function (candidate) {
        return candidate.key === column;
      }) ? column : "id",
      direction: direction === "descending" ? "descending" : "ascending"
    };
  }

  function renderDefectTableHeader(table, state) {
    var header = createElement("div", "octane-management-defect-header");
    header.setAttribute("role", "row");
    DEFECT_SORT_COLUMNS.forEach(function (column) {
      var cell = createElement("div", "octane-management-defect-header-cell");
      cell.setAttribute("role", "columnheader");
      if (column.key === state.column) {
        cell.setAttribute("aria-sort", state.direction);
      }
      var button = createElement("button", "octane-management-defect-sort");
      button.type = "button";
      button.setAttribute("data-management-defect-sort", column.key);
      button.setAttribute(
          "aria-label",
          "Sort by "
              + column.label
              + (column.key === state.column
                ? ", currently " + state.direction
                : ""));
      var label = createElement("span", "octane-management-defect-sort-label");
      label.textContent = column.label;
      var indicator = createElement(
          "span", "octane-management-defect-sort-indicator");
      indicator.setAttribute("aria-hidden", "true");
      button.appendChild(label);
      button.appendChild(indicator);
      cell.appendChild(button);
      header.appendChild(cell);
    });
    table.appendChild(header);
  }

  function updateDefectSort(zone, button) {
    var table = zone.querySelector("[data-management-defect-list]");
    if (!table) {
      return;
    }
    var state = defectSortState(table);
    var column = button.getAttribute("data-management-defect-sort") || "id";
    var direction =
        state.column === column && state.direction === "ascending"
        ? "descending"
        : "ascending";
    table.setAttribute("data-sort-column", column);
    table.setAttribute("data-sort-direction", direction);
    renderFailureDetails(zone);
    var active = table.querySelector(
        '[data-management-defect-sort="' + column + '"]');
    if (active) {
      active.focus({preventScroll: true});
    }
  }

  function severityColor(severity, zone) {
    var key = String(severity || "").toLowerCase().replace(/[^a-z0-9]/g, "");
    if (!Object.prototype.hasOwnProperty.call(DEFAULT_SEVERITY_COLORS, key)) {
      key = "unspecified";
    }
    return themeColor(
        zone, SEVERITY_COLOR_PROPERTIES[key], DEFAULT_SEVERITY_COLORS[key]);
  }

  function emphasisTextColor(zone) {
    return themeColor(zone, "--octane-color-on-emphasis", "#000000");
  }

  function themeColor(zone, propertyName, fallback) {
    if (!zone || typeof global.getComputedStyle !== "function") {
      return fallback;
    }
    var value = global.getComputedStyle(zone).getPropertyValue(propertyName).trim();
    return value || fallback;
  }

  function colorsFor(payload, zone) {
    var source = payload && payload.colors ? payload.colors : {};
    var colors = {};
    Object.keys(DEFAULT_COLORS).forEach(function (key) {
      var fallback = source[key] || DEFAULT_COLORS[key];
      colors[key] = themeColor(zone, THEME_COLOR_PROPERTIES[key], fallback);
    });
    return colors;
  }

  function normalizedPoints(payload) {
    return array(payload && payload.points)
        .map(function (point) {
          return {
            blocked: nonNegative(point.blocked),
            elapsedMillis: nonNegative(point.elapsedMillis),
            executed: nonNegative(point.executed),
            failed: nonNegative(point.failed),
            inProgress: nonNegative(point.inProgress),
            passed: nonNegative(point.passed),
            planned: nonNegative(point.planned),
            skipped: nonNegative(point.skipped),
            total: nonNegative(point.total)
          };
        })
        .sort(function (left, right) {
          return left.elapsedMillis - right.elapsedMillis;
        });
  }

  function derivedExecutionIntervals(points, durationMillis) {
    var intervals = [];
    var previous = {blocked: 0, failed: 0, passed: 0, skipped: 0};
    for (var index = 0; index < 10; index += 1) {
      intervals.push({
        blocked: 0,
        failed: 0,
        index: index,
        passed: 0,
        skipped: 0,
        total: 0
      });
    }
    points.forEach(function (point) {
      var elapsed = Math.min(durationMillis, nonNegative(point.elapsedMillis));
      var intervalIndex = elapsed <= 0
          ? 0
          : Math.min(9, Math.floor(((elapsed - 1) * 10) / durationMillis));
      STATE_KEYS.forEach(function (key) {
        var increment = Math.max(0, nonNegative(point[key]) - previous[key]);
        intervals[intervalIndex][key] += increment;
        intervals[intervalIndex].total += increment;
        previous[key] = nonNegative(point[key]);
      });
    });
    return intervals;
  }

  function normalizedExecutionIntervals(payload, points, durationMillis) {
    var source = array(payload && payload.executionIntervals);
    if (!source.length) {
      return derivedExecutionIntervals(points, durationMillis);
    }
    var intervals = [];
    for (var index = 0; index < 10; index += 1) {
      var interval = source[index] || {};
      var normalized = {
        blocked: nonNegative(interval.blocked),
        failed: nonNegative(interval.failed),
        index: index,
        passed: nonNegative(interval.passed),
        skipped: nonNegative(interval.skipped)
      };
      normalized.total =
          normalized.passed + normalized.failed + normalized.blocked + normalized.skipped;
      intervals.push(normalized);
    }
    return intervals;
  }

  function chartMaximum(points, keys) {
    var maximum = 0;
    points.forEach(function (point) {
      keys.forEach(function (key) {
        maximum = Math.max(maximum, nonNegative(point[key]));
      });
    });
    return Math.max(1, Math.ceil(maximum));
  }

  function timelineAxisScale(maximum) {
    var highest = Math.max(1, Math.ceil(nonNegative(maximum)));
    var intervals = Math.min(4, highest);
    var step = Math.max(1, Math.ceil(highest / intervals));
    var scaledMaximum = step * intervals;
    var ticks = [];
    for (var index = intervals; index >= 0; index -= 1) {
      ticks.push(index * step);
    }
    return {maximum: scaledMaximum, step: step, ticks: ticks};
  }

  function timelineXFor(elapsedMillis, durationMillis) {
    var duration = Math.max(1, durationMillis);
    var ratio = Math.min(1, nonNegative(elapsedMillis) / duration);
    return TIMELINE_BOUNDS.left
        + ratio * (TIMELINE_BOUNDS.right - TIMELINE_BOUNDS.left);
  }

  function timelineYFor(value, maximum) {
    var ratio = Math.min(1, nonNegative(value) / Math.max(1, maximum));
    return TIMELINE_BOUNDS.bottom
        - ratio * (TIMELINE_BOUNDS.bottom - TIMELINE_BOUNDS.top);
  }

  function pathFor(points, key, maximum, durationMillis) {
    if (!points.length) {
      return "";
    }
    return points.map(function (point, index) {
      var x = timelineXFor(point.elapsedMillis, durationMillis);
      var y = timelineYFor(point[key], maximum);
      return (index === 0 ? "M " : " L ") + x.toFixed(2) + " " + y.toFixed(2);
    }).join("");
  }

  function setYAxisLabels(container, ticks) {
    clear(container);
    array(ticks).forEach(function (tick) {
      var label = createElement("span", "octane-management-axis-value");
      label.textContent = String(tick);
      container.appendChild(label);
    });
  }

  function failureAxisMaximum(categories) {
    var maximum = array(categories).reduce(function (highest, category) {
      var categoryTotal = nonNegative(category.open) + nonNegative(category.closed);
      return Math.max(highest, Math.ceil(categoryTotal));
    }, 0);
    return maximum + 1;
  }

  function integerAxisTicks(maximum) {
    var ceiling = Math.max(1, Math.ceil(nonNegative(maximum)));
    var step = Math.max(1, Math.ceil(ceiling / 8));
    var ticks = [ceiling];
    for (var value = ceiling - 1; value > 0; value -= 1) {
      if (value % step === 0) {
        ticks.push(value);
      }
    }
    ticks.push(0);
    return ticks;
  }

  function failureAxisPosition(value, maximum) {
    return ((maximum - value) / maximum) * 100;
  }

  function setFailureYAxisLabels(container, ticks, maximum) {
    clear(container);
    var track = createElement("span", "octane-management-failure-axis-track");
    ticks.forEach(function (tick) {
      var label = createElement("span", "octane-management-axis-value");
      label.textContent = String(tick);
      label.setAttribute("data-management-axis-value", String(tick));
      label.style.setProperty(
          "--octane-management-axis-position",
          failureAxisPosition(tick, maximum) + "%");
      track.appendChild(label);
    });
    container.appendChild(track);
  }

  function renderFailureGridLines(chart, ticks, maximum) {
    var grid = createElement("span", "octane-management-failure-grid-lines");
    grid.setAttribute("aria-hidden", "true");
    ticks.forEach(function (tick) {
      if (tick === 0) {
        return;
      }
      var line = createElement("span", "octane-management-failure-grid-line");
      line.setAttribute("data-management-grid-value", String(tick));
      line.style.setProperty(
          "--octane-management-axis-position",
          failureAxisPosition(tick, maximum) + "%");
      grid.appendChild(line);
    });
    chart.appendChild(grid);
  }

  function clockLabel(startedAt, offsetMillis) {
    var timestamp = Date.parse(startedAt || "");
    if (!Number.isFinite(timestamp)) {
      return offsetMillis === 0 ? "Start" : "End";
    }
    return new Date(timestamp + offsetMillis).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function setTimelineLabels(container, payload) {
    clear(container);
    var duration = Math.max(1, nonNegative(payload.durationMillis));
    [0, duration / 2, duration].forEach(function (offset) {
      var label = createElement("span", "octane-management-axis-value");
      label.textContent = clockLabel(payload.startedAt, offset);
      container.appendChild(label);
    });
  }

  function renderLineSeries(svg, points, key, maximum, duration, color, className) {
    var path = createSvgElement("path", className);
    path.setAttribute("d", pathFor(points, key, maximum, duration));
    path.setAttribute("stroke", color);
    path.setAttribute("vector-effect", "non-scaling-stroke");
    svg.appendChild(path);
  }

  function appendTimelineSvgLine(svg, className, x1, x2, y) {
    var line = createSvgElement("line", className);
    line.setAttribute("x1", String(x1));
    line.setAttribute("x2", String(x2));
    line.setAttribute("y1", String(y));
    line.setAttribute("y2", String(y));
    line.setAttribute("vector-effect", "non-scaling-stroke");
    svg.appendChild(line);
  }

  function renderTimelineSvgAxes(svg, scale) {
    scale.ticks.forEach(function (tick) {
      if (tick > 0) {
        appendTimelineSvgLine(
            svg,
            "octane-management-grid-line",
            TIMELINE_BOUNDS.left,
            TIMELINE_BOUNDS.right,
            timelineYFor(tick, scale.maximum));
      }
    });
    appendTimelineSvgLine(
        svg,
        "octane-management-timeline-axis-line octane-management-timeline-axis-dotted",
        0,
        TIMELINE_BOUNDS.left,
        TIMELINE_BOUNDS.bottom);
    appendTimelineSvgLine(
        svg,
        "octane-management-timeline-axis-line",
        TIMELINE_BOUNDS.left,
        TIMELINE_BOUNDS.right,
        TIMELINE_BOUNDS.bottom);
    appendTimelineSvgLine(
        svg,
        "octane-management-timeline-axis-line octane-management-timeline-axis-dotted",
        TIMELINE_BOUNDS.right,
        TIMELINE_BOUNDS.width,
        TIMELINE_BOUNDS.bottom);
  }

  function renderTimelineHtmlGrid(plot, scale) {
    var grid = createElement("span", "octane-management-timeline-grid-lines");
    grid.setAttribute("aria-hidden", "true");
    scale.ticks.forEach(function (tick) {
      if (tick <= 0) {
        return;
      }
      var line = createElement("span", "octane-management-timeline-grid-line");
      line.setAttribute("data-management-grid-value", String(tick));
      line.style.setProperty(
          "--octane-management-grid-position",
          ((scale.maximum - tick) / scale.maximum) * 100 + "%");
      grid.appendChild(line);
    });
    plot.appendChild(grid);
  }

  function renderBurnDown(zone, payload, colors) {
    var panel = zone.querySelector("[data-management-burndown]");
    if (!panel) {
      return;
    }
    var svg = panel.querySelector("svg");
    var yLabels = panel.querySelector("[data-management-y-labels]");
    var xLabels = panel.querySelector("[data-management-x-labels]");
    var points = normalizedPoints(payload);
    var scale = timelineAxisScale(
        chartMaximum(points, ["total", "executed", "passed"]));
    var duration = Math.max(1, nonNegative(payload.durationMillis));
    clear(svg);
    renderTimelineSvgAxes(svg, scale);
    renderLineSeries(
        svg, points, "total", scale.maximum, duration, colors.planned,
        "octane-management-line octane-management-line-planned");
    renderLineSeries(
        svg, points, "executed", scale.maximum, duration, colors.executed,
        "octane-management-line octane-management-line-executed");
    renderLineSeries(
        svg, points, "passed", scale.maximum, duration, colors.passed,
        "octane-management-line octane-management-line-passed");
    setYAxisLabels(yLabels, scale.ticks);
    setTimelineLabels(xLabels, payload);
    renderLegend(
        legendForPanel(panel),
        [
          {color: colors.planned, label: "Planned Tests"},
          {color: colors.executed, label: "Executed Tests"},
          {color: colors.passed, label: "Passed Tests"}
        ]);
  }

  function renderCurrentState(zone, payload, colors) {
    var panel = zone.querySelector("[data-management-state]");
    if (!panel) {
      return;
    }
    var plot = panel.querySelector("[data-management-state-bars]");
    var yLabels = panel.querySelector("[data-management-y-labels]");
    var xLabels = panel.querySelector("[data-management-x-labels]");
    var points = normalizedPoints(payload);
    var duration = Math.max(1, nonNegative(payload.durationMillis));
    var intervals = normalizedExecutionIntervals(payload, points, duration);
    var maximum = Math.max(
        1,
        intervals.reduce(function (highest, interval) {
          return Math.max(highest, interval.total);
        }, 0));
    var scale = timelineAxisScale(maximum);
    clear(plot);
    renderTimelineHtmlGrid(plot, scale);
    intervals.forEach(function (interval, partIndex) {
      var column = createElement("div", "octane-management-state-column");
      column.setAttribute(
          "aria-label",
          "Sprint part " + String(partIndex + 1) + " of 10");
      STATE_KEYS.forEach(function (key) {
        var count = nonNegative(interval[key]);
        var segment = createElement("span", "octane-management-state-segment");
        segment.setAttribute("data-status", key);
        segment.style.setProperty("--octane-segment-color", colors[key]);
        segment.style.setProperty(
            "--octane-segment-size", ((count / scale.maximum) * 100) + "%");
        segment.title = STATE_LABELS[key] + ": " + count;
        column.appendChild(segment);
      });
      plot.appendChild(column);
    });
    setYAxisLabels(yLabels, scale.ticks);
    setTimelineLabels(xLabels, payload);
    renderLegend(
        legendForPanel(panel),
        STATE_KEYS.map(function (key) {
          return {color: colors[key], label: STATE_LABELS[key]};
        }));
  }

  function legendForPanel(panel) {
    var card = panel ? panel.closest(".octane-test-management-card") : null;
    return card ? card.querySelector("[data-management-legend]") : null;
  }

  function renderLegend(container, items) {
    clear(container);
    array(items).forEach(function (item) {
      var row = createElement("span", "octane-management-legend-item");
      var swatch = createElement("span", "octane-management-legend-swatch");
      swatch.style.setProperty("--octane-legend-color", item.color);
      var label = createElement("span");
      label.textContent = item.label;
      row.appendChild(swatch);
      row.appendChild(label);
      container.appendChild(row);
    });
  }

  function renderFailureAnalysis(zone, payload, colors) {
    var panel = zone.querySelector("[data-management-failures]");
    if (!panel) {
      return;
    }
    var categories = array(payload.failureCategories);
    var chart = panel.querySelector("[data-management-failure-bars]");
    var yLabels = panel.querySelector("[data-management-y-labels]");
    var switcher = zone.querySelector("[data-management-failure-switcher]");
    var maximum = failureAxisMaximum(categories);
    var ticks = integerAxisTicks(maximum);
    clear(chart);
    renderFailureGridLines(chart, ticks, maximum);
    categories.forEach(function (category) {
      var button = createElement("button", "octane-management-failure-group");
      button.type = "button";
      button.title = category.label || "Category";
      button.setAttribute("data-management-category", category.key || "");
      button.setAttribute(
          "aria-label",
          String(category.label || "Category")
              + ": "
              + nonNegative(category.open)
              + " open and "
              + nonNegative(category.closed)
              + " closed defects");
      var bars = createElement("span", "octane-management-failure-bars");
      [
        {
          count: nonNegative(category.open),
          color: colors.open,
          label: "Open"
        },
        {
          count: nonNegative(category.closed),
          color: colors.closed,
          label: "Closed"
        }
      ].forEach(function (series) {
        var bar = createElement("span", "octane-management-failure-bar");
        bar.style.setProperty("--octane-bar-color", series.color);
        bar.style.setProperty("--octane-bar-size", ((series.count / maximum) * 100) + "%");
        bar.title = series.label + ": " + series.count;
        bars.appendChild(bar);
      });
      var label = createElement("span", "octane-management-failure-label");
      label.textContent = category.label || "";
      button.appendChild(bars);
      button.appendChild(label);
      chart.appendChild(button);
    });
    setFailureYAxisLabels(yLabels, ticks, maximum);
    renderLegend(
        legendForPanel(panel),
        [
          {color: colors.open, label: "Open"},
          {color: colors.closed, label: "Closed"}
        ]);
    renderCategorySwitcher(switcher, categories, payload.totalDefects);
    renderFailureDetails(zone, colors);
  }

  function failureCategoryTotal(categories, totalDefects) {
    var reportedTotal = Number(totalDefects);
    if (Number.isFinite(reportedTotal) && reportedTotal >= 0) {
      return Math.floor(reportedTotal);
    }
    return categories.reduce(function (total, category) {
      return total + nonNegative(category.open) + nonNegative(category.closed);
    }, 0);
  }

  function renderCategorySwitcher(container, categories, totalDefects) {
    if (!container) {
      return;
    }
    var selected = container.getAttribute("data-selected-category") || "all";
    var available = categories.some(function (category) {
      return category.key === selected;
    });
    if (selected !== "all" && !available) {
      selected = "all";
      container.setAttribute("data-selected-category", selected);
    }
    clear(container);
    var allLabel = "All " + failureCategoryTotal(categories, totalDefects);
    [{key: "all", label: allLabel}].concat(categories).forEach(function (category) {
      var button = createElement("button", "octane-management-category-toggle");
      var key = category.key || "all";
      button.type = "button";
      button.setAttribute("data-management-category-filter", key);
      button.setAttribute("aria-pressed", String(key === selected));
      button.textContent = category.label || "All";
      container.appendChild(button);
    });
    bindCategoryScroller(container);
    scheduleCategoryScrollControls(container);
  }

  function categoryNavigationFor(container) {
    return container
      ? container.closest("[data-management-failure-tab-nav]")
      : null;
  }

  function updateCategoryScrollControls(container) {
    var navigation = categoryNavigationFor(container);
    if (!navigation) {
      return;
    }
    var previous = navigation.querySelector('[data-management-category-scroll="-1"]');
    var next = navigation.querySelector('[data-management-category-scroll="1"]');
    var navigationStyle = global.getComputedStyle(navigation);
    var columnGap = Number.parseFloat(navigationStyle.columnGap) || 0;
    var availableWithoutControls =
        Math.max(0, navigation.clientWidth - (columnGap * 2));
    var hasOverflow = container.scrollWidth > availableWithoutControls + 1;
    [previous, next].forEach(function (button) {
      if (button) {
        button.setAttribute("data-visible", String(hasOverflow));
      }
    });
    var maximum = Math.max(0, container.scrollWidth - container.clientWidth);
    if (previous) {
      previous.disabled = !hasOverflow || container.scrollLeft <= 1;
    }
    if (next) {
      next.disabled = !hasOverflow || container.scrollLeft >= maximum - 1;
    }
  }

  function scheduleCategoryScrollControls(container) {
    if (!container || container.__octaneCategoryScrollFrame) {
      return;
    }
    container.__octaneCategoryScrollFrame = global.requestAnimationFrame(function () {
      container.__octaneCategoryScrollFrame = null;
      updateCategoryScrollControls(container);
    });
  }

  function bindCategoryScroller(container) {
    if (!container || container.__octaneCategoryScrollerBound) {
      return;
    }
    container.__octaneCategoryScrollerBound = true;
    container.addEventListener("scroll", function () {
      scheduleCategoryScrollControls(container);
    }, {passive: true});
    if (typeof global.ResizeObserver === "function") {
      container.__octaneCategoryResizeObserver = new global.ResizeObserver(function () {
        scheduleCategoryScrollControls(container);
      });
      container.__octaneCategoryResizeObserver.observe(container);
    } else {
      global.addEventListener("resize", function () {
        scheduleCategoryScrollControls(container);
      }, {passive: true});
    }
  }

  function motionBehavior() {
    return global.matchMedia
        && global.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "auto"
      : "smooth";
  }

  function categoryElement(container, attributeName, category) {
    if (!container) {
      return null;
    }
    var elements = container.querySelectorAll("[" + attributeName + "]");
    for (var index = 0; index < elements.length; index += 1) {
      if (elements[index].getAttribute(attributeName) === category) {
        return elements[index];
      }
    }
    return null;
  }

  function scrollHorizontalItemIntoView(container, item) {
    if (!container || !item || container.scrollWidth <= container.clientWidth + 1) {
      return;
    }
    var containerBounds = container.getBoundingClientRect();
    var itemBounds = item.getBoundingClientRect();
    var nextScrollLeft = container.scrollLeft;
    if (itemBounds.left < containerBounds.left) {
      nextScrollLeft -= containerBounds.left - itemBounds.left;
    } else if (itemBounds.right > containerBounds.right) {
      nextScrollLeft += itemBounds.right - containerBounds.right;
    }
    nextScrollLeft = Math.max(
        0,
        Math.min(nextScrollLeft, container.scrollWidth - container.clientWidth));
    if (Math.abs(nextScrollLeft - container.scrollLeft) <= 1) {
      return;
    }
    if (typeof container.scrollTo === "function") {
      container.scrollTo({behavior: "auto", left: nextScrollLeft});
    } else {
      container.scrollLeft = nextScrollLeft;
    }
  }

  function revealFailureCategory(zone, category) {
    if (!zone || !category) {
      return;
    }
    var card = zone.querySelector('[data-card-key="test-management-failures"]');
    if (!card || !card.classList.contains("octane-expanded")) {
      return;
    }
    var chart = card.querySelector("[data-management-failure-bars]");
    var switcher = card.querySelector("[data-management-failure-switcher]");
    scrollHorizontalItemIntoView(
        chart,
        categoryElement(chart, "data-management-category", category));
    scrollHorizontalItemIntoView(
        switcher,
        categoryElement(switcher, "data-management-category-filter", category));
    scheduleCategoryScrollControls(switcher);
  }

  function scheduleFailureCategoryReveal(zone, category) {
    if (!zone || !category) {
      return;
    }
    if (zone.__octaneFailureCategoryRevealFrame
        && typeof global.cancelAnimationFrame === "function") {
      global.cancelAnimationFrame(zone.__octaneFailureCategoryRevealFrame);
    }
    if (typeof global.requestAnimationFrame !== "function") {
      revealFailureCategory(zone, category);
      return;
    }
    zone.__octaneFailureCategoryRevealFrame =
        global.requestAnimationFrame(function () {
          zone.__octaneFailureCategoryRevealFrame = null;
          revealFailureCategory(zone, category);
        });
  }

  function scrollCategorySwitcher(button) {
    var navigation = button
      ? button.closest("[data-management-failure-tab-nav]")
      : null;
    var container = navigation
      ? navigation.querySelector("[data-management-failure-switcher]")
      : null;
    if (!container) {
      return;
    }
    var direction = Number(button.getAttribute("data-management-category-scroll")) || 0;
    container.scrollBy({
      behavior: motionBehavior(),
      left: direction * Math.max(120, container.clientWidth * 0.72)
    });
  }

  function selectedCategory(zone) {
    var switcher = zone.querySelector("[data-management-failure-switcher]");
    return switcher ? switcher.getAttribute("data-selected-category") || "all" : "all";
  }

  function setSelectedCategory(zone, category) {
    var switcher = zone.querySelector("[data-management-failure-switcher]");
    if (!switcher) {
      return;
    }
    switcher.setAttribute("data-selected-category", category || "all");
    var buttons = switcher.querySelectorAll("[data-management-category-filter]");
    for (var index = 0; index < buttons.length; index += 1) {
      var selected =
          buttons[index].getAttribute("data-management-category-filter") === category;
      buttons[index].setAttribute("aria-pressed", String(selected));
      if (selected) {
        buttons[index].scrollIntoView({
          behavior: motionBehavior(),
          block: "nearest",
          inline: "nearest"
        });
      }
    }
    scheduleCategoryScrollControls(switcher);
    renderFailureDetails(zone);
  }

  function renderFailureDetails(zone, colors) {
    var payload = zone.__octaneTestManagementPayload || {};
    colors = colors || colorsFor(payload, zone);
    var category = selectedCategory(zone);
    var list = zone.querySelector("[data-management-defect-list]");
    if (!list) {
      return;
    }
    var defects = [];
    array(payload.failureCategories).forEach(function (entry) {
      if (category === "all" || entry.key === category) {
        defects = defects.concat(array(entry.defects));
      }
    });
    var sortState = defectSortState(list);
    list.setAttribute("data-sort-column", sortState.column);
    list.setAttribute("data-sort-direction", sortState.direction);
    defects = sortFailureDefects(defects, sortState.column, sortState.direction);
    clear(list);
    renderDefectTableHeader(list, sortState);
    list.setAttribute("aria-rowcount", String(defects.length + 1));
    if (!defects.length) {
      var empty = createElement("div", "octane-management-defect-empty");
      empty.setAttribute("role", "row");
      var emptyCell = createElement("span");
      emptyCell.setAttribute("role", "cell");
      emptyCell.setAttribute("aria-colspan", "4");
      emptyCell.textContent = "No defects in this category.";
      empty.appendChild(emptyCell);
      list.appendChild(empty);
      return;
    }
    defects.forEach(function (defect) {
      var row = createElement("div", "octane-management-defect-row");
      row.setAttribute("role", "row");
      var identifier = createElement("span", "octane-management-defect-id");
      identifier.setAttribute("role", "cell");
      identifier.textContent = defect.id ? "#" + defect.id : "N/A";
      var description = createElement("span", "octane-management-defect-description");
      description.setAttribute("role", "cell");
      description.textContent = defect.description || "Defect";
      var statusLabel = canonicalStatus(defect);
      var severityLabel = displayedSeverity(defect);
      var status = createElement(
          "span",
          "octane-management-defect-pill octane-management-defect-status");
      status.setAttribute("role", "cell");
      status.style.setProperty(
          "--octane-pill-color",
          statusLabel === "Open" ? colors.open : colors.closed);
      status.style.setProperty(
          "--octane-pill-text-color", emphasisTextColor(zone));
      status.textContent = statusLabel;
      status.setAttribute("aria-label", "Status: " + statusLabel);
      var severity = createElement(
          "span",
          "octane-management-defect-pill octane-management-defect-severity");
      severity.setAttribute("role", "cell");
      severity.style.setProperty(
          "--octane-pill-color",
          severityColor(
              defect.severityColorKey || defect.severity || severityLabel,
              zone));
      severity.style.setProperty(
          "--octane-pill-text-color", emphasisTextColor(zone));
      severity.textContent = severityLabel;
      severity.setAttribute("aria-label", "Severity: " + severityLabel);
      row.appendChild(identifier);
      row.appendChild(description);
      row.appendChild(status);
      row.appendChild(severity);
      list.appendChild(row);
    });
  }

  function renderMetrics(zone, payload) {
    var metricsRoot = zone.__octaneTestManagementMetricsRoot || zone;
    var grid = metricsRoot.querySelector("[data-management-metrics]");
    if (!grid) {
      return;
    }
    clear(grid);
    array(payload.metrics).forEach(function (metric) {
      var tile = createElement(
          "article",
          "octane-management-metric-tile octane-management-tone-" + (metric.tone || "neutral"));
      tile.setAttribute(
          "data-management-metric-key",
          String(metric.key || "").trim().toLowerCase());
      var title = createElement("h3", "octane-management-metric-title");
      title.textContent = metric.title || "";
      var value = createElement("div", "octane-management-metric-value");
      value.textContent = metric.value || "";
      var detail = createElement("div", "octane-management-metric-detail");
      detail.textContent = metric.detail || "";
      tile.appendChild(title);
      tile.appendChild(value);
      tile.appendChild(detail);
      if (array(metric.items).length) {
        var items = createElement("ul", "octane-management-metric-items");
        metric.items.forEach(function (item) {
          var row = createElement("li");
          var label = createElement("span");
          label.textContent = item.label || "";
          var itemValue = createElement(
              "strong", "octane-management-metric-item-value");
          if (item.primaryValue != null) {
            var primaryValue = createElement(
                "span", "octane-management-metric-item-primary");
            primaryValue.textContent = item.primaryValue || "";
            itemValue.appendChild(primaryValue);
            if (item.secondaryValue) {
              var separator = createElement(
                  "span", "octane-management-metric-item-separator");
              separator.setAttribute("aria-hidden", "true");
              separator.textContent = "|";
              var secondaryValue = createElement(
                  "span", "octane-management-metric-item-secondary");
              secondaryValue.textContent = item.secondaryValue;
              itemValue.appendChild(separator);
              itemValue.appendChild(secondaryValue);
            }
          } else {
            itemValue.textContent = item.value || "";
          }
          row.appendChild(label);
          row.appendChild(itemValue);
          items.appendChild(row);
        });
        tile.appendChild(items);
      }
      grid.appendChild(tile);
    });
  }

  function render(zone) {
    if (!zone) {
      return;
    }
    var payload = zone.__octaneTestManagementPayload || {};
    var colors = colorsFor(payload, zone);
    renderBurnDown(zone, payload, colors);
    renderCurrentState(zone, payload, colors);
    renderFailureAnalysis(zone, payload, colors);
    renderMetrics(zone, payload);
  }

  function scheduleRender(zone) {
    if (!zone || zone.__octaneTestManagementRenderFrame != null) {
      return;
    }
    var renderOnFrame = typeof global.requestAnimationFrame === "function"
        ? global.requestAnimationFrame.bind(global)
        : function (callback) {
          return global.setTimeout(callback, 16);
        };
    zone.__octaneTestManagementRenderFrame = renderOnFrame(function () {
      zone.__octaneTestManagementRenderFrame = null;
      if (zone.isConnected !== false) {
        render(zone);
      }
    });
  }

  function bindInteractions(zone) {
    if (!zone || zone.getAttribute("data-management-events-bound") === "true") {
      return;
    }
    zone.setAttribute("data-management-events-bound", "true");
    zone.addEventListener("click", function (event) {
      var defectSort = event.target.closest("[data-management-defect-sort]");
      if (defectSort && zone.contains(defectSort)) {
        updateDefectSort(zone, defectSort);
        return;
      }
      var categoryScroll = event.target.closest("[data-management-category-scroll]");
      if (categoryScroll && zone.contains(categoryScroll)) {
        scrollCategorySwitcher(categoryScroll);
        return;
      }
      var categoryBar = event.target.closest("[data-management-category]");
      if (categoryBar && zone.contains(categoryBar)) {
        var category = categoryBar.getAttribute("data-management-category") || "all";
        setSelectedCategory(zone, category);
        if (typeof zone.__octaneTestManagementOnCategorySelect === "function") {
          zone.__octaneTestManagementOnCategorySelect(categoryBar, category);
        }
        return;
      }
      var categoryToggle = event.target.closest("[data-management-category-filter]");
      if (categoryToggle && zone.contains(categoryToggle)) {
        setSelectedCategory(
            zone,
            categoryToggle.getAttribute("data-management-category-filter") || "all");
      }
    });
  }

  function mount(zone, payload, options) {
    if (!zone) {
      return;
    }
    zone.__octaneTestManagementPayload = payload || {};
    zone.__octaneTestManagementOnCategorySelect =
        options && options.onCategorySelect ? options.onCategorySelect : null;
    zone.__octaneTestManagementMetricsRoot =
        options && options.metricsRoot ? options.metricsRoot : zone;
    bindInteractions(zone);
    render(zone);
  }

  function update(zone, payload) {
    if (!zone) {
      return;
    }
    zone.__octaneTestManagementPayload = payload || {};
    scheduleRender(zone);
  }

  global.OctaneTestManagement = {
    failureAxisMaximum: failureAxisMaximum,
    integerAxisTicks: integerAxisTicks,
    mount: mount,
    render: render,
    revealFailureCategory: scheduleFailureCategoryReveal,
    setSelectedCategory: setSelectedCategory,
    sortFailureDefects: sortFailureDefects,
    timelineAxisScale: timelineAxisScale,
    update: update
  };
})(window);
