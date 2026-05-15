package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import io.jenkins.plugins.octanesuitegatebyembiti.models.GateRequest;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import io.jenkins.plugins.octanesuitegatebyembiti.models.OctaneGateReportState;
import io.jenkins.plugins.octanesuitegatebyembiti.models.StatusClassifier;
import java.util.List;

public interface OctaneGateReportPublisher {
  default void onWaiting(GateRequest request, List<String> suiteRunIds) {}

  default void onPoll(GateResult result, StatusClassifier classifier) {}

  default void onFinal(
      OctaneGateReportState state,
      String message,
      GateResult result,
      StatusClassifier classifier) {}

  default void onError(String message, GateRequest request) {}
}
