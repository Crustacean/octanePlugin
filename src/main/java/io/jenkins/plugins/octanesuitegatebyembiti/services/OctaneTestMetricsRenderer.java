package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneTestMetricCard;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneTestMetrics;
import java.util.Locale;

public class OctaneTestMetricsRenderer {
  public String render(OctaneTestMetrics metrics) {
    OctaneTestMetrics safeMetrics = metrics == null ? OctaneTestMetrics.empty() : metrics;
    StringBuilder html = new StringBuilder();
    html.append("<div class=\"octane-test-metrics-grid\">");
    for (OctaneTestMetricCard card : safeMetrics.getCards()) {
      html.append("<article class=\"octane-test-metric-card octane-test-metric-")
          .append(escape(card.getKey()))
          .append("\" data-test-metric-key=\"")
          .append(escape(card.getKey()))
          .append("\">");
      html.append("<div class=\"octane-test-metric-heading\">")
          .append(icon(card.getIcon()))
          .append("<span>")
          .append(escape(card.getTitle()))
          .append("</span></div>");
      html.append("<div class=\"octane-test-metric-value\">")
          .append(escape(card.getValue()))
          .append("</div>");
      html.append("<div class=\"octane-test-metric-detail\">")
          .append(escape(card.getDetail()))
          .append("</div>");
      html.append("<div class=\"octane-test-metric-trend octane-test-metric-trend-")
          .append(escape(card.getTrendTone().toLowerCase(Locale.ROOT)))
          .append("\">")
          .append(escape(card.getTrendText()))
          .append("</div>");
      html.append("</article>");
    }
    html.append("</div>");
    return html.toString();
  }

  private String icon(String icon) {
    if ("chart".equals(icon)) {
      return "<svg class=\"octane-test-metric-icon\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
          + "<path d=\"M4 20h16\"/><path d=\"M6 16v-5\"/><path d=\"M12 16V7\"/>"
          + "<path d=\"M18 16v-9\"/></svg>";
    }
    if ("activity".equals(icon)) {
      return "<svg class=\"octane-test-metric-icon\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
          + "<path d=\"M4 12h4l2-6 4 12 2-6h4\"/></svg>";
    }
    if ("defect".equals(icon)) {
      return "<svg class=\"octane-test-metric-icon\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
          + "<path d=\"M12 3l9 16H3z\"/><path d=\"M12 9v4\"/><path d=\"M12 17h.01\"/></svg>";
    }
    return "<svg class=\"octane-test-metric-icon\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
        + "<circle cx=\"12\" cy=\"13\" r=\"8\"/><path d=\"M12 9v4l3 2\"/><path d=\"M9 2h6\"/>"
        + "</svg>";
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
