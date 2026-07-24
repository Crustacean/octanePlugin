(function (global) {
  "use strict";

  var SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  var DEFAULT_COLORS = {
    blocked: "#FF9F0A",
    closed: "#34C759",
    executed: "#4391F5",
    failed: "#FF453A",
    inProgress: "#4391F5",
    open: "#FF453A",
    passed: "#30D158",
    planned: "#8E8E93",
    skipped: "#BF5AF2"
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

  function pathFor(points, key, maximum, durationMillis) {
    if (!points.length) {
      return "";
    }
    return points.map(function (point, index) {
      var x = Math.min(1000, (point.elapsedMillis / durationMillis) * 1000);
      var y = 360 - (nonNegative(point[key]) / maximum) * 340;
      return (index === 0 ? "M " : " L ") + x.toFixed(2) + " " + y.toFixed(2);
    }).join("");
  }

  function setYAxisLabels(container, maximum) {
    clear(container);
    for (var index = 4; index >= 0; index -= 1) {
      var label = createElement("span", "octane-management-axis-value");
      label.textContent = String(Math.round((maximum * index) / 4));
      container.appendChild(label);
    }
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

  function renderBurnDown(zone, payload, colors) {
    var panel = zone.querySelector("[data-management-burndown]");
    if (!panel) {
      return;
    }
    var svg = panel.querySelector("svg");
    var yLabels = panel.querySelector("[data-management-y-labels]");
    var xLabels = panel.querySelector("[data-management-x-labels]");
    var points = normalizedPoints(payload);
    var maximum = chartMaximum(points, ["total", "executed", "passed"]);
    var duration = Math.max(1, nonNegative(payload.durationMillis));
    clear(svg);
    for (var gridIndex = 0; gridIndex < 5; gridIndex += 1) {
      var gridLine = createSvgElement("line", "octane-management-grid-line");
      var y = 20 + gridIndex * 85;
      gridLine.setAttribute("x1", "0");
      gridLine.setAttribute("x2", "1000");
      gridLine.setAttribute("y1", String(y));
      gridLine.setAttribute("y2", String(y));
      svg.appendChild(gridLine);
    }
    renderLineSeries(
        svg, points, "total", maximum, duration, colors.planned,
        "octane-management-line octane-management-line-planned");
    renderLineSeries(
        svg, points, "executed", maximum, duration, colors.executed,
        "octane-management-line octane-management-line-executed");
    renderLineSeries(
        svg, points, "passed", maximum, duration, colors.passed,
        "octane-management-line octane-management-line-passed");
    setYAxisLabels(yLabels, maximum);
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
    clear(plot);
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
        segment.style.setProperty("--octane-segment-size", ((count / maximum) * 100) + "%");
        segment.title = STATE_LABELS[key] + ": " + count;
        column.appendChild(segment);
      });
      plot.appendChild(column);
    });
    setYAxisLabels(yLabels, maximum);
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
    var maximum = 1;
    categories.forEach(function (category) {
      maximum = Math.max(maximum, nonNegative(category.open) + nonNegative(category.closed));
    });
    clear(chart);
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
    setYAxisLabels(yLabels, maximum);
    renderLegend(
        legendForPanel(panel),
        [
          {color: colors.open, label: "Open"},
          {color: colors.closed, label: "Closed"}
        ]);
    renderCategorySwitcher(switcher, categories);
    renderFailureDetails(zone, colors);
  }

  function renderCategorySwitcher(container, categories) {
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
    [{key: "all", label: "All"}].concat(categories).forEach(function (category) {
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
    clear(list);
    if (!defects.length) {
      var empty = createElement("li", "octane-management-defect-empty");
      empty.textContent = "No defects in this category.";
      list.appendChild(empty);
      return;
    }
    defects.forEach(function (defect) {
      var row = createElement("li", "octane-management-defect-row");
      var identifier = createElement("span", "octane-management-defect-id");
      identifier.textContent = defect.id ? "#" + defect.id : "N/A";
      var description = createElement("span", "octane-management-defect-description");
      description.textContent = defect.description || "Defect";
      var pills = createElement("span", "octane-management-defect-pills");
      var statusLabel = canonicalStatus(defect);
      var severityLabel = displayedSeverity(defect);
      var status = createElement("span", "octane-management-defect-pill");
      status.style.setProperty(
          "--octane-pill-color",
          statusLabel === "Open" ? colors.open : colors.closed);
      status.style.setProperty(
          "--octane-pill-text-color", emphasisTextColor(zone));
      status.textContent = statusLabel;
      status.setAttribute("aria-label", "Status: " + statusLabel);
      var severity = createElement("span", "octane-management-defect-pill");
      severity.style.setProperty(
          "--octane-pill-color",
          severityColor(
              defect.severityColorKey || defect.severity || severityLabel,
              zone));
      severity.style.setProperty(
          "--octane-pill-text-color", emphasisTextColor(zone));
      severity.textContent = severityLabel;
      severity.setAttribute("aria-label", "Severity: " + severityLabel);
      pills.appendChild(status);
      pills.appendChild(severity);
      row.appendChild(identifier);
      row.appendChild(description);
      row.appendChild(pills);
      list.appendChild(row);
    });
  }

  function renderMetrics(zone, payload) {
    var grid = zone.querySelector("[data-management-metrics]");
    if (!grid) {
      return;
    }
    clear(grid);
    array(payload.metrics).forEach(function (metric) {
      var tile = createElement(
          "article",
          "octane-management-metric-tile octane-management-tone-" + (metric.tone || "neutral"));
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
          var itemValue = createElement("strong");
          itemValue.textContent = item.value || "";
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

  function bindInteractions(zone) {
    if (!zone || zone.getAttribute("data-management-events-bound") === "true") {
      return;
    }
    zone.setAttribute("data-management-events-bound", "true");
    zone.addEventListener("click", function (event) {
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
    bindInteractions(zone);
    render(zone);
  }

  function update(zone, payload) {
    if (!zone) {
      return;
    }
    zone.__octaneTestManagementPayload = payload || {};
    render(zone);
  }

  global.OctaneTestManagement = {
    mount: mount,
    render: render,
    revealFailureCategory: scheduleFailureCategoryReveal,
    setSelectedCategory: setSelectedCategory,
    update: update
  };
})(window);
