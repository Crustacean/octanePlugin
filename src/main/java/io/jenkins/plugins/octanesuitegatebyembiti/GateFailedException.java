package io.jenkins.plugins.octanesuitegatebyembiti;

import hudson.AbortException;

class GateFailedException extends AbortException {
  private static final long serialVersionUID = 1L;

  private final transient GateResult result;

  GateFailedException(String message, GateResult result) {
    super(message);
    this.result = result;
  }

  GateResult getResult() {
    return result;
  }
}
