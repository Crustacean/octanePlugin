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
        .octane-card-tools {
          color: #8a94a6;
          font-size: 18px;
          letter-spacing: 3px;
          user-select: none;
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
          align-items: end;
          display: flex;
          gap: 16px;
          justify-content: flex-start;
          min-height: 270px;
          overflow: hidden;
          padding: 16px 4px 0;
          width: 100%;
        }
        .octane-vertical-bars::before,
        .octane-vertical-bars::after {
          content: "";
          flex: 1 1 0;
          min-width: 0;
        }
        .octane-suite-column {
          align-items: center;
          display: grid;
          flex: 0 0 67px;
          gap: 8px;
          grid-template-rows: 210px 19px 48px;
          justify-items: center;
          min-width: 67px;
        }
        .octane-vertical-bar {
          align-items: stretch;
          align-self: end;
          background: #e6ebf2;
          border-radius: 4px 4px 0 0;
          display: flex;
          flex-direction: column-reverse;
          height: 210px;
          overflow: hidden;
          width: 38px;
        }
        .octane-vertical-segment {
          display: block;
          width: 100%;
        }
        .octane-suite-label {
          display: block;
          max-width: 90px;
          min-height: 16px;
          overflow: hidden;
          text-align: right;
          text-overflow: ellipsis;
          transform: rotate(-45deg);
          transform-origin: center;
          white-space: nowrap;
        }
        .octane-total {
          font-weight: 600;
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
    if (snapshot.hasSections()) {
      for (OctaneGateReportSection section : snapshot.getSections()) {
        renderDistributionCard(html, section);
        renderSuiteRunCard(html, section);
      }
    } else {
      html.append("<section class=\"octane-chart-card\" draggable=\"true\">\n");
      html.append("<div class=\"octane-card-header\"><div>");
      html.append("<h2 class=\"octane-card-title\">Suite run charts</h2>");
      html.append("<div class=\"octane-muted\">Waiting for first poll</div>");
      html.append("</div><span class=\"octane-card-tools\" ");
      html.append("title=\"Drag to move. Resize from the corner.\">:::</span></div>\n");
      html.append("<div class=\"octane-empty\">");
      html.append("The report will populate after the first Octane poll.");
      html.append("</div>\n");
      html.append("</section>\n");
    }
    html.append("</div>\n");
  }

  private void renderDistributionCard(StringBuilder html, OctaneGateReportSection section) {
    html.append("<section class=\"octane-chart-card\" draggable=\"true\">\n");
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
    html.append("</div><span class=\"octane-card-tools\" ");
    html.append("title=\"Drag to move. Resize from the corner.\">:::</span></div>\n");
    if (section.isNoRuns()) {
      html.append("<div class=\"octane-empty\">No run results have been returned yet.</div>\n");
    } else {
      html.append("<div class=\"octane-donut-wrap\">\n");
      html.append("<svg class=\"octane-donut\" viewBox=\"-10 -10 120 120\" role=\"img\" ");
      html.append("aria-label=\"");
      html.append(escapeAttribute(section.getStatusDistributionTitle()));
      html.append("\">\n");
      for (OctaneGatePieSlice slice : section.getPieSlices()) {
        renderSlice(html, slice);
      }
      html.append("<circle class=\"octane-donut-hole\" cx=\"50\" cy=\"50\" r=\"27\" />\n");
      for (OctaneGatePieSlice slice : section.getPieSlices()) {
        renderSliceLabel(html, slice);
      }
      html.append("</svg>\n");
      html.append("</div>\n");
    }
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
      html.append("<circle cx=\"50\" cy=\"50\" r=\"42\" fill=\"");
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
    html.append("<section class=\"octane-chart-card\" draggable=\"true\">\n");
    html.append("<div class=\"octane-card-header\"><div>");
    html.append("<h2 class=\"octane-card-title\">");
    html.append(escapeHtml(section.getSuiteRunChartTitle()));
    html.append("</h2>");
    html.append("<div class=\"octane-muted\">Total Suiteruns: ");
    html.append(section.getSuiteRunCount());
    html.append("</div>");
    html.append("</div><span class=\"octane-card-tools\" ");
    html.append("title=\"Drag to move. Resize from the corner.\">:::</span></div>\n");
    if (section.isNoRuns()) {
      html.append("<div class=\"octane-empty\">No run results have been returned yet.</div>\n");
    } else {
      html.append("<div class=\"octane-vertical-bars\">\n");
      for (OctaneGateSuiteRunChart suiteRun : section.getSuiteRuns()) {
        renderSuiteRunColumn(html, suiteRun);
      }
      html.append("</div>\n");
    }
    html.append("</section>\n");
  }

  private void renderSuiteRunColumn(StringBuilder html, OctaneGateSuiteRunChart suiteRun) {
    html.append("<div class=\"octane-suite-column\">\n");
    html.append("<div class=\"octane-vertical-bar\" style=\"");
    html.append(escapeAttribute(suiteRun.getBarHeightStyle()));
    html.append("\" title=\"");
    html.append(suiteRun.getTotal());
    html.append(" tests\">\n");
    for (OctaneGateStatusCount status : suiteRun.getStatuses()) {
      if (status.getCount() > 0) {
        html.append("<span class=\"octane-vertical-segment\" style=\"");
        html.append(escapeAttribute(status.getHeightStyle()));
        html.append("\" title=\"");
        html.append(escapeAttribute(status.getTitle()));
        html.append("\"></span>\n");
      }
    }
    html.append("</div>\n");
    html.append("<span class=\"octane-total\">");
    html.append(suiteRun.getTotal());
    html.append("</span>\n");
    html.append("<span class=\"octane-suite-label\" title=\"");
    html.append(escapeAttribute(suiteRun.getSuiteRunId()));
    html.append("\">");
    html.append(escapeHtml(suiteRun.getSuiteRunId()));
    html.append("</span>\n");
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
