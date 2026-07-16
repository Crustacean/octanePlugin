package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGatePieSlice;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSection;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateStatusCount;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateSuiteRunChart;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import java.util.List;

public class OctaneReportZoneHtmlRenderer {
  static final int DEFAULT_VIEWPORT_WIDTH = 1400;
  static final int BAR_SLOT_WIDTH_PX = 10;
  static final int OVERFLOW_INDICATOR_WIDTH_PX = 24;
  static final int EMAIL_SINGLE_COLUMN_BREAKPOINT_PX = 840;
  static final int EMAIL_SINGLE_COLUMN_CHROME_PX = 164;
  static final int EMAIL_TWO_COLUMN_CHROME_PX = 139;

  public String render(OctaneGateReportSnapshot snapshot) {
    return render(snapshot, OctaneReportTheme.LIGHT.name(), DEFAULT_VIEWPORT_WIDTH);
  }

  public String render(OctaneGateReportSnapshot snapshot, String theme) {
    return render(snapshot, theme, DEFAULT_VIEWPORT_WIDTH);
  }

  public String render(OctaneGateReportSnapshot snapshot, String theme, int viewportWidth) {
    OctaneGateReportSnapshot safeSnapshot =
        snapshot == null ? OctaneGateReportSnapshot.empty() : snapshot;
    OctaneReportTheme reportTheme = OctaneReportTheme.from(theme);
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html>\n");
    html.append("<html data-octane-theme=\"");
    html.append(reportTheme.getHtmlValue());
    html.append("\">\n");
    html.append("<head>\n");
    html.append("<meta charset=\"UTF-8\" />\n");
    html.append("<meta name=\"color-scheme\" content=\"");
    html.append(reportTheme.getColorSchemeContent());
    html.append("\" />\n");
    html.append("<title>Octane Gate Report</title>\n");
    appendStyle(html);
    html.append("</head>\n");
    html.append("<body>\n");
    renderReportZone(html, safeSnapshot, maxVisibleBars(emailBarChartWidth(viewportWidth)));
    html.append("</body>\n");
    html.append("</html>\n");
    return html.toString();
  }

  public String renderZone(OctaneGateReportSnapshot snapshot) {
    OctaneGateReportSnapshot safeSnapshot =
        snapshot == null ? OctaneGateReportSnapshot.empty() : snapshot;
    StringBuilder html = new StringBuilder();
    renderReportZone(html, safeSnapshot, null);
    return html.toString();
  }

  private void appendStyle(StringBuilder html) {
    html.append("<style>\n");
    html.append(
        """
        :root {
          color-scheme: light;
          --octane-axis-line: #30363D;
          --octane-axis-text: #827C7B;
          --octane-bar-track: #e6ebf2;
          --octane-border: #d7dde6;
          --octane-card-background: #ffffff;
          --octane-card-shadow: rgba(0, 0, 0, 0.14);
          --octane-grid-dot: rgba(33, 38, 45, 0.92);
          --octane-muted-text: #5f6b7a;
          --octane-page-background: #f5f7fb;
          --octane-popup-background: #ffffff;
          --octane-popup-shadow: rgba(0, 0, 0, 0.18);
          --octane-status-blocked: #FF9500;
          --octane-status-failed: #FF3B30;
          --octane-status-no-run: #8E8E93;
          --octane-status-passed: #34C759;
          --octane-status-skipped: #AF52DE;
          --octane-text: #1f2937;
          --octane-tool-color: #8a94a6;
        }
        :root[data-octane-theme="dark"] {
          color-scheme: dark;
          --octane-axis-line: #576779;
          --octane-axis-text: #a8b2c3;
          --octane-bar-track: #30363d;
          --octane-border: #30363d;
          --octane-card-background: #1b1e24;
          --octane-card-shadow: rgba(0, 0, 0, 0.34);
          --octane-grid-dot: rgba(87, 103, 121, 0.7);
          --octane-muted-text: #9aa7bd;
          --octane-page-background: #181a20;
          --octane-popup-background: #22262e;
          --octane-popup-shadow: rgba(0, 0, 0, 0.42);
          --octane-status-blocked: #FF9F0A;
          --octane-status-failed: #FF453A;
          --octane-status-no-run: #8E8E93;
          --octane-status-passed: #30D158;
          --octane-status-skipped: #BF5AF2;
          --octane-text: #f3f6fb;
          --octane-tool-color: #9aa7bd;
        }
        :root[data-octane-theme="system"] {
          color-scheme: light dark;
        }
        @media (prefers-color-scheme: dark) {
          :root[data-octane-theme="system"] {
            --octane-axis-line: #576779;
            --octane-axis-text: #a8b2c3;
            --octane-bar-track: #30363d;
            --octane-border: #30363d;
            --octane-card-background: #1b1e24;
            --octane-card-shadow: rgba(0, 0, 0, 0.34);
            --octane-grid-dot: rgba(87, 103, 121, 0.7);
            --octane-muted-text: #9aa7bd;
            --octane-page-background: #181a20;
            --octane-popup-background: #22262e;
            --octane-popup-shadow: rgba(0, 0, 0, 0.42);
            --octane-status-blocked: #FF9F0A;
            --octane-status-failed: #FF453A;
            --octane-status-no-run: #8E8E93;
            --octane-status-passed: #30D158;
            --octane-status-skipped: #BF5AF2;
            --octane-text: #f3f6fb;
            --octane-tool-color: #9aa7bd;
          }
        }
        @supports (background: oklch(0.17 0.01 265 / 1)) {
          :root[data-octane-theme="dark"] {
            --octane-page-background: oklch(0.17 0.01 265 / 1);
          }
          @media (prefers-color-scheme: dark) {
            :root[data-octane-theme="system"] {
              --octane-page-background: oklch(0.17 0.01 265 / 1);
            }
          }
        }
        * {
          box-sizing: border-box;
        }
        body {
          background: var(--octane-page-background);
          color: var(--octane-text);
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          margin: 0;
          padding: 16px;
        }
        .octane-report-zone {
          align-items: stretch;
          border: 1px solid var(--octane-page-background);
          border-radius: 8px;
          display: flex;
          flex-wrap: wrap;
          gap: 16px;
          padding: 16px;
          width: 100%;
        }
        .octane-chart-card {
          background: var(--octane-card-background);
          border: 1px solid var(--octane-border);
          border-radius: 14px;
          box-shadow: 0 1px 7px var(--octane-card-shadow);
          flex: 0 1 calc(50% - 8px);
          min-height: 310px;
          min-width: 360px;
          overflow-x: hidden;
          overflow-y: hidden;
          padding: 16px;
          width: calc(50% - 8px);
        }
        .octane-card-header {
          align-items: center;
          display: flex;
          gap: 12px;
          justify-content: space-between;
          margin-bottom: 12px;
        }
        .octane-card-title {
          font-size: 16px;
          font-weight: 600;
          margin: 0;
        }
        .octane-muted {
          color: var(--octane-muted-text);
        }
        .octane-visually-hidden:where(:not(:focus-within, :active)) {
          border: 0 !important;
          clip-path: inset(50%) !important;
          height: 1px !important;
          margin: -1px !important;
          overflow: hidden !important;
          padding: 0 !important;
          position: absolute !important;
          white-space: nowrap !important;
          width: 1px !important;
        }
        .octane-card-tools {
          align-items: center;
          appearance: none;
          background: transparent;
          border: 0;
          border-radius: 4px;
          color: var(--octane-tool-color);
          cursor: grab;
          display: inline-flex;
          height: 18px;
          justify-content: center;
          padding: 0;
          user-select: none;
          width: 18px;
        }
        .octane-grabber-icon {
          display: block;
          fill: currentColor;
          height: 14px;
          width: 14px;
        }
        .octane-donut-wrap {
          align-items: center;
          display: flex;
          gap: 12px;
          justify-content: center;
          padding: 12px 24px;
        }
        .octane-donut {
          display: block;
          height: auto;
          justify-self: center;
          max-width: 280px;
          overflow: visible;
          width: 100%;
        }
        .octane-donut-label {
          fill: var(--octane-text);
          font-size: 4.5px;
          font-weight: 700;
          pointer-events: none;
          text-anchor: middle;
        }
        .octane-donut-hole {
          fill: var(--octane-card-background);
        }
        .octane-distribution-meta {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          gap: 6px 12px;
          margin-top: 4px;
        }
        .octane-total-label {
          color: var(--octane-muted-text);
        }
        .octane-suite-chart-meta {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          gap: 6px 12px;
          margin-top: 4px;
        }
        .octane-axis-value {
          color: var(--octane-axis-text);
          font-family: Inter, "Segoe UI", Arial, sans-serif;
          font-size: 12px;
          font-weight: 400;
        }
        .octane-bar-graph {
          --octane-axis-label-row: 27px;
          box-sizing: border-box;
          column-gap: 1px;
          display: grid;
          grid-template-columns: 22px max-content minmax(0, 1fr);
          grid-template-rows: 260px var(--octane-axis-label-row);
          margin-top: 16px;
          row-gap: 0;
          width: 100%;
        }
        .octane-y-axis-label {
          align-self: center;
          color: var(--octane-axis-text);
          font-family: Inter, "Segoe UI", Arial, sans-serif;
          font-size: 12px;
          font-weight: 400;
          grid-column: 1;
          grid-row: 1;
          justify-self: center;
          transform: rotate(180deg);
          white-space: nowrap;
          writing-mode: vertical-rl;
        }
        .octane-y-axis-scale {
          align-items: end;
          display: flex;
          flex-direction: column;
          grid-column: 2;
          grid-row: 1;
          justify-content: space-between;
          min-width: 0;
          padding-right: 3px;
        }
        .octane-bar-plot {
          box-sizing: border-box;
          grid-column: 3;
          grid-row: 1 / span 2;
          min-width: 0;
          overflow: visible;
          position: relative;
        }
        .octane-bar-plot::before {
          background-image: radial-gradient(
            circle,
            var(--octane-grid-dot) 0 1px,
            transparent 1.2px
          );
          background-size: 9px calc(100% / var(--octane-grid-line-count, 4));
          bottom: var(--octane-axis-label-row);
          content: "";
          left: 0;
          pointer-events: none;
          position: absolute;
          right: 0;
          top: 0;
          z-index: 0;
        }
        .octane-bar-plot::after {
          background: var(--octane-axis-line);
          bottom: var(--octane-axis-label-row);
          content: "";
          height: 1px;
          left: 0;
          pointer-events: none;
          position: absolute;
          right: 0;
          z-index: 0;
        }
        .octane-legend {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          gap: 6px 10px;
        }
        .octane-legend-row {
          align-items: center;
          display: inline-flex;
          column-gap: 6px;
          white-space: nowrap;
        }
        .octane-swatch {
          border-radius: 2px;
          display: inline-block;
          height: 13px;
          width: 13px;
        }
        .octane-vertical-bars {
          align-items: stretch;
          box-sizing: border-box;
          display: flex;
          gap: clamp(2px, 1vw, 16px);
          height: 100%;
          justify-content: center;
          min-height: 0;
          overflow-x: hidden;
          overflow-y: visible;
          padding: 0 clamp(2px, 0.7vw, 8px);
          width: 100%;
        }
        .octane-vertical-bars.octane-fluid-bars-dense {
          gap: 0;
          justify-content: flex-start;
          padding: 0;
        }
        .octane-fluid-bars-dense .octane-suite-column {
          flex: 0 0 8px;
          margin-right: 2px;
          max-width: 8px;
          min-width: 8px;
          width: 8px;
        }
        .octane-fluid-bars-dense .octane-vertical-bar {
          display: flex;
          margin-right: 0;
          max-width: 8px;
          min-width: 8px;
          vertical-align: bottom;
          width: 8px;
        }
        .octane-bar-overflow-indicator {
          align-self: stretch;
          box-sizing: border-box;
          display: grid;
          flex: 1 0 24px;
          grid-template-rows: minmax(0, 1fr) var(--octane-axis-label-row);
          height: 100%;
          min-width: 24px;
          position: relative;
          z-index: 2;
        }
        .octane-bar-overflow-line {
          align-self: end;
          background: var(--background, var(--octane-card-background));
          border-bottom: 2px dashed #666;
          box-sizing: border-box;
          grid-row: 1;
          height: 3px;
          width: 100%;
        }
        .octane-bar-overflow-count {
          align-self: start;
          color: #888;
          font-size: 10px;
          grid-row: 2;
          justify-self: end;
          line-height: 1.1;
          padding-top: 3px;
          white-space: nowrap;
        }
        .octane-vertical-bars::before,
        .octane-vertical-bars::after {
          content: "";
          display: none;
        }
        .octane-suite-column {
          align-items: center;
          display: grid;
          flex: 1 1 74px;
          grid-template-rows: minmax(0, 1fr) var(--octane-axis-label-row);
          height: 100%;
          justify-items: center;
          max-width: 83px;
          min-width: 0;
          position: relative;
        }
        .octane-vertical-bar-wrap {
          align-items: end;
          display: flex;
          grid-row: 1;
          height: 100%;
          justify-content: center;
          min-height: 0;
          width: 100%;
          z-index: 1;
        }
        .octane-vertical-bar {
          align-items: stretch;
          align-self: end;
          background: var(--octane-bar-track);
          border-radius: 0;
          display: flex;
          flex-direction: column-reverse;
          height: 100%;
          overflow: hidden;
          width: min(clamp(18.2px, 80.6%, 54.6px), calc(100% - 2px));
        }
        .octane-bar-popup {
          background: var(--octane-popup-background);
          border: 1px solid var(--octane-popup-border-color, var(--octane-border));
          border-radius: 8px;
          box-shadow:
              0 0 0 1px var(--octane-popup-border-color, transparent),
              0 7px 22px var(--octane-popup-shadow);
          color: var(--octane-text);
          display: grid;
          font-size: 10.35px;
          gap: 5px;
          left: 50%;
          min-width: 175px;
          opacity: 0;
          padding: 8px 10px;
          pointer-events: none;
          position: absolute;
          top: 25px;
          transform: translate(-50%, 6px);
          visibility: hidden;
          z-index: 5;
        }
        .octane-suite-column:hover .octane-bar-popup,
        .octane-suite-column:focus-within .octane-bar-popup {
          opacity: 1;
          transform: translate(-50%, 0);
          visibility: visible;
        }
        .octane-bar-popup-name {
          border-bottom: 1px solid var(--octane-border);
          font-weight: 700;
          line-height: 1.25;
          overflow-wrap: anywhere;
          padding-bottom: 5px;
          white-space: normal;
        }
        .octane-bar-popup-row {
          align-items: center;
          display: grid;
          gap: 5px;
          grid-template-columns: 9px minmax(0, 1fr) auto auto;
        }
        .octane-bar-popup .octane-swatch {
          height: 9px;
          width: 9px;
        }
        .octane-bar-popup-label {
          overflow-wrap: anywhere;
          white-space: normal;
        }
        .octane-bar-popup-value,
        .octane-bar-popup-percent,
        .octane-bar-popup-total-value {
          font-weight: 700;
          text-align: right;
        }
        .octane-bar-popup-total {
          border-top: 1px solid var(--octane-border);
          display: flex;
          justify-content: space-between;
          padding-top: 5px;
        }
        .octane-vertical-segment {
          display: block;
          width: 100%;
        }
        .octane-suite-label {
          align-self: start;
          box-sizing: border-box;
          display: block;
          color: var(--octane-axis-text);
          font-family: Inter, "Segoe UI", Arial, sans-serif;
          font-size: 12px;
          font-weight: 400;
          grid-row: 2;
          line-height: 1.1;
          max-width: min(100%, 109px);
          min-height: 16px;
          overflow: hidden;
          padding-top: 3px;
          text-align: center;
          text-overflow: ellipsis;
          transform: none;
          transform-origin: center;
          white-space: nowrap;
          width: 100%;
        }
        .octane-empty {
          border: 1px dashed var(--octane-border);
          border-radius: 8px;
          color: var(--octane-muted-text);
          padding: 16px;
        }
        @media (max-width: 840px) {
          .octane-chart-card {
            flex-basis: 100%;
            min-width: 280px;
            width: 100%;
          }
        }
        """);
    html.append("</style>\n");
  }

  private void renderReportZone(
      StringBuilder html, OctaneGateReportSnapshot snapshot, Integer maxVisibleBars) {
    html.append("<div class=\"octane-report-zone octane-card-zone\" id=\"octane-report-zone\">\n");
    if (snapshot.hasReportSections()) {
      for (OctaneGateReportSection section : snapshot.getReportSections()) {
        renderDistributionCard(html, section);
        renderSuiteRunCard(html, section, maxVisibleBars);
      }
    } else if (!snapshot.hasSections()) {
      html.append("<section class=\"octane-chart-card\" draggable=\"true\" ");
      html.append("data-card-key=\"suite-run-empty\">\n");
      html.append("<div class=\"octane-card-header\"><div>");
      html.append("<h2 class=\"octane-card-title\">Suite run charts</h2>");
      html.append("<div class=\"octane-muted\">");
      html.append(escapeHtml(snapshot.getStateLabel()));
      html.append("</div>");
      html.append("</div>");
      renderCardTools(html);
      html.append("</div>\n");
      html.append("<div class=\"octane-empty\">");
      html.append(escapeHtml(snapshot.getEmptyReportMessage()));
      html.append("</div>\n");
      html.append("</section>\n");
    }
    html.append("</div>\n");
  }

  private void renderDistributionCard(StringBuilder html, OctaneGateReportSection section) {
    html.append(
        "<section class=\"octane-chart-card\" draggable=\"true\" data-card-key=\"distribution-");
    html.append(escapeAttribute(section.getSource()));
    html.append("\">\n");
    html.append("<div class=\"octane-card-header\"><div>");
    html.append("<h2 class=\"octane-card-title\">");
    html.append(escapeHtml(section.getStatusDistributionTitle()));
    html.append("</h2>");
    html.append("<div class=\"octane-distribution-meta\">");
    html.append("<span class=\"octane-total-label\">Total: ");
    html.append(section.getMetrics().getTotal());
    html.append("</span>");
    html.append("<div class=\"octane-legend\">");
    for (OctaneGateStatusCount status : section.getTotals()) {
      if (status.getCount() > 0) {
        renderLegendKey(html, status);
      }
    }
    html.append("</div>");
    html.append("</div>");
    html.append("</div>");
    renderCardTools(html);
    html.append("</div>\n");
    html.append("<div class=\"octane-donut-wrap\">\n");
    html.append("<svg class=\"octane-donut\" viewBox=\"-10 -10 120 120\" role=\"img\" ");
    html.append("aria-label=\"");
    html.append(escapeAttribute(section.getStatusDistributionTitle()));
    html.append("\">\n");
    html.append("<title>");
    html.append(escapeHtml(section.getStatusDistributionTitle()));
    html.append("</title>\n");
    for (OctaneGatePieSlice slice : section.getPieSlices()) {
      renderSlice(html, slice);
    }
    html.append("<circle class=\"octane-donut-hole\" cx=\"50\" cy=\"50\" r=\"30\" />\n");
    for (OctaneGatePieSlice slice : section.getPieSlices()) {
      renderSliceLabel(html, slice);
    }
    html.append("</svg>\n");
    html.append("</div>\n");
    renderDistributionSummaryTable(html, section);
    html.append("</section>\n");
  }

  private void renderLegendKey(StringBuilder html, OctaneGateStatusCount status) {
    html.append("<div class=\"octane-legend-row\">");
    html.append("<span class=\"octane-swatch\" style=\"background: ");
    html.append(escapeAttribute(status.getColor()));
    html.append(";\"></span>");
    html.append("<span>");
    html.append(escapeHtml(status.getLabel()));
    html.append("</span>");
    html.append("</div>\n");
  }

  private void renderSlice(StringBuilder html, OctaneGatePieSlice slice) {
    if (slice.isFullCircle()) {
      html.append("<circle cx=\"50\" cy=\"50\" r=\"46\" fill=\"");
      html.append(escapeAttribute(slice.getColor()));
      html.append("\"><title>");
      html.append(escapeHtml(slice.getTitle()));
      html.append("</title></circle>\n");
      return;
    }
    html.append("<path d=\"");
    html.append(escapeAttribute(slice.getPath()));
    html.append("\" fill=\"");
    html.append(escapeAttribute(slice.getColor()));
    html.append("\"><title>");
    html.append(escapeHtml(slice.getTitle()));
    html.append("</title></path>\n");
  }

  private void renderSliceLabel(StringBuilder html, OctaneGatePieSlice slice) {
    html.append("<text class=\"octane-donut-label\" x=\"");
    html.append(escapeAttribute(slice.getLabelX()));
    html.append("\" y=\"");
    html.append(escapeAttribute(slice.getLabelY()));
    html.append("\" dominant-baseline=\"central\">");
    html.append(escapeHtml(slice.getPercentageLabel()));
    html.append("</text>\n");
  }

  private void renderSuiteRunCard(
      StringBuilder html, OctaneGateReportSection section, Integer maxVisibleBars) {
    html.append("<section class=\"octane-chart-card\" draggable=\"true\" data-card-key=\"bars-");
    html.append(escapeAttribute(section.getSource()));
    html.append("\">\n");
    html.append("<div class=\"octane-card-header\"><div>");
    html.append("<h2 class=\"octane-card-title\">");
    html.append(escapeHtml(section.getSuiteRunChartTitle()));
    html.append("</h2>");
    html.append("<div class=\"octane-suite-chart-meta\">");
    html.append("<span class=\"octane-total-label\">Total Suiteruns: ");
    html.append(section.getSuiteRunCount());
    html.append("</span>");
    html.append("<div class=\"octane-legend\">");
    for (OctaneGateStatusCount status : section.getTotals()) {
      if (status.getCount() > 0) {
        renderLegendKey(html, status);
      }
    }
    html.append("</div>");
    html.append("</div>");
    html.append("</div>");
    renderCardTools(html);
    html.append("</div>\n");
    html.append("<div class=\"octane-bar-graph\">\n");
    html.append("<div class=\"octane-y-axis-label\">Test Runs</div>\n");
    html.append("<div class=\"octane-y-axis-scale\">");
    for (Integer tick : section.getYAxisTicks()) {
      html.append("<span class=\"octane-axis-value\">");
      html.append(tick);
      html.append("</span>");
    }
    html.append("</div>\n");
    html.append("<div class=\"octane-bar-plot\" style=\"--octane-grid-line-count: ");
    html.append(section.getYAxisGridLineCount());
    html.append(";\">\n");
    String cardKey = "bars-" + section.getSource();
    List<OctaneGateSuiteRunChart> suiteRuns = section.getSuiteRuns();
    List<OctaneGateSuiteRunChart> visibleSuiteRuns = suiteRuns;
    int hiddenCount = 0;
    if (maxVisibleBars != null && suiteRuns.size() > maxVisibleBars) {
      visibleSuiteRuns = suiteRuns.subList(0, maxVisibleBars);
      hiddenCount = suiteRuns.size() - maxVisibleBars;
    }
    boolean hasOverflow = hiddenCount > 0;
    html.append("<div class=\"octane-vertical-bars");
    if (hasOverflow) {
      html.append(" octane-fluid-bars-dense");
    }
    html.append("\">\n");
    for (OctaneGateSuiteRunChart suiteRun : visibleSuiteRuns) {
      renderSuiteRunColumn(html, suiteRun, cardKey);
    }
    if (hasOverflow) {
      renderBarOverflowIndicator(html, hiddenCount);
    }
    html.append("</div>\n");
    html.append("</div>\n");
    html.append("</div>\n");
    renderSuiteRunSummaryTable(html, section);
    html.append("</section>\n");
  }

  private void renderCardTools(StringBuilder html) {
    html.append("<button class=\"octane-card-tools\" type=\"button\" ");
    html.append("aria-label=\"Move widget\" ");
    html.append("title=\"Drag to move. Use arrow keys to reorder. Resize from the corner.\">");
    html.append("<svg class=\"octane-grabber-icon\" viewBox=\"0 0 24 24\" ");
    html.append("aria-hidden=\"true\" focusable=\"false\">");
    html.append("<circle cx=\"12\" cy=\"5\" r=\"2\" />");
    html.append("<circle cx=\"12\" cy=\"12\" r=\"2\" />");
    html.append("<circle cx=\"12\" cy=\"19\" r=\"2\" />");
    html.append("</svg>");
    html.append("</button>");
  }

  private void renderDistributionSummaryTable(StringBuilder html, OctaneGateReportSection section) {
    html.append("<table class=\"octane-chart-data-summary octane-visually-hidden\">");
    html.append("<caption>");
    html.append(escapeHtml(section.getStatusDistributionTitle()));
    html.append("</caption><thead><tr>");
    html.append("<th scope=\"col\">Status</th>");
    html.append("<th scope=\"col\">Count</th>");
    html.append("<th scope=\"col\">Percent</th>");
    html.append("</tr></thead><tbody>");
    for (OctaneGateStatusCount status : section.getTotals()) {
      if (status.getCount() > 0) {
        html.append("<tr><th scope=\"row\">");
        html.append(escapeHtml(status.getLabel()));
        html.append("</th><td>");
        html.append(status.getCount());
        html.append("</td><td>");
        html.append(escapeHtml(status.getPercentageLabel()));
        html.append("</td></tr>");
      }
    }
    html.append("</tbody></table>\n");
  }

  private void renderSuiteRunSummaryTable(StringBuilder html, OctaneGateReportSection section) {
    html.append("<table class=\"octane-chart-data-summary octane-visually-hidden\">");
    html.append("<caption>");
    html.append(escapeHtml(section.getSuiteRunChartTitle()));
    html.append("</caption><thead><tr>");
    html.append("<th scope=\"col\">Run by</th>");
    html.append("<th scope=\"col\">Status</th>");
    html.append("<th scope=\"col\">Count</th>");
    html.append("<th scope=\"col\">Percent</th>");
    html.append("<th scope=\"col\">Total</th>");
    html.append("</tr></thead><tbody>");
    for (OctaneGateSuiteRunChart suiteRun : section.getSuiteRuns()) {
      for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
        if (status.getCount() > 0) {
          html.append("<tr><th scope=\"row\">");
          html.append(escapeHtml(suiteRun.getDisplayName()));
          html.append("</th><td>");
          html.append(escapeHtml(status.getLabel()));
          html.append("</td><td>");
          html.append(status.getCount());
          html.append("</td><td>");
          html.append(escapeHtml(status.getPercentageLabel()));
          html.append("</td><td>");
          html.append(suiteRun.getTotal());
          html.append("</td></tr>");
        }
      }
    }
    html.append("</tbody></table>\n");
  }

  private void renderSuiteRunColumn(
      StringBuilder html, OctaneGateSuiteRunChart suiteRun, String cardKey) {
    html.append("<div class=\"octane-suite-column\" tabindex=\"0\" aria-label=\"");
    html.append(escapeAttribute(suiteRun.getTitle()));
    html.append("\" data-card-key=\"");
    html.append(escapeAttribute(cardKey));
    html.append("\" data-bar-key=\"");
    html.append(escapeAttribute(suiteRun.getSuiteRunId()));
    html.append("\" data-dominant-status-color=\"");
    html.append(escapeAttribute(suiteRun.getDominantStatusColor()));
    html.append("\" data-dominant-status-label=\"");
    html.append(escapeAttribute(suiteRun.getDominantStatusLabel()));
    html.append("\" data-status-passed-count=\"");
    html.append(suiteRun.getPassedCount());
    html.append("\" data-status-passed-color=\"");
    html.append(escapeAttribute(suiteRun.getPassedTooltipColor()));
    html.append("\" data-status-failed-count=\"");
    html.append(suiteRun.getFailedCount());
    html.append("\" data-status-failed-color=\"");
    html.append(escapeAttribute(suiteRun.getFailedTooltipColor()));
    html.append("\" data-status-blocked-count=\"");
    html.append(suiteRun.getBlockedCount());
    html.append("\" data-status-blocked-color=\"");
    html.append(escapeAttribute(suiteRun.getBlockedTooltipColor()));
    html.append("\" data-status-skipped-count=\"");
    html.append(suiteRun.getSkippedCount());
    html.append("\" data-status-skipped-color=\"");
    html.append(escapeAttribute(suiteRun.getSkippedTooltipColor()));
    html.append("\" data-status-running-count=\"");
    html.append(suiteRun.getRunningCount());
    html.append("\" data-status-running-color=\"");
    html.append(escapeAttribute(suiteRun.getRunningTooltipColor()));
    html.append("\">\n");
    html.append("<div class=\"octane-vertical-bar-wrap\">\n");
    html.append("<div class=\"octane-vertical-bar\" style=\"");
    html.append(escapeAttribute(suiteRun.getBarHeightStyle()));
    html.append("\">\n");
    for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
      if (status.getCount() > 0) {
        html.append("<span class=\"octane-vertical-segment\" style=\"");
        html.append(escapeAttribute(status.getHeightStyle()));
        html.append("\"></span>\n");
      }
    }
    html.append("</div>\n");
    html.append("</div>\n");
    html.append("<span class=\"octane-suite-label\" title=\"");
    html.append(escapeAttribute(suiteRun.getTitle()));
    html.append("\">");
    html.append(escapeHtml(suiteRun.getAxisLabel()));
    html.append("</span>\n");
    renderSuiteRunPopup(html, suiteRun);
    html.append("</div>\n");
  }

  private void renderBarOverflowIndicator(StringBuilder html, int hiddenCount) {
    html.append("<div class=\"octane-bar-overflow-indicator\" role=\"note\" aria-label=\"");
    html.append(hiddenCount);
    html.append(" tester bars omitted\" data-hidden-count=\"");
    html.append(hiddenCount);
    html.append("\">");
    html.append("<span class=\"octane-bar-overflow-line\" aria-hidden=\"true\"></span>");
    html.append("<span class=\"octane-bar-overflow-count\">+");
    html.append(hiddenCount);
    html.append("</span></div>\n");
  }

  static int maxVisibleBars(int viewportWidth) {
    return Math.max(
        1, (Math.max(0, viewportWidth) - OVERFLOW_INDICATOR_WIDTH_PX) / BAR_SLOT_WIDTH_PX);
  }

  static int emailBarChartWidth(int viewportWidth) {
    int safeViewportWidth = Math.max(320, viewportWidth);
    int availableWidth =
        safeViewportWidth <= EMAIL_SINGLE_COLUMN_BREAKPOINT_PX
            ? safeViewportWidth - EMAIL_SINGLE_COLUMN_CHROME_PX
            : safeViewportWidth / 2 - EMAIL_TWO_COLUMN_CHROME_PX;
    return Math.max(BAR_SLOT_WIDTH_PX + OVERFLOW_INDICATOR_WIDTH_PX, availableWidth);
  }

  private void renderSuiteRunPopup(StringBuilder html, OctaneGateSuiteRunChart suiteRun) {
    html.append("<div class=\"octane-bar-popup\" role=\"tooltip\"");
    if (!suiteRun.getDominantStatusColor().isEmpty()) {
      html.append(" style=\"--octane-popup-border-color: ");
      html.append(escapeAttribute(suiteRun.getDominantStatusColor()));
      html.append(";\"");
    }
    html.append(">\n");
    html.append("<div class=\"octane-bar-popup-name\">");
    html.append(escapeHtml(suiteRun.getDisplayName()));
    html.append("</div>\n");
    for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
      if (status.getCount() > 0) {
        html.append("<div class=\"octane-bar-popup-row\">");
        html.append("<span class=\"octane-swatch\" style=\"background: ");
        html.append(escapeAttribute(status.getTooltipColor()));
        html.append(";\"></span>");
        html.append("<span class=\"octane-bar-popup-label\">");
        html.append(escapeHtml(status.getLabel()));
        html.append("</span>");
        html.append("<span class=\"octane-bar-popup-value\">");
        html.append(status.getCount());
        html.append("</span>");
        html.append("<span class=\"octane-bar-popup-percent\">(");
        html.append(escapeHtml(status.getPercentageLabel()));
        html.append(")</span>");
        html.append("</div>\n");
      }
    }
    html.append("<div class=\"octane-bar-popup-total\">");
    html.append("<span>Total</span>");
    html.append("<span class=\"octane-bar-popup-total-value\">");
    html.append(suiteRun.getTotal());
    html.append("</span>");
    html.append("</div>\n");
    html.append("</div>\n");
  }

  private String escapeHtml(String value) {
    return escapeAttribute(value);
  }

  private String escapeAttribute(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
