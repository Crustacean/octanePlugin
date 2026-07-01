package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;

public class OctaneEmailBodyRenderer {
  private static final String PASS_COLOR = "#009900";
  private static final String FAIL_COLOR = "#990000";
  private static final String NEUTRAL_COLOR = "#737373";

  public String render(String configuredBody, OctaneGateReportSnapshot snapshot, String reportUrl) {
    String body =
        Util.trimToEmpty(configuredBody).isEmpty()
            ? "Attached is the Octane report-zone screenshot."
            : Util.trimToEmpty(configuredBody);
    String normalizedReportUrl = Util.trimToEmpty(reportUrl);
    boolean bodyContainsReportUrl =
        !normalizedReportUrl.isEmpty() && body.contains(normalizedReportUrl);
    Verdict verdict = verdict(snapshot == null ? null : snapshot.getState());

    StringBuilder html = new StringBuilder();
    html.append("<div style=\"font-family:Arial,sans-serif;color:#202124;line-height:1.5;\">");
    html.append("<p>").append(renderPlainText(body, normalizedReportUrl)).append("</p>");
    html.append("<h2 style=\"font-size:1.25rem;margin:1.25rem 0 0.75rem;\">")
        .append("Octane Criteria Result")
        .append("</h2>");
    appendLabelValue(
        html,
        "Octane Criteria Verdict",
        "<strong style=\"color:" + verdict.color + ";\">" + verdict.label + "</strong>");
    appendLabelValue(
        html,
        "Criteria",
        "<code style=\"white-space:normal;overflow-wrap:anywhere;\">"
            + escape(snapshot == null ? "" : snapshot.getCriteria())
            + "</code>");
    appendLabelValue(
        html, "Gate state", escape(snapshot == null ? "Not available" : snapshot.getStateLabel()));
    appendLabelValue(
        html,
        "Reason",
        escape(snapshot == null ? "Gate report data is unavailable." : snapshot.getMessage()));
    appendEvaluationTable(html, snapshot);
    if (!bodyContainsReportUrl && !normalizedReportUrl.isEmpty()) {
      html.append("<p style=\"margin:1rem 0 0;\"><strong>Octane Gate Report:</strong> ")
          .append(reportLink(normalizedReportUrl))
          .append("</p>");
    }
    html.append("</div>");
    return html.toString();
  }

  private void appendEvaluationTable(StringBuilder html, OctaneGateReportSnapshot snapshot) {
    CriteriaEvaluation evaluation =
        snapshot == null ? CriteriaEvaluation.unavailable() : snapshot.getCriteriaEvaluation();
    if (!evaluation.isAvailable() || evaluation.getComparisons().isEmpty()) {
      html.append("<p style=\"margin:1rem 0 0;\"><strong>Criteria evaluation:</strong> ")
          .append("Detailed evaluation unavailable for this build.</p>");
      return;
    }

    html.append(
        "<table style=\"border-collapse:collapse;margin-top:1rem;max-width:760px;width:100%;\">");
    html.append(
        "<caption style=\"font-weight:700;padding:0 0 0.5rem;text-align:left;\">Criteria evaluation</caption>");
    html.append("<thead><tr>");
    appendHeader(html, "Criterion", "left");
    appendHeader(html, "Actual", "right");
    appendHeader(html, "Result", "left");
    html.append("</tr></thead><tbody>");
    for (CriteriaComparisonEvaluation comparison : evaluation.getComparisons()) {
      String color = comparison.isPassed() ? PASS_COLOR : FAIL_COLOR;
      html.append("<tr>");
      html.append("<td style=\"border:1px solid #d0d7de;padding:0.5rem;text-align:left;\"><code>")
          .append(escape(comparison.getCriterionLabel()))
          .append("</code></td>");
      html.append(
              "<td style=\"border:1px solid #d0d7de;padding:0.5rem;text-align:right;white-space:nowrap;\">")
          .append(escape(comparison.getActualLabel()))
          .append("</td>");
      html.append("<td style=\"border:1px solid #d0d7de;color:")
          .append(color)
          .append(";font-weight:700;padding:0.5rem;text-align:left;white-space:nowrap;\">")
          .append(comparison.getResultLabel())
          .append("</td>");
      html.append("</tr>");
    }
    html.append("</tbody></table>");
  }

  private void appendHeader(StringBuilder html, String label, String alignment) {
    html.append("<th scope=\"col\" style=\"background:#f6f8fa;border:1px solid #d0d7de;")
        .append("padding:0.5rem;text-align:")
        .append(alignment)
        .append(";\">")
        .append(label)
        .append("</th>");
  }

  private void appendLabelValue(StringBuilder html, String label, String valueHtml) {
    html.append("<p style=\"margin:0.35rem 0;\"><strong>")
        .append(label)
        .append(":</strong> ")
        .append(valueHtml)
        .append("</p>");
  }

  private String renderPlainText(String value, String reportUrl) {
    String rendered = escape(value).replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
    if (reportUrl.isEmpty()) {
      return rendered;
    }
    return rendered.replace(escape(reportUrl), reportLink(reportUrl));
  }

  private String reportLink(String reportUrl) {
    String escaped = escape(reportUrl);
    return "<a href=\"" + escaped + "\">" + escaped + "</a>";
  }

  private Verdict verdict(OctaneGateReportState state) {
    if (state == OctaneGateReportState.PASSED) {
      return new Verdict("PASS", PASS_COLOR);
    }
    if (state == OctaneGateReportState.FAILED
        || state == OctaneGateReportState.UNSTABLE
        || state == OctaneGateReportState.TIMED_OUT) {
      return new Verdict("FAIL", FAIL_COLOR);
    }
    return new Verdict("NOT EVALUATED", NEUTRAL_COLOR);
  }

  private String escape(String value) {
    return Util.trimToEmpty(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static class Verdict {
    private final String label;
    private final String color;

    Verdict(String label, String color) {
      this.label = label;
      this.color = color;
    }
  }
}
