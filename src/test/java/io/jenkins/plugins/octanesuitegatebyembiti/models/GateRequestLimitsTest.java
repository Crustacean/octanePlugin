package io.jenkins.plugins.octanesuitegatebyembiti.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GateRequestLimitsTest {
  @Test
  public void clampsUserControlledPollingAndRetentionLimits() {
    GateRequest request = new GateRequest("server", "1");

    request.setPollIntervalSeconds(Integer.MAX_VALUE);
    request.setTimeoutMinutes(Integer.MAX_VALUE);
    request.setTimeoutMinutesExtended(Integer.MAX_VALUE);
    request.setRiskHeatMapMaxDefects(Integer.MAX_VALUE);

    assertEquals(GateRequest.MAX_POLL_INTERVAL_SECONDS, request.getPollIntervalSeconds());
    assertEquals(GateRequest.MAX_TIMEOUT_MINUTES, request.getTimeoutMinutes());
    assertEquals(GateRequest.MAX_TIMEOUT_MINUTES, request.getTimeoutMinutesExtended());
    assertEquals(GateRequest.MAX_RISK_HEAT_MAP_DEFECTS, request.getRiskHeatMapMaxDefects());
  }
}
