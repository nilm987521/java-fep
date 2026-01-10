package com.fep.jmeter.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ValidationError.
 */
@DisplayName("ValidationError Tests")
class ValidationErrorTest {

    @Test
    @DisplayName("Should have all expected error types")
    void shouldHaveAllExpectedErrorTypes() {
        assertThat(ValidationError.ErrorType.values()).containsExactlyInAnyOrder(
            ValidationError.ErrorType.MISSING,
            ValidationError.ErrorType.FORMAT,
            ValidationError.ErrorType.VALUE,
            ValidationError.ErrorType.LENGTH,
            ValidationError.ErrorType.PATTERN
        );
    }

    @Test
    @DisplayName("Should create MISSING error for required field")
    void shouldCreateMissingError() {
        ValidationError error = ValidationError.missing(11);

        assertThat(error.getFieldNumber()).isEqualTo(11);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.MISSING);
        assertThat(error.getExpected()).isEqualTo("present");
        assertThat(error.getActual()).isEqualTo("missing");
        assertThat(error.getMessage()).contains("Required field 11 is missing");
    }

    @Test
    @DisplayName("Should create FORMAT error for invalid field format")
    void shouldCreateFormatError() {
        ValidationError error = ValidationError.format(4, "NUMERIC", "ALPHA123");

        assertThat(error.getFieldNumber()).isEqualTo(4);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.FORMAT);
        assertThat(error.getExpected()).isEqualTo("NUMERIC");
        assertThat(error.getActual()).isEqualTo("ALPHA123");
        assertThat(error.getMessage()).contains("Field 4 format invalid");
        assertThat(error.getMessage()).contains("expected NUMERIC");
        assertThat(error.getMessage()).contains("got ALPHA123");
    }

    @Test
    @DisplayName("Should create VALUE error for invalid field value")
    void shouldCreateValueError() {
        ValidationError error = ValidationError.value(3, "010000|400000", "999999");

        assertThat(error.getFieldNumber()).isEqualTo(3);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.VALUE);
        assertThat(error.getExpected()).isEqualTo("010000|400000");
        assertThat(error.getActual()).isEqualTo("999999");
        assertThat(error.getMessage()).contains("Field 3 value not allowed");
        assertThat(error.getMessage()).contains("999999");
        assertThat(error.getMessage()).contains("010000|400000");
    }

    @Test
    @DisplayName("Should create LENGTH error for incorrect field length")
    void shouldCreateLengthError() {
        ValidationError error = ValidationError.length(37, 12, 10);

        assertThat(error.getFieldNumber()).isEqualTo(37);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.LENGTH);
        assertThat(error.getExpected()).isEqualTo("12");
        assertThat(error.getActual()).isEqualTo("10");
        assertThat(error.getMessage()).contains("Field 37 length invalid");
        assertThat(error.getMessage()).contains("expected 12");
        assertThat(error.getMessage()).contains("got 10");
    }

    @Test
    @DisplayName("Should create PATTERN error for regex mismatch")
    void shouldCreatePatternError() {
        ValidationError error = ValidationError.pattern(41, "^[A-Z0-9]{8}$", "abc123");

        assertThat(error.getFieldNumber()).isEqualTo(41);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.PATTERN);
        assertThat(error.getExpected()).isEqualTo("^[A-Z0-9]{8}$");
        assertThat(error.getActual()).isEqualTo("abc123");
        assertThat(error.getMessage()).contains("Field 41 doesn't match pattern");
        assertThat(error.getMessage()).contains("^[A-Z0-9]{8}$");
    }

    @Test
    @DisplayName("Should create error using builder")
    void shouldCreateErrorUsingBuilder() {
        ValidationError error = ValidationError.builder()
            .fieldNumber(99)
            .errorType(ValidationError.ErrorType.FORMAT)
            .expected("expected value")
            .actual("actual value")
            .message("Custom error message")
            .build();

        assertThat(error.getFieldNumber()).isEqualTo(99);
        assertThat(error.getErrorType()).isEqualTo(ValidationError.ErrorType.FORMAT);
        assertThat(error.getExpected()).isEqualTo("expected value");
        assertThat(error.getActual()).isEqualTo("actual value");
        assertThat(error.getMessage()).isEqualTo("Custom error message");
    }

    @Test
    @DisplayName("Should create error with null field number for message-level errors")
    void shouldCreateErrorWithNullFieldNumber() {
        ValidationError error = ValidationError.builder()
            .fieldNumber(null)
            .errorType(ValidationError.ErrorType.FORMAT)
            .message("Message-level error")
            .build();

        assertThat(error.getFieldNumber()).isNull();
        assertThat(error.getMessage()).isEqualTo("Message-level error");
    }

    @Test
    @DisplayName("Should have toString representation")
    void shouldHaveToStringRepresentation() {
        ValidationError error = ValidationError.missing(11);

        String toString = error.toString();
        assertThat(toString).contains("ValidationError");
        assertThat(toString).contains("11");
        assertThat(toString).contains("MISSING");
    }

    @ParameterizedTest
    @EnumSource(ValidationError.ErrorType.class)
    @DisplayName("All error types should be parseable from name")
    void allErrorTypesShouldBeParseableFromName(ValidationError.ErrorType type) {
        assertThat(ValidationError.ErrorType.valueOf(type.name())).isEqualTo(type);
    }
}
