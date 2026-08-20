package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneEmailFailureMode;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneReportTheme;
import io.jenkins.plugins.octanesuitegatebyembiti.utils.Util;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

abstract class AbstractOctaneEmailStep extends Step {
  private final String to;
  private String cc = "";
  private String bcc = "";
  private String subject = "";
  private String body = "";
  private String projectName = "";
  private String domainName = "";
  private String from = "";
  private String replyTo = "";
  private String onFailure;
  private String browserPath = "";
  private String theme = OctaneReportTheme.LIGHT.name();
  private int viewportWidth = OctaneEmailReportStep.DEFAULT_VIEWPORT_WIDTH;
  private boolean archiveScreenshot;
  private boolean printDefectGroups;
  private boolean important;

  AbstractOctaneEmailStep(String to, OctaneEmailFailureMode onFailure, boolean archiveScreenshot) {
    this.to = Util.trimToEmpty(to);
    this.onFailure = onFailure.name();
    this.archiveScreenshot = archiveScreenshot;
  }

  public String getTo() {
    return to;
  }

  public String getCc() {
    return cc;
  }

  @DataBoundSetter
  public void setCc(String cc) {
    this.cc = Util.trimToEmpty(cc);
  }

  public String getBcc() {
    return bcc;
  }

  @DataBoundSetter
  public void setBcc(String bcc) {
    this.bcc = Util.trimToEmpty(bcc);
  }

  public String getSubject() {
    return subject;
  }

  @DataBoundSetter
  public void setSubject(String subject) {
    this.subject = Util.trimToEmpty(subject);
  }

  public String getBody() {
    return body;
  }

  @DataBoundSetter
  public void setBody(String body) {
    this.body = Util.trimToEmpty(body);
  }

  public String getProjectName() {
    return projectName;
  }

  @DataBoundSetter
  public void setProjectName(String projectName) {
    this.projectName = Util.trimToEmpty(projectName);
  }

  public String getDomainName() {
    return domainName;
  }

  @DataBoundSetter
  public void setDomainName(String domainName) {
    this.domainName = Util.trimToEmpty(domainName);
  }

  public String getFrom() {
    return from;
  }

  @DataBoundSetter
  public void setFrom(String from) {
    this.from = Util.trimToEmpty(from);
  }

  public String getReplyTo() {
    return replyTo;
  }

  @DataBoundSetter
  public void setReplyTo(String replyTo) {
    this.replyTo = Util.trimToEmpty(replyTo);
  }

  public String getOnFailure() {
    return onFailure;
  }

  @DataBoundSetter
  public void setOnFailure(String onFailure) {
    this.onFailure = OctaneEmailFailureMode.normalize(onFailure);
  }

  public String getBrowserPath() {
    return browserPath;
  }

  @DataBoundSetter
  public void setBrowserPath(String browserPath) {
    this.browserPath = Util.trimToEmpty(browserPath);
  }

  public String getTheme() {
    return theme;
  }

  @DataBoundSetter
  public void setTheme(String theme) {
    this.theme = OctaneReportTheme.normalize(theme);
  }

  public int getViewportWidth() {
    return viewportWidth;
  }

  @DataBoundSetter
  public void setViewportWidth(int viewportWidth) {
    this.viewportWidth = Math.max(320, viewportWidth);
  }

  public boolean isArchiveScreenshot() {
    return archiveScreenshot;
  }

  @DataBoundSetter
  public void setArchiveScreenshot(boolean archiveScreenshot) {
    this.archiveScreenshot = archiveScreenshot;
  }

  public boolean isPrintDefectGroups() {
    return printDefectGroups;
  }

  @DataBoundSetter
  public void setPrintDefectGroups(boolean printDefectGroups) {
    this.printDefectGroups = printDefectGroups;
  }

  public boolean isImportant() {
    return important;
  }

  @DataBoundSetter
  public void setImportant(boolean important) {
    this.important = important;
  }

  final OctaneEmailReportStep.EmailRequest toRequest() {
    return new OctaneEmailReportStep.EmailRequest(
        to,
        cc,
        bcc,
        subject,
        body,
        projectName,
        domainName,
        from,
        replyTo,
        onFailure,
        browserPath,
        theme,
        viewportWidth,
        archiveScreenshot,
        printDefectGroups,
        important);
  }
}
