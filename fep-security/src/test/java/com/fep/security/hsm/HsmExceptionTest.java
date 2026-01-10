package com.fep.security.hsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HsmException.
 */
@DisplayName("HsmException Tests")
class HsmExceptionTest {

    @Test
    @DisplayName("Should create exception with message only")
    void shouldCreateExceptionWithMessageOnly() {
        HsmException ex = new HsmException("HSM connection failed");

        assertThat(ex.getMessage()).isEqualTo("HSM connection failed");
        assertThat(ex.getErrorCode()).isEqualTo("99");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with message and error code")
    void shouldCreateExceptionWithMessageAndErrorCode() {
        HsmException ex = new HsmException("Invalid key format", "01");

        assertThat(ex.getMessage()).isEqualTo("Invalid key format");
        assertThat(ex.getErrorCode()).isEqualTo("01");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("Network error");
        HsmException ex = new HsmException("HSM communication failed", cause);

        assertThat(ex.getMessage()).isEqualTo("HSM communication failed");
        assertThat(ex.getErrorCode()).isEqualTo("99");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should create exception with message, error code and cause")
    void shouldCreateExceptionWithMessageErrorCodeAndCause() {
        Throwable cause = new RuntimeException("Timeout");
        HsmException ex = new HsmException("Operation timed out", "05", cause);

        assertThat(ex.getMessage()).isEqualTo("Operation timed out");
        assertThat(ex.getErrorCode()).isEqualTo("05");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeRuntimeException() {
        HsmException ex = new HsmException("Test");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
