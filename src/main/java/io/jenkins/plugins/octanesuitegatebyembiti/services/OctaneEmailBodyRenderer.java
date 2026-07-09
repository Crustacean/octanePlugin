package io.jenkins.plugins.octanesuitegatebyembiti.services;

import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaComparisonEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.CriteriaEvaluation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.DefectCriteriaMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectSeveritySummary;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneExecutionStatusDistribution;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportSnapshot;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OctaneEmailBodyRenderer {
  private static final String PASS_COLOR = "#009900";
  private static final String FAIL_COLOR = "#990000";
  private static final String NEUTRAL_COLOR = "#737373";
  private static final String LIGHT_SYSTEM_GREEN = "#34C759";
  private static final String LIGHT_SYSTEM_RED = "#FF3B30";
  private static final String DARK_SYSTEM_GREEN = "#30D158";
  private static final String DARK_SYSTEM_RED = "#FF453A";
  private static final String SECTION_TITLE_STYLE =
      "font-family:Arial,sans-serif;font-size:16px;font-weight:600;line-height:1.25;"
          + "padding:0 0 8px;text-align:left;";
  private static final String TABLE_HEADER_STYLE =
      "font-family:Arial,sans-serif;font-size:15px;font-weight:600;line-height:1.4;";
  private static final String TABLE_VALUE_STYLE =
      "font-family:Arial,sans-serif;font-size:15px;font-weight:400;line-height:1.4;";
  private static final String TABLE_CELL_PADDING = "padding:4px 8px;";
  private static final String EXECUTION_DETAILS_TOKEN = "{{EXECUTION_DETAILS}}";
  private static final String REPORT_SCREENSHOT_TOKEN = "{{REPORT_SCREENSHOT}}";
  private static final String[][] DEFECT_SEVERITIES = {
    {"critical", "Critical"},
    {"veryHigh", "Very High"},
    {"high", "High"},
    {"medium", "Medium"},
    {"low", "Low"},
    {"unspecified", "Unspecified"}
  };
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
    return render(
        configuredBody,
        projectName,
        domainName,
        snapshot,
        reportUrl,
        screenshotContentId,
        OctaneReportTheme.LIGHT.name());
  }

  public String render(
      String configuredBody,
      String projectName,
      String domainName,
      OctaneGateReportSnapshot snapshot,
      String reportUrl,
      String screenshotContentId,
      String theme) {
    return render(
        configuredBody,
        projectName,
        domainName,
        snapshot,
        reportUrl,
        screenshotContentId,
        theme,
        false);
  }

  public String render(
      String configuredBody,
      String projectName,
      String domainName,
      OctaneGateReportSnapshot snapshot,
      String reportUrl,
      String screenshotContentId,
      String theme,
      boolean printDefectGroups) {
    String template = reportTemplate(configuredBody);
    String normalizedReportUrl = Util.trimToEmpty(reportUrl);
    Verdict verdict = emailVerdict(snapshot == null ? null : snapshot.getState());
    String criteriaHtml =
        "<code style=\"font-family:Consolas,monospace;white-space:normal;word-break:break-word;\">"
            + escape(snapshot == null ? "Not available" : snapshot.getCriteria())
            + "</code>"
            + renderDefectGroupsParagraph(snapshot, printDefectGroups);
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
    rendered = rendered.replace("{{CRITERIA}}", criteriaHtml);
    rendered =
        rendered.replace(
            "{{REPORT_LINK}}",
            normalizedReportUrl.isEmpty()
                ? "view the report output (link unavailable)"
                : reportLink(normalizedReportUrl, "view the report output"));
    rendered =
        renderExecutionReport(
            rendered, projectName, domainName, snapshot, screenshotContentId, theme);
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

  private String renderExecutionReport(
      String template,
      String projectName,
      String domainName,
      OctaneGateReportSnapshot snapshot,
      String screenshotContentId,
      String theme) {
    String details = renderExecutionDetails(projectName, domainName, snapshot, theme);
    String screenshot = renderInlineScreenshot(screenshotContentId, projectName);
    int detailsStart = template.indexOf(EXECUTION_DETAILS_TOKEN);
    int screenshotStart = template.indexOf(REPORT_SCREENSHOT_TOKEN);
    if (detailsStart >= 0 && screenshotStart > detailsStart) {
      String contents = details + screenshot;
      return template.substring(0, detailsStart)
          + wrapExecutionReport(contents)
          + template.substring(screenshotStart + REPORT_SCREENSHOT_TOKEN.length());
    }
    return template
        .replace(EXECUTION_DETAILS_TOKEN, wrapExecutionReport(details))
        .replace(REPORT_SCREENSHOT_TOKEN, screenshot);
  }

  private String wrapExecutionReport(String contents) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<table data-octane-email-section=\"execution-report\" role=\"presentation\" "
            + "cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;width:100%;\"><tr><td "
            + "style=\"border:2px solid #374151;padding:16px;\">");
    html.append(contents);
    html.append("</td></tr></table>");
    return html.toString();
  }

  private String renderExecutionDetails(
      String projectName, String domainName, OctaneGateReportSnapshot snapshot, String theme) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;width:100%;\"><tr>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendProjectDetailsTable(html, projectName, domainName, snapshot);
    html.append("</td><td style=\"font-size:1px;line-height:1px;width:24px;\">&nbsp;</td>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendExecutionTable(html, snapshot, theme);
    html.append("</td></tr></table>");
    appendSpacer(html, 28);
    appendDefectAnalysisTables(html, snapshot);
    appendSpacer(html, 28);
    appendEvaluationTable(html, snapshot);
    return html.toString();
  }

  private void appendDefectAnalysisTables(StringBuilder html, OctaneGateReportSnapshot snapshot) {
    DefectCriteriaMetrics metrics =
        snapshot == null
            ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), java.util.List.of())
            : snapshot.getDefectMetrics();
    html.append(
        "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"border-collapse:collapse;width:100%;\"><tr>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendDefectDistributionMatrix(html, metrics);
    html.append("</td><td style=\"font-size:1px;line-height:1px;width:24px;\">&nbsp;</td>");
    html.append("<td style=\"vertical-align:top;width:50%;\">");
    appendDefectStatusTable(html, metrics);
    html.append("</td></tr></table>");
  }

  private void appendDefectDistributionMatrix(StringBuilder html, DefectCriteriaMetrics metrics) {
    OctaneDefectSeveritySummary summary = metrics.getSeveritySummary();
    int[] priorityTotals = new int[3];
    html.append(
        defectTableStart(
            "defect-distribution", "Defect Distribution Matrix (Severity vs. Priority)"));
    html.append("<thead><tr>");
    appendHeader(html, "Severity / Priority", "left");
    appendHeader(html, "Highest", "right");
    appendHeader(html, "Medium", "right");
    appendHeader(html, "Low", "right");
    html.append("</tr></thead><tbody>");
    for (String[] severity : DEFECT_SEVERITIES) {
      int count = summary.getTotalCount(severity[0]);
      int priorityIndex = priorityIndex(metrics, severity[0]);
      priorityTotals[priorityIndex] += count;
      html.append("<tr>");
      appendDefectRowHeader(html, severity[1]);
      for (int index = 0; index < priorityTotals.length; index++) {
        String priority = priorityLabel(index);
        appendDefectCell(
            html,
            index == priorityIndex ? count : 0,
            severity[1] + " severity, " + priority + " priority");
      }
      html.append("</tr>");
    }
    html.append("<tr>");
    appendDefectRowHeader(html, "Total");
    for (int index = 0; index < priorityTotals.length; index++) {
      String priority = priorityLabel(index);
      appendDefectCell(html, priorityTotals[index], "All severities, " + priority + " priority");
    }
    html.append("</tr></tbody></table>");
  }

  private void appendDefectStatusTable(StringBuilder html, DefectCriteriaMetrics metrics) {
    OctaneDefectSeveritySummary summary = metrics.getSeveritySummary();
    List<DefectStatusColumn> columns = defectStatusColumns(metrics);
    html.append(defectTableStart("defect-status", "Defect Status Table (by Severity)"));
    html.append("<thead><tr>");
    appendHeader(html, "Defect Status", "left");
    for (DefectStatusColumn column : columns) {
      appendHeader(html, titleCase(column.label), "right");
    }
    html.append("</tr></thead><tbody>");
    appendDefectStatusRow(html, "Open", summary, columns, DefectStatusCount.OPEN);
    appendDefectStatusRow(html, "Closed", summary, columns, DefectStatusCount.CLOSED);
    appendDefectStatusRow(html, "Total", summary, columns, DefectStatusCount.TOTAL);
    html.append("</tbody></table>");
  }

  private void appendDefectStatusRow(
      StringBuilder html,
      String rowLabel,
      OctaneDefectSeveritySummary summary,
      List<DefectStatusColumn> columns,
      DefectStatusCount countType) {
    html.append("<tr>");
    appendDefectRowHeader(html, rowLabel);
    for (DefectStatusColumn column : columns) {
      int count = defectStatusCount(summary, column.types, countType);
      appendDefectCell(html, count, titleCase(column.label) + ", " + rowLabel);
    }
    html.append("</tr>");
  }

  private List<DefectStatusColumn> defectStatusColumns(DefectCriteriaMetrics metrics) {
    List<DefectStatusColumn> columns = new ArrayList<>();
    Set<String> groupedTypes = new LinkedHashSet<>();
    for (OctaneDefectGroup group : metrics.getConfiguredGroups()) {
      List<String> types = group.getNormalizedTypes();
      if (types.isEmpty()) {
        continue;
      }
      columns.add(new DefectStatusColumn(group.getName(), types));
      groupedTypes.addAll(types);
    }

    for (String[] severity : DEFECT_SEVERITIES) {
      String type = OctaneDefectSeveritySummary.normalizeOpenType(severity[0]);
      if (groupedTypes.contains(type)) {
        continue;
      }
      columns.add(new DefectStatusColumn(severity[1], List.of(type)));
    }
    return columns;
  }

  private int defectStatusCount(
      OctaneDefectSeveritySummary summary, List<String> types, DefectStatusCount countType) {
    int count = 0;
    for (String type : types) {
      if (countType == DefectStatusCount.OPEN) {
        count += summary.getOpenCount(type);
      } else if (countType == DefectStatusCount.CLOSED) {
        count += summary.getClosedCount(type);
      } else {
        count += summary.getTotalCount(type);
      }
    }
    return count;
  }

  private String renderDefectGroupsParagraph(
      OctaneGateReportSnapshot snapshot, boolean printDefectGroups) {
    if (!printDefectGroups) {
      return "";
    }
    DefectCriteriaMetrics metrics =
        snapshot == null
            ? new DefectCriteriaMetrics(OctaneDefectSeveritySummary.empty(), List.of())
            : snapshot.getDefectMetrics();
    String defectGroups = formatDefectGroups(metrics);
    if (defectGroups.isEmpty()) {
      return "";
    }
    return "\n<p style=\"margin:8px 0 0;\">Defect groups: ( " + defectGroups + " )</p>";
  }

  private String formatDefectGroups(DefectCriteriaMetrics metrics) {
    List<String> entries = new ArrayList<>();
    Set<String> groupedTypes = new LinkedHashSet<>();
    for (OctaneDefectGroup group : metrics.getConfiguredGroups()) {
      List<String> types = group.getNormalizedTypes();
      if (types.isEmpty()) {
        continue;
      }
      groupedTypes.addAll(types);
      List<String> labels = new ArrayList<>();
      for (String type : types) {
        labels.add(escape(defectTypeLabel(type)));
      }
      entries.add(
          "<strong>"
              + escape(titleCase(group.getName()))
              + ":</strong> "
              + String.join(", ", labels));
    }

    for (String[] severity : DEFECT_SEVERITIES) {
      String type = OctaneDefectSeveritySummary.normalizeOpenType(severity[0]);
      if (!groupedTypes.contains(type)) {
        entries.add(escape(severity[1]));
      }
    }
    return String.join(" ; ", entries);
  }

  private String defectTypeLabel(String type) {
    String normalized = OctaneDefectSeveritySummary.normalizeOpenType(type);
    for (String[] severity : DEFECT_SEVERITIES) {
      if (normalized.equals(OctaneDefectSeveritySummary.normalizeOpenType(severity[0]))) {
        return severity[1];
      }
    }
    return titleCase(type);
  }

  private String defectTableStart(String tableName, String caption) {
    return "<table data-octane-email-table=\""
        + tableName
        + "\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;"
        + "table-layout:fixed;width:100%;\"><caption style=\""
        + SECTION_TITLE_STYLE
        + "\">"
        + escape(caption)
        + "</caption>";
  }

  private void appendDefectRowHeader(StringBuilder html, String label) {
    html.append("<th scope=\"row\" style=\"background:#f6f8fa;border:1px solid #d0d7de;")
        .append(TABLE_HEADER_STYLE)
        .append(TABLE_CELL_PADDING)
        .append("text-align:left;\">")
        .append(escape(label))
        .append("</th>");
  }

  private void appendDefectCell(StringBuilder html, int count, String accessibleLabel) {
    html.append("<td aria-label=\"")
        .append(escape(accessibleLabel))
        .append(": ")
        .append(count)
        .append("\" style=\"border:1px solid #d0d7de;")
        .append(TABLE_VALUE_STYLE)
        .append(TABLE_CELL_PADDING)
        .append("text-align:right;\">")
        .append(count)
        .append("</td>");
  }

  private int priorityIndex(DefectCriteriaMetrics metrics, String severity) {
    if (metrics.isTypeInGroup("major", severity)) {
      return 0;
    }
    if (metrics.isTypeInGroup("minor", severity)) {
      return 1;
    }
    if ("low".equals(OctaneDefectSeveritySummary.normalizeOpenType(severity))) {
      return 2;
    }
    if ("medium".equals(OctaneDefectSeveritySummary.normalizeOpenType(severity))) {
      return 1;
    }
    return 0;
  }

  private String priorityLabel(int index) {
    if (index == 0) {
      return "Highest";
    }
    if (index == 1) {
      return "Medium";
    }
    return "Low";
  }

  private String titleCase(String value) {
    String normalized =
        Util.trimToEmpty(value)
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .replace('-', ' ')
            .replace('_', ' ');
    if (normalized.isEmpty()) {
      return "";
    }
    List<String> words = new ArrayList<>();
    for (String word : normalized.split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      words.add(
          word.substring(0, 1).toUpperCase(Locale.ROOT)
              + word.substring(1).toLowerCase(Locale.ROOT));
    }
    return String.join(" ", words);
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

  private void appendExecutionTable(
      StringBuilder html, OctaneGateReportSnapshot snapshot, String theme) {
    int total = snapshot == null ? 0 : snapshot.getProjectTestTotal();
    int executed = snapshot == null ? 0 : snapshot.getExecutedTestCount();
    int passed = snapshot == null ? 0 : snapshot.getPassedTestCount();
    String executionRate =
        snapshot == null ? "0%" : formatPercentage(snapshot.getExecutionProgress());
    String passRate = executed == 0 ? "0%" : formatPercentage(passed * 100.0 / executed);
    EmailValueCellStyle passRateStyle = passRateCellStyle(snapshot, theme);
    html.append(dataTableStart("Test case execution"));
    appendDetailRow(html, "Total test cases", total);
    appendDetailRow(html, "Executed test cases", executed);
    appendDetailRow(html, "Blocked test cases", statusCount(snapshot, "Blocked"));
    appendDetailRow(html, "Passed test cases", statusCount(snapshot, "Passed"));
    appendDetailRow(html, "Failed test cases", statusCount(snapshot, "Failed"));
    appendDetailRow(html, "No run test cases", Math.max(0, total - executed));
    appendDetailRow(html, "Skipped test cases", statusCount(snapshot, "Skipped"));
    appendDetailRow(html, "Execution rate", executionRate);
    appendDetailRow(html, "Pass Rate", passRate, passRateStyle);
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
    appendDetailRow(html, label, value, EmailValueCellStyle.defaultStyle());
  }

  private void appendDetailRow(
      StringBuilder html, String label, Object value, EmailValueCellStyle valueCellStyle) {
    html.append(
            "<tr><th scope=\"row\" style=\"background:#e5e7eb;border:1px solid #374151;"
                + TABLE_HEADER_STYLE
                + TABLE_CELL_PADDING
                + "text-align:right;width:44%;\">")
        .append(escape(label))
        .append("</th><td");
    if (!valueCellStyle.bgcolor.isEmpty()) {
      html.append(" bgcolor=\"").append(valueCellStyle.bgcolor).append("\"");
    }
    html.append(" style=\"border:1px solid #374151;")
        .append(valueCellStyle.inlineCss)
        .append(TABLE_VALUE_STYLE)
        .append(TABLE_CELL_PADDING)
        .append("text-align:left;word-break:break-word;\">")
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

  private EmailValueCellStyle passRateCellStyle(OctaneGateReportSnapshot snapshot, String theme) {
    OctaneReportTheme emailTheme = emailTheme(theme);
    OctaneGateReportState state = snapshot == null ? null : snapshot.getState();
    if (emailTheme == OctaneReportTheme.LIGHT) {
      if (state == OctaneGateReportState.PASSED) {
        return EmailValueCellStyle.painted(LIGHT_SYSTEM_GREEN, "#FFFFFF");
      }
      if (isFailState(state)) {
        return EmailValueCellStyle.painted(LIGHT_SYSTEM_RED, "#FFFFFF");
      }
    }
    if (emailTheme == OctaneReportTheme.DARK) {
      if (state == OctaneGateReportState.PASSED) {
        return EmailValueCellStyle.painted(DARK_SYSTEM_GREEN, "#000000");
      }
      if (isFailState(state)) {
        return EmailValueCellStyle.painted(DARK_SYSTEM_RED, "#FFFFFF");
      }
    }
    return EmailValueCellStyle.fallbackStyle();
  }

  private OctaneReportTheme emailTheme(String theme) {
    try {
      return OctaneReportTheme.from(theme);
    } catch (IllegalArgumentException e) {
      return OctaneReportTheme.SYSTEM;
    }
  }

  private boolean isFailState(OctaneGateReportState state) {
    return state == OctaneGateReportState.FAILED
        || state == OctaneGateReportState.UNSTABLE
        || state == OctaneGateReportState.TIMED_OUT;
  }

  private String renderInlineScreenshot(String contentId, String projectName) {
    String normalizedContentId = Util.trimToEmpty(contentId);
    if (normalizedContentId.isEmpty()) {
      return "<p><strong>Execution report screenshot unavailable.</strong></p>";
    }
    StringBuilder html = new StringBuilder();
    appendSpacer(html, 28);
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
          .append(TABLE_CELL_PADDING)
          .append("text-align:left;\"><code>")
          .append(escape(comparison.getCriterionLabel()))
          .append("</code></td>");
      html.append(
              "<td style=\"border:1px solid #d0d7de;"
                  + TABLE_VALUE_STYLE
                  + TABLE_CELL_PADDING
                  + "text-align:right;white-space:nowrap;\">")
          .append(escape(comparison.getActualLabel()))
          .append("</td>");
      html.append("<td style=\"border:1px solid #d0d7de;color:")
          .append(color)
          .append(";")
          .append(TABLE_VALUE_STYLE)
          .append("font-weight:600;")
          .append(TABLE_CELL_PADDING)
          .append("text-align:left;white-space:nowrap;\">")
          .append(comparison.getResultLabel())
          .append("</td>");
      html.append("</tr>");
    }
    html.append("</tbody></table>");
  }

  private void appendHeader(StringBuilder html, String label, String alignment) {
    html.append("<th scope=\"col\" style=\"background:#f6f8fa;border:1px solid #d0d7de;")
        .append(TABLE_HEADER_STYLE)
        .append(TABLE_CELL_PADDING)
        .append("text-align:")
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

  private enum DefectStatusCount {
    OPEN,
    CLOSED,
    TOTAL
  }

  private static class DefectStatusColumn {
    private final String label;
    private final List<String> types;

    DefectStatusColumn(String label, List<String> types) {
      this.label = Util.trimToEmpty(label);
      this.types = List.copyOf(types);
    }
  }

  private String escape(String value) {
    return Util.trimToEmpty(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static class EmailValueCellStyle {
    private final String bgcolor;
    private final String inlineCss;

    private EmailValueCellStyle(String bgcolor, String inlineCss) {
      this.bgcolor = Util.trimToEmpty(bgcolor);
      this.inlineCss = inlineCss;
    }

    private static EmailValueCellStyle defaultStyle() {
      return new EmailValueCellStyle("", "");
    }

    private static EmailValueCellStyle fallbackStyle() {
      return new EmailValueCellStyle("", "background-color:transparent;color:inherit;");
    }

    private static EmailValueCellStyle painted(String backgroundColor, String fontColor) {
      return new EmailValueCellStyle(
          backgroundColor, "background-color:" + backgroundColor + ";color:" + fontColor + ";");
    }
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
