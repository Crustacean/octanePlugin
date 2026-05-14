package io.jenkins.plugins.octanesuitegatebyembiti.listeners;

import hudson.model.TaskListener;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateMetrics;
import io.jenkins.plugins.octanesuitegatebyembiti.models.GateResult;
import java.util.List;

public class OctaneGateLogListener {
  public void logWaiting(TaskListener listener, List<String> suiteRunIds) {
    listener
        .getLogger()
        .println("Waiting for ALM Octane suite run(s) " + String.join(", ", suiteRunIds) + ".");
  }

  public void logPollResult(TaskListener listener, GateResult result) {
    GateMetrics metrics = result.getMetrics();
    listener
        .getLogger()
        .printf(
            "Octane suite run %s: execution %.2f%%, pass %.2f%%, total %d, executed %d,"
                + " passed %d, failed %d, skipped %d, running %d.%n",
            result.getSuiteRunId(),
            metrics.getExecutionRate(),
            metrics.getPassRate(),
            metrics.getTotal(),
            metrics.getExecuted(),
            metrics.getPassed(),
            metrics.getFailed(),
            metrics.getSkipped(),
            metrics.getRunning());
  }

  public void logPassed(TaskListener listener) {
    listener.getLogger().println("ALM Octane suite gate passed.");
  }
}
