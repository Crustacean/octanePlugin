(function (root, factory) {
  "use strict";
  var api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.OctaneScaleReport = api;
  }
})(typeof window !== "undefined" ? window : null, function () {
  "use strict";

  var MAX_VISIBLE_BARS = 80;
  var MIN_BAR_SLOT_PX = 10;
  var OVERFLOW_WIDTH_PX = 24;
  var SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  var DONUT_CENTER = 50;
  var DONUT_RADIUS = 46;
  var DONUT_LABEL_RADIUS = 52;
  var DONUT_CALLOUT_ORIGIN_RADIUS = 38;
  var DONUT_CALLOUT_LABEL_X = 104;
  var DONUT_CALLOUT_MIN_Y = -2;
  var DONUT_CALLOUT_MAX_Y = 102;
  var DONUT_CALLOUT_GAP = 8;
  var DONUT_THIN_SLICE_PERCENTAGE = 5;
  var mountedZones = typeof WeakMap === "function" ? new WeakMap() : null;

  function computeVisibleBarCount(width, totalBars) {
    var safeWidth = Math.max(0, Number(width) || 0);
    var safeTotal = Math.max(0, Number(totalBars) || 0);
    var capacity = Math.max(
        1, Math.floor(Math.max(0, safeWidth - OVERFLOW_WIDTH_PX) / MIN_BAR_SLOT_PX));
    return Math.min(MAX_VISIBLE_BARS, safeTotal, capacity);
  }

  function createElement(name, className, text) {
    var element = document.createElement(name);
    if (className) {
      element.className = className;
    }
    if (text !== undefined && text !== null) {
      element.textContent = String(text);
    }
    return element;
  }

  function createSvgElement(name, className) {
    var element = document.createElementNS(SVG_NAMESPACE, name);
    if (className) {
      element.setAttribute("class", className);
    }
    return element;
  }

  function appendText(parent, name, className, text) {
    var element = createElement(name, className, text);
    parent.appendChild(element);
    return element;
  }

  function appendSvgText(parent, className, value, x, y) {
    var text = createSvgElement("text", className);
    text.setAttribute("x", String(x));
    text.setAttribute("y", String(y));
    text.textContent = String(value);
    parent.appendChild(text);
    return text;
  }

  function donutPoint(angle, radius) {
    var radians = angle * Math.PI / 180;
    return {
      x: DONUT_CENTER + radius * Math.cos(radians),
      y: DONUT_CENTER + radius * Math.sin(radians)
    };
  }

  function donutNumber(value) {
    return Number(value).toFixed(3);
  }

  function donutPath(startAngle, endAngle) {
    var start = donutPoint(startAngle, DONUT_RADIUS);
    var end = donutPoint(endAngle, DONUT_RADIUS);
    var largeArc = endAngle - startAngle > 180 ? 1 : 0;
    return "M " + donutNumber(DONUT_CENTER) + " " + donutNumber(DONUT_CENTER)
        + " L " + donutNumber(start.x) + " " + donutNumber(start.y)
        + " A " + donutNumber(DONUT_RADIUS) + " " + donutNumber(DONUT_RADIUS)
        + " 0 " + largeArc + " 1 " + donutNumber(end.x) + " " + donutNumber(end.y)
        + " Z";
  }

  function donutLabelsOverlap(left, right) {
    var leftWidth = Math.max(8, left.percentageLabel.length * 2.45);
    var rightWidth = Math.max(8, right.percentageLabel.length * 2.45);
    return Math.abs(left.labelX - right.labelX) < (leftWidth + rightWidth) / 2 + 1
        && Math.abs(left.labelY - right.labelY) < 5;
  }

  function distributeDonutCallouts(placements) {
    placements.sort(function (left, right) {
      return left.y - right.y;
    });
    var nextY = DONUT_CALLOUT_MIN_Y;
    placements.forEach(function (placement) {
      placement.y = Math.max(placement.y, nextY);
      nextY = placement.y + DONUT_CALLOUT_GAP;
    });
    if (placements.length === 0) {
      return;
    }
    var overflow = placements[placements.length - 1].y - DONUT_CALLOUT_MAX_Y;
    if (overflow > 0) {
      placements.forEach(function (placement) {
        placement.y -= overflow;
      });
    }
    for (var index = placements.length - 2; index >= 0; index -= 1) {
      placements[index].y = Math.min(
          placements[index].y,
          placements[index + 1].y - DONUT_CALLOUT_GAP);
    }
    var underflow = DONUT_CALLOUT_MIN_Y - placements[0].y;
    if (underflow > 0) {
      placements.forEach(function (placement) {
        placement.y += underflow;
      });
    }
  }

  function computeDonutLabelLayout(statuses, total) {
    var safeTotal = Math.max(0, Number(total) || 0);
    if (safeTotal === 0) {
      return [];
    }
    var angle = -90;
    var slices = [];
    (statuses || []).forEach(function (status) {
      var count = Math.max(0, Number(status.count) || 0);
      if (count === 0) {
        return;
      }
      var percentage = count * 100 / safeTotal;
      var endAngle = angle + 360 * percentage / 100;
      var middleAngle = (angle + endAngle) / 2;
      var labelPoint = donutPoint(middleAngle, DONUT_LABEL_RADIUS);
      slices.push({
        callout: percentage < DONUT_THIN_SLICE_PERCENTAGE,
        endAngle: endAngle,
        fullCircle: percentage >= 99.999999,
        labelX: labelPoint.x,
        labelY: labelPoint.y,
        middleAngle: middleAngle,
        path: percentage >= 99.999999 ? "" : donutPath(angle, endAngle),
        percentage: percentage,
        percentageLabel: status.percentageLabel || percentage.toFixed(2) + "%",
        status: status,
        textAnchor: "middle"
      });
      angle = endAngle;
    });
    for (var left = 0; left < slices.length; left += 1) {
      for (var right = left + 1; right < slices.length; right += 1) {
        if (donutLabelsOverlap(slices[left], slices[right])) {
          slices[left].callout = true;
          slices[right].callout = true;
        }
      }
    }

    var leftPlacements = [];
    var rightPlacements = [];
    slices.forEach(function (slice, index) {
      if (!slice.callout) {
        return;
      }
      var placement = {index: index, y: slice.labelY};
      if (Math.cos(slice.middleAngle * Math.PI / 180) < 0) {
        leftPlacements.push(placement);
      } else {
        rightPlacements.push(placement);
      }
    });
    distributeDonutCallouts(leftPlacements);
    distributeDonutCallouts(rightPlacements);

    function applyCallouts(placements, rightSide) {
      placements.forEach(function (placement) {
        var slice = slices[placement.index];
        var leaderStart = donutPoint(slice.middleAngle, DONUT_CALLOUT_ORIGIN_RADIUS);
        slice.labelX = rightSide ? DONUT_CALLOUT_LABEL_X : 100 - DONUT_CALLOUT_LABEL_X;
        slice.labelY = placement.y;
        slice.leaderStartX = leaderStart.x;
        slice.leaderStartY = leaderStart.y;
        slice.leaderEndX = slice.labelX;
        slice.leaderEndY = slice.labelY;
        slice.textAnchor = rightSide ? "start" : "end";
      });
    }
    applyCallouts(leftPlacements, false);
    applyCallouts(rightPlacements, true);
    return slices;
  }

  function createCardActions() {
    var actions = createElement("div", "octane-card-actions");
    var expand = createElement("button", "octane-expand-toggle");
    expand.type = "button";
    expand.setAttribute("aria-label", "Expand widget");
    expand.setAttribute("aria-expanded", "false");
    expand.title = "Expand widget";
    var expandIcon = createSvgElement("svg", "octane-action-icon octane-icon-expand");
    expandIcon.setAttribute("viewBox", "0 0 24 24");
    expandIcon.setAttribute("aria-hidden", "true");
    ["M8 4H4v4", "M16 4h4v4", "M20 16v4h-4", "M8 20H4v-4"].forEach(
        function (pathValue) {
          var path = createSvgElement("path", "");
          path.setAttribute("d", pathValue);
          expandIcon.appendChild(path);
        });
    var collapseIcon = createSvgElement("svg", "octane-action-icon octane-icon-collapse");
    collapseIcon.setAttribute("viewBox", "0 0 24 24");
    collapseIcon.setAttribute("aria-hidden", "true");
    ["M9 3v6H3", "M3 9l6-6", "M15 21v-6h6", "M21 15l-6 6"].forEach(
        function (pathValue) {
          var path = createSvgElement("path", "");
          path.setAttribute("d", pathValue);
          collapseIcon.appendChild(path);
        });
    expand.appendChild(expandIcon);
    expand.appendChild(collapseIcon);
    actions.appendChild(expand);

    var move = createElement("button", "octane-card-tools");
    move.type = "button";
    move.setAttribute("aria-label", "Move widget");
    move.title = "Drag to move. Use arrow keys to reorder. Resize from the corner.";
    var moveIcon = createSvgElement("svg", "octane-grabber-icon");
    moveIcon.setAttribute("viewBox", "0 0 24 24");
    moveIcon.setAttribute("aria-hidden", "true");
    [5, 12, 19].forEach(function (cy) {
      var circle = createSvgElement("circle", "");
      circle.setAttribute("cx", "12");
      circle.setAttribute("cy", String(cy));
      circle.setAttribute("r", "2");
      moveIcon.appendChild(circle);
    });
    move.appendChild(moveIcon);
    actions.appendChild(move);
    return actions;
  }

  function createCard(section, kind) {
    var card = createElement("section", "octane-chart-card octane-deferred-chart-card");
    card.draggable = true;
    card.setAttribute("data-card-key", kind + "-" + section.source);
    card.setAttribute("data-octane-section", section.id);
    var header = createElement("div", "octane-card-header");
    var heading = createElement("div", "");
    appendText(
        heading,
        "h2",
        "octane-card-title",
        kind === "distribution" ? section.distributionTitle : section.barChartTitle);
    header.appendChild(heading);
    var actions = createCardActions();
    header.appendChild(actions);
    card.appendChild(header);
    return {actions: actions, card: card, heading: heading};
  }

  function appendLegend(heading, section, barChart) {
    var metadata = createElement(
        "div", barChart ? "octane-suite-chart-meta" : "octane-distribution-meta");
    appendText(
        metadata,
        "span",
        "octane-total-label",
        barChart
            ? "Total Suiteruns: " + section.suiteRunCount
            : "Total: " + section.metrics.total);
    var legend = createElement("div", "octane-legend");
    (section.totals || []).forEach(function (status) {
      if (Number(status.count) <= 0) {
        return;
      }
      var row = createElement("span", "octane-legend-row");
      var swatch = createElement("span", "octane-swatch");
      swatch.style.background = status.color;
      row.appendChild(swatch);
      appendText(row, "span", "", status.label);
      legend.appendChild(row);
    });
    metadata.appendChild(legend);
    heading.appendChild(metadata);
  }

  function renderDistribution(section) {
    var parts = createCard(section, "distribution");
    appendLegend(parts.heading, section, false);
    var wrap = createElement("div", "octane-donut-wrap");
    var svg = createSvgElement("svg", "octane-donut octane-client-donut");
    svg.setAttribute("viewBox", "-10 -10 120 120");
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", section.distributionTitle);
    var slices = computeDonutLabelLayout(
        section.totals || [], section.metrics && section.metrics.total);
    slices.forEach(function (slice) {
      var graphic = createSvgElement(
          slice.fullCircle ? "circle" : "path",
          slice.fullCircle ? "" : "octane-donut-segment");
      if (slice.fullCircle) {
        graphic.setAttribute("cx", "50");
        graphic.setAttribute("cy", "50");
        graphic.setAttribute("r", "46");
      } else {
        graphic.setAttribute("d", slice.path);
      }
      graphic.setAttribute("fill", slice.status.color);
      var title = createSvgElement("title", "");
      title.textContent = slice.status.label + ": " + slice.status.count
          + " (" + slice.percentageLabel + ")";
      graphic.appendChild(title);
      svg.appendChild(graphic);
    });
    var hole = createSvgElement("circle", "octane-donut-hole");
    hole.setAttribute("cx", "50");
    hole.setAttribute("cy", "50");
    hole.setAttribute("r", "30");
    svg.appendChild(hole);
    slices.forEach(function (slice) {
      if (!slice.callout) {
        return;
      }
      var line = createSvgElement("line", "octane-donut-callout-line");
      line.setAttribute("aria-hidden", "true");
      line.setAttribute("x1", donutNumber(slice.leaderStartX));
      line.setAttribute("y1", donutNumber(slice.leaderStartY));
      line.setAttribute("x2", donutNumber(slice.leaderEndX));
      line.setAttribute("y2", donutNumber(slice.leaderEndY));
      svg.appendChild(line);
    });
    slices.forEach(function (slice) {
      var label = appendSvgText(
          svg,
          "octane-donut-label" + (slice.callout ? " octane-donut-label-callout" : ""),
          slice.percentageLabel,
          donutNumber(slice.labelX),
          donutNumber(slice.labelY));
      label.setAttribute("data-label-mode", slice.callout ? "callout" : "radial");
      label.setAttribute("dominant-baseline", "central");
      label.setAttribute("text-anchor", slice.textAnchor);
    });
    wrap.appendChild(svg);
    parts.card.appendChild(wrap);
    parts.card.appendChild(distributionTable(section));
    return parts.card;
  }

  function distributionTable(section) {
    var table = createElement("table", "octane-chart-data-summary octane-visually-hidden");
    appendText(table, "caption", "", section.distributionTitle);
    var body = createElement("tbody", "");
    (section.totals || []).forEach(function (status) {
      if (Number(status.count) <= 0) {
        return;
      }
      var row = createElement("tr", "");
      var label = appendText(row, "th", "", status.label);
      label.setAttribute("scope", "row");
      appendText(row, "td", "", status.count);
      appendText(row, "td", "", status.percentageLabel);
      body.appendChild(row);
    });
    table.appendChild(body);
    return table;
  }

  function buildSectionUrl(baseUrl, sectionId, cursor, limit) {
    var separator = baseUrl.indexOf("?") >= 0 ? "&" : "?";
    return baseUrl
        + separator
        + "section="
        + encodeURIComponent(sectionId)
        + "&cursor="
        + encodeURIComponent(cursor)
        + "&limit="
        + encodeURIComponent(limit);
  }

  function fetchJson(url, checksum) {
    var headers = {Accept: "application/json"};
    if (checksum) {
      headers["If-None-Match"] = '"' + checksum + '"';
    }
    return window.fetch(url, {
      cache: "no-store",
      credentials: "same-origin",
      headers: headers
    }).then(function (response) {
      if (response.status === 304) {
        return null;
      }
      if (!response.ok) {
        throw new Error("Octane report data request failed: " + response.status);
      }
      return response.json();
    });
  }

  function statusByKey(bar, key) {
    var statuses = bar.statuses || [];
    for (var index = 0; index < statuses.length; index += 1) {
      if (statuses[index].key === key) {
        return statuses[index];
      }
    }
    return {count: 0, label: key, tooltipColor: ""};
  }

  function stampBarData(group, bar, cardKey) {
    group.setAttribute("data-card-key", cardKey);
    group.setAttribute("data-bar-key", bar.id);
    group.setAttribute("data-dominant-status-color", bar.dominantStatusColor || "");
    group.setAttribute("data-dominant-status-label", bar.dominantStatusLabel || "");
    ["passed", "failed", "blocked", "skipped", "running"].forEach(function (key) {
      var status = statusByKey(bar, key);
      group.setAttribute("data-status-" + key + "-count", String(status.count || 0));
      group.setAttribute("data-status-" + key + "-color", status.tooltipColor || "");
      group.setAttribute("data-status-" + key + "-label", status.label || key);
    });
    group.setAttribute("data-bar-name", bar.name || "");
    group.setAttribute("data-bar-total", String(bar.total || 0));
  }

  function renderBarChart(card, section, page) {
    var old = card.querySelector("[data-client-bar-content]");
    if (old) {
      old.remove();
    }
    var content = createElement("div", "octane-client-bar-content");
    content.setAttribute("data-client-bar-content", "true");
    var svg = createSvgElement("svg", "octane-client-bar-chart");
    svg.setAttribute("viewBox", "0 0 1000 300");
    svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", section.barChartTitle);
    var plotLeft = 52;
    var plotTop = 10;
    var plotBottom = 252;
    var chartWidth = Math.max(320, card.clientWidth || 700);
    var pageCursor = Math.max(0, Number(page.cursor) || 0);
    var hiddenCount = Math.max(
        0, Number(page.totalBars) - pageCursor - (page.bars || []).length);
    var overflowSvgWidth = hiddenCount > 0 ? OVERFLOW_WIDTH_PX * 1000 / chartWidth : 0;
    var plotRight = 988 - overflowSvgWidth;
    var plotWidth = Math.max(1, plotRight - plotLeft);
    var maximum = Math.max(1, Number(section.maxTotal) || 1);

    (section.yAxisTicks || []).forEach(function (tick) {
      var y = plotBottom - Number(tick) / maximum * (plotBottom - plotTop);
      var grid = createSvgElement("line", "octane-client-grid-line");
      grid.setAttribute("x1", String(plotLeft));
      grid.setAttribute("x2", String(plotRight));
      grid.setAttribute("y1", String(y));
      grid.setAttribute("y2", String(y));
      svg.appendChild(grid);
      appendSvgText(svg, "octane-client-axis-value", tick, plotLeft - 8, y + 4);
    });
    var axis = createSvgElement("line", "octane-client-axis-line");
    axis.setAttribute("x1", String(plotLeft));
    axis.setAttribute("x2", String(plotRight));
    axis.setAttribute("y1", String(plotBottom));
    axis.setAttribute("y2", String(plotBottom));
    svg.appendChild(axis);

    var bars = page.bars || [];
    var slotWidth = bars.length > 0 ? plotWidth / bars.length : plotWidth;
    var slotWidthPx = slotWidth * chartWidth / 1000;
    var barWidthPx = Math.min(100, Math.max(8, slotWidthPx * 0.72));
    var barWidth = barWidthPx * 1000 / chartWidth;
    var labelEvery = Math.max(1, Math.ceil(54 / Math.max(1, slotWidthPx)));
    bars.forEach(function (bar, barIndex) {
      var center = plotLeft + slotWidth * (barIndex + 0.5);
      var group = createSvgElement("g", "octane-suite-column octane-client-suite-column");
      group.setAttribute("tabindex", "0");
      group.setAttribute("role", "img");
      group.setAttribute("aria-label", bar.title || bar.name || "Tester bar");
      stampBarData(group, bar, "bars-" + section.source);
      var currentBottom = plotBottom;
      var hitHeight = Math.max(
          0.5, Number(bar.total) / maximum * (plotBottom - plotTop));
      var hitTarget = createSvgElement(
          "rect", "octane-vertical-bar octane-client-bar-hit-target");
      hitTarget.setAttribute("x", String(center - barWidth / 2));
      hitTarget.setAttribute("y", String(plotBottom - hitHeight));
      hitTarget.setAttribute("width", String(barWidth));
      hitTarget.setAttribute("height", String(hitHeight));
      hitTarget.setAttribute("fill", "transparent");
      (bar.statuses || []).forEach(function (status) {
        if (Number(status.count) <= 0) {
          return;
        }
        var height = Number(status.count) / maximum * (plotBottom - plotTop);
        var segment = createSvgElement("rect", "octane-client-bar-segment");
        segment.setAttribute("x", String(center - barWidth / 2));
        segment.setAttribute("y", String(currentBottom - height));
        segment.setAttribute("width", String(barWidth));
        segment.setAttribute("height", String(Math.max(0.5, height)));
        segment.setAttribute("fill", status.color);
        group.appendChild(segment);
        currentBottom -= height;
      });
      if (barIndex % labelEvery === 0 || bars.length <= 12) {
        var label = appendSvgText(
            group, "octane-client-axis-label", bar.axisLabel || bar.name || "", center, 277);
        label.setAttribute("text-anchor", "middle");
      }
      // Keep the transparent hit area above the painted segments so delegated pointer events
      // consistently target the bar in every browser.
      group.appendChild(hitTarget);
      svg.appendChild(group);
    });

    if (hiddenCount > 0) {
      var overflowStart = plotRight;
      var overflowEnd = Math.min(998, overflowStart + overflowSvgWidth);
      var overflow = createSvgElement("line", "octane-client-overflow-line");
      overflow.setAttribute("x1", String(overflowStart));
      overflow.setAttribute("x2", String(overflowEnd));
      overflow.setAttribute("y1", String(plotBottom));
      overflow.setAttribute("y2", String(plotBottom));
      svg.appendChild(overflow);
      var overflowLabel = appendSvgText(
          svg, "octane-client-overflow-label", "+" + hiddenCount, overflowEnd, 277);
      overflowLabel.setAttribute("text-anchor", "end");
    }
    content.appendChild(svg);
    content.appendChild(barSummaryTable(section, bars));
    card.appendChild(content);
    card.setAttribute("data-octane-loaded", "true");
  }

  function barSummaryTable(section, bars) {
    var table = createElement("table", "octane-chart-data-summary octane-visually-hidden");
    appendText(table, "caption", "", section.barChartTitle + " visible data");
    var body = createElement("tbody", "");
    bars.forEach(function (bar) {
      (bar.statuses || []).forEach(function (status) {
        if (Number(status.count) <= 0) {
          return;
        }
        var row = createElement("tr", "");
        var name = appendText(row, "th", "", bar.name);
        name.setAttribute("scope", "row");
        appendText(row, "td", "", status.label);
        appendText(row, "td", "", status.count);
        appendText(row, "td", "", status.percentageLabel);
        appendText(row, "td", "", bar.total);
        body.appendChild(row);
      });
    });
    table.appendChild(body);
    return table;
  }

  function renderBarCard(section, state) {
    var parts = createCard(section, "bars");
    appendLegend(parts.heading, section, true);
    var pageStatus = createElement("span", "octane-visually-hidden");
    pageStatus.setAttribute("aria-live", "polite");
    parts.heading.appendChild(pageStatus);
    var previous = createPageButton("previous", "Previous tester bars", "M15 18l-6-6 6-6");
    var next = createPageButton("next", "Next tester bars", "M9 18l6-6-6-6");
    parts.actions.insertBefore(previous, parts.actions.firstChild);
    parts.actions.insertBefore(next, parts.actions.children[1] || parts.actions.firstChild);
    var loading = createElement("div", "octane-client-chart-loading", "Loading chart data...");
    loading.setAttribute("role", "status");
    parts.card.appendChild(loading);
    var lastLimit = 0;
    var currentCursor = 0;
    var currentPage = null;
    var loadingRequest = false;
    var requestGeneration = 0;
    function load(cursor) {
      var limit = computeVisibleBarCount(parts.card.clientWidth || 700, section.barCount);
      var safeCursor = Math.max(0, Number(cursor) || 0);
      if (loadingRequest
          || (limit === lastLimit
              && safeCursor === currentCursor
              && parts.card.hasAttribute("data-octane-loaded"))) {
        return;
      }
      loadingRequest = true;
      var generation = ++requestGeneration;
      parts.card.setAttribute("aria-busy", "true");
      previous.disabled = true;
      next.disabled = true;
      fetchJson(buildSectionUrl(state.dataUrl, section.id, safeCursor, limit), "")
          .then(function (page) {
            if (generation !== requestGeneration || !parts.card.isConnected) {
              return;
            }
            loadingRequest = false;
            parts.card.setAttribute("aria-busy", "false");
            if (!page) {
              return;
            }
            lastLimit = limit;
            currentCursor = Math.max(0, Number(page.cursor) || 0);
            currentPage = page;
            loading.remove();
            renderBarChart(parts.card, section, page);
            updatePageControls();
          })
          .catch(function () {
            if (generation !== requestGeneration || !parts.card.isConnected) {
              return;
            }
            loadingRequest = false;
            parts.card.setAttribute("aria-busy", "false");
            if (!loading.isConnected) {
              parts.card.appendChild(loading);
            }
            loading.textContent = "Chart data is temporarily unavailable.";
            updatePageControls();
          });
    }
    function updatePageControls() {
      var total = currentPage ? Math.max(0, Number(currentPage.totalBars) || 0) : 0;
      var shown = currentPage && currentPage.bars ? currentPage.bars.length : 0;
      previous.hidden = total <= shown;
      next.hidden = total <= shown;
      previous.disabled = loadingRequest || currentCursor <= 0;
      next.disabled =
          loadingRequest || !currentPage || Number(currentPage.nextCursor) < 0;
      if (shown > 0) {
        pageStatus.textContent =
            "Showing tester bars "
            + (currentCursor + 1)
            + " to "
            + (currentCursor + shown)
            + " of "
            + total;
      } else {
        pageStatus.textContent = "No tester bars available";
      }
    }
    previous.addEventListener("click", function () {
      load(Math.max(0, currentCursor - Math.max(1, lastLimit)));
    });
    next.addEventListener("click", function () {
      if (currentPage && Number(currentPage.nextCursor) >= 0) {
        load(Number(currentPage.nextCursor));
      }
    });
    updatePageControls();
    if (typeof IntersectionObserver === "function") {
      var observer = new IntersectionObserver(function (entries) {
        if (entries.some(function (entry) { return entry.isIntersecting; })) {
          load(currentCursor);
          observer.disconnect();
        }
      }, {rootMargin: "240px 0px"});
      observer.observe(parts.card);
      trackCleanup(state, function () { observer.disconnect(); });
    } else {
      load(currentCursor);
    }
    if (typeof ResizeObserver === "function") {
      var resizeTimer = 0;
      var resizeObserver = new ResizeObserver(function () {
        window.clearTimeout(resizeTimer);
        resizeTimer = window.setTimeout(function () { load(currentCursor); }, 80);
      });
      resizeObserver.observe(parts.card);
      trackCleanup(state, function () {
        window.clearTimeout(resizeTimer);
        resizeObserver.disconnect();
      });
    }
    return parts.card;
  }

  function createPageButton(direction, label, pathValue) {
    var button = createElement("button", "octane-client-page-button");
    button.type = "button";
    button.setAttribute("aria-label", label);
    button.title = label;
    button.setAttribute("data-page-direction", direction);
    var icon = createSvgElement("svg", "octane-action-icon");
    icon.setAttribute("viewBox", "0 0 24 24");
    icon.setAttribute("aria-hidden", "true");
    var path = createSvgElement("path", "");
    path.setAttribute("d", pathValue);
    icon.appendChild(path);
    button.appendChild(icon);
    return button;
  }

  function trackCleanup(state, cleanup) {
    if (!state.cleanups) {
      state.cleanups = [];
    }
    state.cleanups.push(cleanup);
  }

  function disposeState(state) {
    (state.cleanups || []).forEach(function (cleanup) {
      cleanup();
    });
    state.cleanups = [];
  }

  function renderIndex(zone, payload, state) {
    disposeState(state);
    var fragment = document.createDocumentFragment();
    (payload.sections || []).forEach(function (section) {
      fragment.appendChild(renderDistribution(section));
      fragment.appendChild(renderBarCard(section, state));
    });
    if (!payload.sections || payload.sections.length === 0) {
      var card = createElement("section", "octane-chart-card");
      card.setAttribute("data-card-key", "suite-run-empty");
      appendText(card, "h2", "octane-card-title", "Suite run charts");
      appendText(card, "div", "octane-empty", payload.message || "No report data yet.");
      fragment.appendChild(card);
    }
    zone.replaceChildren(fragment);
    zone.setAttribute("data-report-schema-version", String(payload.schemaVersion || 0));
    zone.setAttribute("data-report-client-ready", "true");
  }

  function mount(zone, dataUrl, checksum) {
    if (!zone || !dataUrl || typeof window === "undefined" || !window.fetch) {
      return Promise.resolve(false);
    }
    var state = mountedZones ? mountedZones.get(zone) : zone.__octaneScaleState;
    if (!state) {
      state = {checksum: "", cleanups: [], dataUrl: dataUrl, generation: 0, promise: null};
      if (mountedZones) {
        mountedZones.set(zone, state);
      } else {
        zone.__octaneScaleState = state;
      }
    }
    state.dataUrl = dataUrl;
    if (state.promise && state.checksum === checksum) {
      return state.promise;
    }
    state.checksum = checksum || "";
    var generation = ++state.generation;
    zone.setAttribute("aria-busy", "true");
    state.promise = fetchJson(dataUrl, "")
        .then(function (payload) {
          if (generation !== state.generation) {
            return false;
          }
          if (payload) {
            renderIndex(zone, payload, state);
          }
          zone.setAttribute("aria-busy", "false");
          return true;
        })
        .catch(function () {
          if (generation !== state.generation) {
            return false;
          }
          zone.setAttribute("aria-busy", "false");
          zone.setAttribute("data-report-client-error", "true");
          return false;
        });
    if (typeof window !== "undefined") {
      window.__octaneReportReady = state.promise;
    }
    return state.promise;
  }

  return {
    MAX_VISIBLE_BARS: MAX_VISIBLE_BARS,
    OVERFLOW_WIDTH_PX: OVERFLOW_WIDTH_PX,
    computeDonutLabelLayout: computeDonutLabelLayout,
    computeVisibleBarCount: computeVisibleBarCount,
    mount: mount
  };
});
