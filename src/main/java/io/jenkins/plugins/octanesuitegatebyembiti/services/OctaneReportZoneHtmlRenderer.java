package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGatePieSlice;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSection;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateStatusCount;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateSuiteRunChart;

public class OctaneReportZoneHtmlRenderer {
  public String render(OctaneGateReportSnapshot snapshot) {
    OctaneGateReportSnapshot safeSnapshot =
        snapshot == null ? OctaneGateReportSnapshot.empty() : snapshot;
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html>\n");
    html.append("<html>\n");
    html.append("<head>\n");
    html.append("<meta charset=\"UTF-8\" />\n");
    html.append("<title>Octane Gate Report</title>\n");
    appendStyle(html);
    html.append("</head>\n");
    html.append("<body>\n");
    renderReportZone(html, safeSnapshot);
    html.append("</body>\n");
    html.append("</html>\n");
    return html.toString();
  }

  public String renderZone(OctaneGateReportSnapshot snapshot) {
    OctaneGateReportSnapshot safeSnapshot =
        snapshot == null ? OctaneGateReportSnapshot.empty() : snapshot;
    StringBuilder html = new StringBuilder();
    renderReportZone(html, safeSnapshot);
    return html.toString();
  }

  private void appendStyle(StringBuilder html) {
    html.append("<style>\n");
    html.append(
        """
        :root {
          color-scheme: light;
        }
        * {
          box-sizing: border-box;
        }
        body {
          background: #f5f7fb;
          color: #1f2937;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          margin: 0;
          padding: 16px;
        }
        .octane-report-zone {
          align-items: stretch;
          border: 1px solid #f5f7fb;
          border-radius: 8px;
          display: flex;
          flex-wrap: wrap;
          gap: 16px;
          padding: 16px;
          width: 100%;
        }
        .octane-chart-card {
          background: #ffffff;
          border: 1px solid #d7dde6;
          border-radius: 14px;
          box-shadow: 0 1px 7px rgba(0, 0, 0, 0.14);
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
          color: #5f6b7a;
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
          color: #8a94a6;
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
          fill: #1f2937;
          font-size: 4.5px;
          font-weight: 700;
          pointer-events: none;
          text-anchor: middle;
        }
        .octane-donut-hole {
          fill: #ffffff;
        }
        .octane-distribution-meta {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          gap: 6px 12px;
          margin-top: 4px;
        }
        .octane-total-label {
          color: #5f6b7a;
        }
        .octane-suite-chart-meta {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          gap: 6px 12px;
          margin-top: 4px;
        }
        .octane-axis-value {
          color: #827C7B;
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
          color: #827C7B;
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
            rgba(33, 38, 45, 0.92) 0 1px,
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
          background: #30363D;
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
          background: #e6ebf2;
          border-radius: 0;
          display: flex;
          flex-direction: column-reverse;
          height: 100%;
          overflow: hidden;
          width: min(clamp(18.2px, 80.6%, 54.6px), calc(100% - 2px));
        }
        .octane-bar-popup {
          background: #ffffff;
          border: 1px solid #d7dde6;
          border-radius: 8px;
          box-shadow: 0 7px 22px rgba(0, 0, 0, 0.18);
          color: #1f2937;
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
          border-bottom: 1px solid #d7dde6;
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
          border-top: 1px solid #d7dde6;
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
          color: #827C7B;
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
          border: 1px dashed #d7dde6;
          border-radius: 8px;
          color: #5f6b7a;
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

  private void renderReportZone(StringBuilder html, OctaneGateReportSnapshot snapshot) {
    html.append("<div class=\"octane-report-zone octane-card-zone\" id=\"octane-report-zone\">\n");
    if (snapshot.hasReportSections()) {
      for (OctaneGateReportSection section : snapshot.getReportSections()) {
        renderDistributionCard(html, section);
        renderSuiteRunCard(html, section);
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

  private void renderSuiteRunCard(StringBuilder html, OctaneGateReportSection section) {
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
    html.append("<div class=\"octane-vertical-bars\">\n");
    String cardKey = "bars-" + section.getSource();
    for (OctaneGateSuiteRunChart suiteRun : section.getSuiteRuns()) {
      renderSuiteRunColumn(html, suiteRun, cardKey);
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

  private void renderSuiteRunPopup(StringBuilder html, OctaneGateSuiteRunChart suiteRun) {
    html.append("<div class=\"octane-bar-popup\" role=\"tooltip\">\n");
    html.append("<div class=\"octane-bar-popup-name\">");
    html.append(escapeHtml(suiteRun.getDisplayName()));
    html.append("</div>\n");
    for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
      if (status.getCount() > 0) {
        html.append("<div class=\"octane-bar-popup-row\">");
        html.append("<span class=\"octane-swatch\" style=\"background: ");
        html.append(escapeAttribute(status.getColor()));
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
