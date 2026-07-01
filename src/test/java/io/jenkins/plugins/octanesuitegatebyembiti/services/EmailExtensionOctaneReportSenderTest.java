package io.jenkins.plugins.octanesuitegatebyembiti.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.AbortException;
import org.junit.jupiter.api.Test;

class EmailExtensionOctaneReportSenderTest {
  @Test
  void acceptsOutputWithoutAnEmailExtensionFailure() {
    assertDoesNotThrow(
        () ->
            EmailExtensionOctaneReportSender.verifySendOutput("Sending email to: qa@example.com"));
  }

  @Test
  void rejectsSmtpRetryFailureReportedByEmailExtension() {
    AbortException exception =
        assertThrows(
            AbortException.class,
            () ->
                EmailExtensionOctaneReportSender.verifySendOutput(
                    "SMTP connection error while sending email. Retrying once more in 10 seconds.\n"
                        + "Failed after second try sending email"));

    assertTrue(exception.getMessage().contains("did not send the message"));
    assertTrue(exception.getMessage().contains("Failed after second try sending email"));
  }

  @Test
  void rejectsPartialRecipientFailure() {
    assertThrows(
        AbortException.class,
        () ->
            EmailExtensionOctaneReportSender.verifySendOutput(
                "Not sent to the following valid addresses: qa@example.com"));
  }
}
