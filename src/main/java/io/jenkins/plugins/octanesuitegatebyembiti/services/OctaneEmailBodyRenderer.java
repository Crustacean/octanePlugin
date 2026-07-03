package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneExecutionStatusDistribution;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class OctaneEmailBodyRenderer {
  private static final String PASS_COLOR = "#009900";
  private static final String FAIL_COLOR = "#990000";
  private static final String NEUTRAL_COLOR = "#737373";
  private static final String SECTION_TITLE_STYLE =
      "font-family:Arial,sans-serif;font-size:16px;font-weight:700;line-height:1.25;"
          + "padding:0 0 8px;text-align:left;";
  private static final String TABLE_HEADER_STYLE =
      "font-family:Arial,sans-serif;font-size:15px;font-weight:600;line-height:1.4;";
  private static final String TABLE_VALUE_STYLE =
      "font-family:Arial,sans-serif;font-size:15px;font-weight:400;line-height:1.4;";
  private static final String DEFAULT_TEMPLATE =
      """
      Hello Team,

      The automated job for {{PROJECT_NAME}} tests has run and is {{GATE_RESULT}}.

      Set criteria: {{CRITERIA}}

      Click here to {{REPORT_LINK}}.

      See below the execution details:

      {{EXECUTION_DETAILS}}

      {{REPORT_SCREENSHOT}}

      Thanks.
      QA Automation Team
      """;
  private static final DateTimeFormatter EMAIL_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.of("Africa/Nairobi"));

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

  public String render(
      String configuredBody,
      String projectName,
      String domainName,
      OctaneGateReportSnapshot snapshot,
      String reportUrl,
      String screenshotContentId) {
    String template = reportTemplate(configuredBody);
    String normalizedReportUrl = Util.trimToEmpty(reportUrl);
    Verdict verdict = emailVerdict(snapshot == null ? null : snapshot.getState());
    String rendered = escape(template).replace("\r\n", "\n").replace('\r', '\n');
    rendered = rendered.replace("{{PROJECT_NAME}}", escape(defaultText(projectName, "Octane")));
    rendered =
        rendered.replace("{{DOMAIN_NAME}}", escape(defaultText(domainName, "Not specified")));
    rendered =
        rendered.replace(
            "{{GATE_RESULT}}",
            "<strong style=\"color:"
                + verdict.color
                + ";font-weight:700;\">"
                + verdict.label
                + "</strong>");
    rendered =
        rendered.replace(
            "{{CRITERIA}}",
            "<code style=\"font-family:Consolas,monospace;white-space:normal;word-break:break-word;\">"
                + escape(snapshot == null ? "Not available" : snapshot.getCriteria())
                + "</code>");
    rendered =
        rendered.replace(
            "{{REPORT_LINK}}",
            normalizedReportUrl.isEmpty()
                ? "view the report output (link unavailable)"
                : reportLink(normalizedReportUrl, "view the report output"));
    rendered =
        rendered.replace(
            "{{EXECUTION_DETAILS}}", renderExecutionDetails(projectName, domainName, snapshot));
    rendered =
        rendered.replace(
            "{{REPORT_SCREENSHOT}}", renderInlineScreenshot(screenshotContentId, projectName));
    rendered = rendered.replace("\n", "<br>");

    return "<div style=\"color:#202124;font-family:Arial,sans-serif;font-size:15px;"
        + "line-height:1.5;margin:0 auto;max-width:1200px;\">"
        + rendered
        + "</div>";
  }

  private String reportTemplate(String configuredBody) {
    String configured = Util.trimToEmpty(configuredBody);
    if (configured.contains("{{")) {
      return configured;
    }
    if (configured.isEmpty()) {
      return DEFAULT_TEMPLATE;
    }
    return DEFAULT_TEMPLATE.replace("Hello Team,\n\n", "Hello Team,\n\n" + configured + "\n\n");
  }

  private String renderExecutionDetails(
      String projectName, String domainName, OctaneGateReportSnapshot snapshot) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;width:100%;\"><tr><td "
            + "style=\"border:2px solid #374151;padding:16px;\">");
    html.append(
        "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;width:100%;\"><tr>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendProjectDetailsTable(html, projectName, domainName, snapshot);
    html.append("</td><td style=\"font-size:1px;line-height:1px;width:24px;\">&nbsp;</td>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendExecutionTable(html, snapshot);
    html.append("</td></tr></table>");
    appendSpacer(html, 28);
    appendEvaluationTable(html, snapshot);
    html.append("</td></tr></table>");
    return html.toString();
  }

  private void appendProjectDetailsTable(
      StringBuilder html,
      String projectName,
      String domainName,
      OctaneGateReportSnapshot snapshot) {
    html.append(dataTableStart("Project Details"));
    appendDetailRow(html, "Domain", defaultText(domainName, "Not specified"));
    appendDetailRow(html, "Project", defaultText(projectName, "Octane"));
    appendDetailRow(
        html, "Start date", formatTimestamp(snapshot == null ? "" : snapshot.getStartedAt()));
    appendDetailRow(
        html, "End date", formatTimestamp(snapshot == null ? "" : snapshot.getUpdatedAt()));
    html.append("</tbody></table>");
  }

  private void appendExecutionTable(StringBuilder html, OctaneGateReportSnapshot snapshot) {
    int total = snapshot == null ? 0 : snapshot.getProjectTestTotal();
    int executed = snapshot == null ? 0 : snapshot.getExecutedTestCount();
    html.append(dataTableStart("Test case execution"));
    appendDetailRow(html, "Total test cases", total);
    appendDetailRow(html, "Executed test cases", executed);
    appendDetailRow(html, "Blocked test cases", statusCount(snapshot, "Blocked"));
    appendDetailRow(html, "Passed test cases", statusCount(snapshot, "Passed"));
    appendDetailRow(html, "Failed test cases", statusCount(snapshot, "Failed"));
    appendDetailRow(html, "No run test cases", Math.max(0, total - executed));
    appendDetailRow(html, "Skipped test cases", statusCount(snapshot, "Skipped"));
    appendDetailRow(
        html,
        "Execution rate",
        snapshot == null ? "0%" : formatPercentage(snapshot.getExecutionProgress()));
    html.append("</tbody></table>");
  }

  private String dataTableStart(String caption) {
    return "<table cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;"
        + "table-layout:fixed;width:100%;\"><caption style=\""
        + SECTION_TITLE_STYLE
        + "\">"
        + escape(caption)
        + "</caption><tbody>";
  }

  private void appendDetailRow(StringBuilder html, String label, Object value) {
    html.append(
            "<tr><th scope=\"row\" style=\"background:#e5e7eb;border:1px solid #374151;"
                + TABLE_HEADER_STYLE
                + "padding:7px 9px;text-align:right;width:44%;\">")
        .append(escape(label))
        .append(
            "</th><td style=\"border:1px solid #374151;"
                + TABLE_VALUE_STYLE
                + "padding:7px 9px;text-align:left;word-break:break-word;\">")
        .append(escape(String.valueOf(value)))
        .append("</td></tr>");
  }

  private int statusCount(OctaneGateReportSnapshot snapshot, String label) {
    if (snapshot == null) {
      return 0;
    }
    for (OctaneExecutionStatusDistribution.Segment segment :
        snapshot.getExecutionStatusDistribution().getSegments()) {
      if (label.equalsIgnoreCase(segment.getLabel())) {
        return segment.getCount();
      }
    }
    return 0;
  }

  private String renderInlineScreenshot(String contentId, String projectName) {
    String normalizedContentId = Util.trimToEmpty(contentId);
    if (normalizedContentId.isEmpty()) {
      return "<p><strong>Execution report screenshot unavailable.</strong></p>";
    }
    StringBuilder html = new StringBuilder();
    appendSpacer(html, 32);
    html.append(
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;width:100%;\"><tr><td style=\""
                + SECTION_TITLE_STYLE
                + "\">Execution graph</td></tr><tr><td>")
        .append("<img src=\"cid:")
        .append(escape(normalizedContentId))
        .append("\" alt=\"")
        .append(escape(defaultText(projectName, "Octane")))
        .append(
            " gate execution report charts\" style=\"border:0;display:block;height:auto;"
                + "max-width:100%;width:100%;\"></td></tr></table>");
    appendSpacer(html, 24);
    return html.toString();
  }

  private void appendSpacer(StringBuilder html, int height) {
    html.append(
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;width:100%;\"><tr><td height=\"")
        .append(height)
        .append("\" style=\"font-size:1px;height:")
        .append(height)
        .append("px;line-height:1px;\">&nbsp;</td></tr></table>");
  }

  private String formatTimestamp(String value) {
    try {
      return EMAIL_DATE_FORMATTER.format(Instant.parse(value)) + " EAT";
    } catch (RuntimeException e) {
      return defaultText(value, "Not available");
    }
  }

  private String formatPercentage(double value) {
    if (Math.abs(value - Math.rint(value)) < 0.0001) {
      return String.format(Locale.ROOT, "%.0f%%", value);
    }
    return String.format(Locale.ROOT, "%.1f%%", value);
  }

  private String defaultText(String value, String fallback) {
    String normalized = Util.trimToEmpty(value);
    return normalized.isEmpty() ? fallback : normalized;
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
        "<table cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;"
            + "table-layout:fixed;width:100%;\">");
    html.append("<caption style=\"")
        .append(SECTION_TITLE_STYLE)
        .append("\">Criteria evaluation</caption>");
    html.append(
        "<colgroup><col style=\"width:58%;\"><col style=\"width:22%;\"><col style=\"width:20%;\"></colgroup>");
    html.append("<thead><tr>");
    appendHeader(html, "Criterion", "left");
    appendHeader(html, "Actual", "right");
    appendHeader(html, "Result", "left");
    html.append("</tr></thead><tbody>");
    for (CriteriaComparisonEvaluation comparison : evaluation.getComparisons()) {
      String color = comparison.isPassed() ? PASS_COLOR : FAIL_COLOR;
      html.append("<tr>");
      html.append("<td style=\"border:1px solid #d0d7de;")
          .append(TABLE_VALUE_STYLE)
          .append("padding:0.5rem;text-align:left;\"><code>")
          .append(escape(comparison.getCriterionLabel()))
          .append("</code></td>");
      html.append(
              "<td style=\"border:1px solid #d0d7de;"
                  + TABLE_VALUE_STYLE
                  + "padding:0.5rem;text-align:right;white-space:nowrap;\">")
          .append(escape(comparison.getActualLabel()))
          .append("</td>");
      html.append("<td style=\"border:1px solid #d0d7de;color:")
          .append(color)
          .append(";")
          .append(TABLE_VALUE_STYLE)
          .append("font-weight:700;padding:0.5rem;text-align:left;white-space:nowrap;\">")
          .append(comparison.getResultLabel())
          .append("</td>");
      html.append("</tr>");
    }
    html.append("</tbody></table>");
  }

  private void appendHeader(StringBuilder html, String label, String alignment) {
    html.append("<th scope=\"col\" style=\"background:#f6f8fa;border:1px solid #d0d7de;")
        .append(TABLE_HEADER_STYLE)
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
    return reportLink(reportUrl, reportUrl);
  }

  private String reportLink(String reportUrl, String label) {
    String escaped = escape(reportUrl);
    return "<a href=\""
        + escaped
        + "\" style=\"color:#0969da;text-decoration:underline;\">"
        + escape(label)
        + "</a>";
  }

  private Verdict emailVerdict(OctaneGateReportState state) {
    Verdict gateVerdict = verdict(state);
    return "PASS".equals(gateVerdict.label)
        ? new Verdict("SUCCESS", gateVerdict.color)
        : gateVerdict;
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
