package com.fep.communication.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CommunicationException.
 */
@DisplayName("CommunicationException Tests")
class CommunicationExceptionTest {

    @Test
    @DisplayName("Should create exception with error code and message")
    void shouldCreateExceptionWithCodeAndMessage() {
        CommunicationException ex = new CommunicationException("TEST001", "Test error message");

        assertThat(ex.getErrorCode()).isEqualTo("TEST001");
        assertThat(ex.getErrorMessage()).isEqualTo("Test error message");
        assertThat(ex.getMessage()).isEqualTo("Test error message");
    }

    @Test
    @DisplayName("Should create exception with error code, message and cause")
    void shouldCreateExceptionWithCodeMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        CommunicationException ex = new CommunicationException("TEST002", "Test error", cause);

        assertThat(ex.getErrorCode()).isEqualTo("TEST002");
        assertThat(ex.getErrorMessage()).isEqualTo("Test error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create connectionFailed exception")
    void shouldCreateConnectionFailedException() {
        Throwable cause = new RuntimeException("Connection refused");
        CommunicationException ex = CommunicationException.connectionFailed("localhost", 8080, cause);

        assertThat(ex.getErrorCode()).isEqualTo("COMM001");
        assertThat(ex.getErrorMessage()).contains("localhost").contains("8080");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create connectionTimeout exception")
    void shouldCreateConnectionTimeoutException() {
        CommunicationException ex = CommunicationException.connectionTimeout("192.168.1.1", 9000);

        assertThat(ex.getErrorCode()).isEqualTo("COMM002");
        assertThat(ex.getErrorMessage()).contains("192.168.1.1").contains("9000");
    }

    @Test
    @DisplayName("Should create sendFailed exception without cause")
    void shouldCreateSendFailedExceptionWithoutCause() {
        CommunicationException ex = CommunicationException.sendFailed("Message too large");

        assertThat(ex.getErrorCode()).isEqualTo("COMM003");
        assertThat(ex.getErrorMessage()).contains("Message too large");
    }

    @Test
    @DisplayName("Should create sendFailed exception with cause")
    void shouldCreateSendFailedExceptionWithCause() {
        Throwable cause = new RuntimeException("Network error");
        CommunicationException ex = CommunicationException.sendFailed("Transmission failed", cause);

        assertThat(ex.getErrorCode()).isEqualTo("COMM003");
        assertThat(ex.getErrorMessage()).contains("Transmission failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create receiveFailed exception without cause")
    void shouldCreateReceiveFailedExceptionWithoutCause() {
        CommunicationException ex = CommunicationException.receiveFailed("Invalid response");

        assertThat(ex.getErrorCode()).isEqualTo("COMM004");
        assertThat(ex.getErrorMessage()).contains("Invalid response");
    }

    @Test
    @DisplayName("Should create receiveFailed exception with cause")
    void shouldCreateReceiveFailedExceptionWithCause() {
        Throwable cause = new RuntimeException("Parse error");
        CommunicationException ex = CommunicationException.receiveFailed("Malformed data", cause);

        assertThat(ex.getErrorCode()).isEqualTo("COMM004");
        assertThat(ex.getErrorMessage()).contains("Malformed data");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create responseTimeout exception")
    void shouldCreateResponseTimeoutException() {
        CommunicationException ex = CommunicationException.responseTimeout();

        assertThat(ex.getErrorCode()).isEqualTo("COMM005");
        assertThat(ex.getErrorMessage()).contains("timeout");
    }

    @Test
    @DisplayName("Should create channelClosed exception without message")
    void shouldCreateChannelClosedExceptionWithoutMessage() {
        CommunicationException ex = CommunicationException.channelClosed();

        assertThat(ex.getErrorCode()).isEqualTo("COMM006");
        assertThat(ex.getErrorMessage()).contains("closed");
    }

    @Test
    @DisplayName("Should create channelClosed exception with message")
    void shouldCreateChannelClosedExceptionWithMessage() {
        CommunicationException ex = CommunicationException.channelClosed("Server shutdown");

        assertThat(ex.getErrorCode()).isEqualTo("COMM006");
        assertThat(ex.getErrorMessage()).contains("Server shutdown");
    }

    @Test
    @DisplayName("Should create poolExhausted exception")
    void shouldCreatePoolExhaustedException() {
        CommunicationException ex = CommunicationException.poolExhausted();

        assertThat(ex.getErrorCode()).isEqualTo("COMM007");
        assertThat(ex.getErrorMessage()).contains("pool").contains("exhausted");
    }

    @Test
    @DisplayName("Should create invalidState exception")
    void shouldCreateInvalidStateException() {
        CommunicationException ex = CommunicationException.invalidState("Cannot send while disconnected");

        assertThat(ex.getErrorCode()).isEqualTo("COMM008");
        assertThat(ex.getErrorMessage()).contains("Cannot send while disconnected");
    }

    @Test
    @DisplayName("Should create sendChannelDisconnected exception")
    void shouldCreateSendChannelDisconnectedException() {
        CommunicationException ex = CommunicationException.sendChannelDisconnected();

        assertThat(ex.getErrorCode()).isEqualTo("COMM009");
        assertThat(ex.getErrorMessage()).contains("Send channel").contains("disconnected");
    }

    @Test
    @DisplayName("Should create receiveChannelDisconnected exception")
    void shouldCreateReceiveChannelDisconnectedException() {
        CommunicationException ex = CommunicationException.receiveChannelDisconnected();

        assertThat(ex.getErrorCode()).isEqualTo("COMM010");
        assertThat(ex.getErrorMessage()).contains("Receive channel").contains("disconnected");
    }

    @Test
    @DisplayName("Should create bothChannelsDisconnected exception")
    void shouldCreateBothChannelsDisconnectedException() {
        CommunicationException ex = CommunicationException.bothChannelsDisconnected();

        assertThat(ex.getErrorCode()).isEqualTo("COMM011");
        assertThat(ex.getErrorMessage()).contains("Both").contains("disconnected");
    }

    @Test
    @DisplayName("Should create stanMismatch exception")
    void shouldCreateStanMismatchException() {
        CommunicationException ex = CommunicationException.stanMismatch("123456", "654321");

        assertThat(ex.getErrorCode()).isEqualTo("COMM012");
        assertThat(ex.getErrorMessage()).contains("123456").contains("654321");
    }

    @Test
    @DisplayName("Should create duplicateStan exception")
    void shouldCreateDuplicateStanException() {
        CommunicationException ex = CommunicationException.duplicateStan("999999");

        assertThat(ex.getErrorCode()).isEqualTo("COMM013");
        assertThat(ex.getErrorMessage()).contains("999999");
    }
}
