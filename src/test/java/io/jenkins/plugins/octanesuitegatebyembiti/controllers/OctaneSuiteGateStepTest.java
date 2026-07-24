package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import static org.junit.Assert.assertEquals;

import hudson.util.FormValidation;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneDefectGroup;
import java.util.List;
import org.junit.Test;

public class OctaneSuiteGateStepTest {
  @Test
  public void bindsDefectGroupsIntoGateRequest() {
    OctaneDefectGroup major = new OctaneDefectGroup("major");
    major.setTypes("Critical, Very High, High, Unspecified");
    OctaneSuiteGateStep step = new OctaneSuiteGateStep("octane-prod", "1196");
    step.setDefectGroups(List.of(major));

    GateRequest request = step.toRequest();

    assertEquals(1, request.getDefectGroups().size());
    assertEquals("major", request.getDefectGroups().get(0).getName());
    assertEquals(
        List.of("critical", "veryhigh", "high", "unspecified"),
        request.getDefectGroups().get(0).getNormalizedTypes());
  }

  @Test
  public void freestyleBuilderDelegatesDefectGroups() {
    OctaneDefectGroup minor = new OctaneDefectGroup("minor");
    minor.setTypes("Low, Medium");
    OctaneSuiteGateBuilder builder = new OctaneSuiteGateBuilder("octane-prod", "1196");
    builder.setDefectGroups(List.of(minor));

    assertEquals(1, builder.getDefectGroups().size());
    assertEquals("minor", builder.getDefectGroups().get(0).getName());
  }

  @Test
  public void bindsTesterDetailThresholdsIntoGateRequest() {
    OctaneSuiteGateStep step = new OctaneSuiteGateStep("octane-prod", "1196");
    step.setBasePassrateFigure(82);
    step.setBaseExecutionFigure(91);

    GateRequest request = step.toRequest();

    assertEquals(82, request.getBasePassrateFigure());
    assertEquals(91, request.getBaseExecutionFigure());
  }

  @Test
  public void freestyleBuilderDelegatesTesterDetailThresholds() {
    OctaneSuiteGateBuilder builder = new OctaneSuiteGateBuilder("octane-prod", "1196");
    builder.setBasePassrateFigure(84);
    builder.setBaseExecutionFigure(93);

    assertEquals(84, builder.getBasePassrateFigure());
    assertEquals(93, builder.getBaseExecutionFigure());
  }

  @Test
  public void extendedTimeoutValidationAcceptsBlankOrZero() {
    OctaneSuiteGateStep.DescriptorImpl descriptor = new OctaneSuiteGateStep.DescriptorImpl();

    assertEquals(FormValidation.Kind.OK, descriptor.doCheckTimeoutMinutesExtended("").kind);
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckTimeoutMinutesExtended(" ").kind);
    assertEquals(FormValidation.Kind.OK, descriptor.doCheckTimeoutMinutesExtended("0").kind);
    assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckTimeoutMinutesExtended("-1").kind);
  }
}
