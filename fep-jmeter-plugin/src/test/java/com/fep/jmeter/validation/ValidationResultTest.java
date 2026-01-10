package com.fep.jmeter.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ValidationResult.
 */
@DisplayName("ValidationResult Tests")
class ValidationResultTest {

    @Test
    @DisplayName("Should create successful validation result")
    void shouldCreateSuccessfulValidationResult() {
        ValidationResult result = ValidationResult.success(50L);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getErrorCount()).isZero();
        assertThat(result.getValidationTimeMs()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should create failed validation result with errors")
    void shouldCreateFailedValidationResult() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.length(4, 12, 10)
        );

        ValidationResult result = ValidationResult.failure(errors, 100L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrorCount()).isEqualTo(2);
        assertThat(result.getValidationTimeMs()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should return errors as unmodifiable list")
    void shouldReturnErrorsAsUnmodifiableList() {
        List<ValidationError> errors = Arrays.asList(ValidationError.missing(11));
        ValidationResult result = ValidationResult.failure(errors, 50L);

        assertThatThrownBy(() -> result.getErrors().add(ValidationError.missing(12)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should get errors for specific field")
    void shouldGetErrorsForSpecificField() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.length(11, 6, 5),
            ValidationError.missing(41)
        );

        ValidationResult result = ValidationResult.failure(errors, 50L);

        List<ValidationError> field11Errors = result.getErrorsForField(11);
        assertThat(field11Errors).hasSize(2);
        assertThat(field11Errors).allMatch(e -> e.getFieldNumber() == 11);

        List<ValidationError> field41Errors = result.getErrorsForField(41);
        assertThat(field41Errors).hasSize(1);

        List<ValidationError> field99Errors = result.getErrorsForField(99);
        assertThat(field99Errors).isEmpty();
    }

    @Test
    @DisplayName("Should get errors by type")
    void shouldGetErrorsByType() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.missing(41),
            ValidationError.length(4, 12, 10),
            ValidationError.format(3, "NUMERIC", "ABC")
        );

        ValidationResult result = ValidationResult.failure(errors, 50L);

        List<ValidationError> missingErrors = result.getErrorsByType(ValidationError.ErrorType.MISSING);
        assertThat(missingErrors).hasSize(2);

        List<ValidationError> lengthErrors = result.getErrorsByType(ValidationError.ErrorType.LENGTH);
        assertThat(lengthErrors).hasSize(1);

        List<ValidationError> formatErrors = result.getErrorsByType(ValidationError.ErrorType.FORMAT);
        assertThat(formatErrors).hasSize(1);

        List<ValidationError> valueErrors = result.getErrorsByType(ValidationError.ErrorType.VALUE);
        assertThat(valueErrors).isEmpty();
    }

    @Test
    @DisplayName("Should get error summary for passed validation")
    void shouldGetErrorSummaryForPassedValidation() {
        ValidationResult result = ValidationResult.success(50L);

        assertThat(result.getErrorSummary()).isEqualTo("Validation passed");
    }

    @Test
    @DisplayName("Should get error summary for failed validation")
    void shouldGetErrorSummaryForFailedValidation() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.length(4, 12, 10)
        );

        ValidationResult result = ValidationResult.failure(errors, 50L);
        String summary = result.getErrorSummary();

        assertThat(summary).contains("Required field 11 is missing");
        assertThat(summary).contains("Field 4 length invalid");
        assertThat(summary).contains(";"); // Errors separated by semicolon
    }

    @Test
    @DisplayName("Should get detailed report for passed validation")
    void shouldGetDetailedReportForPassedValidation() {
        ValidationResult result = ValidationResult.success(50L);
        String report = result.getDetailedReport();

        assertThat(report).contains("Validation Result: PASS");
        assertThat(report).contains("Validation Time: 50 ms");
        assertThat(report).doesNotContain("Errors");
    }

    @Test
    @DisplayName("Should get detailed report for failed validation")
    void shouldGetDetailedReportForFailedValidation() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.length(4, 12, 10)
        );

        ValidationResult result = ValidationResult.failure(errors, 100L);
        String report = result.getDetailedReport();

        assertThat(report).contains("Validation Result: FAIL");
        assertThat(report).contains("Validation Time: 100 ms");
        assertThat(report).contains("Errors (2):");
        assertThat(report).contains("[MISSING]");
        assertThat(report).contains("[LENGTH]");
        assertThat(report).contains("F11:");
        assertThat(report).contains("F4:");
    }

    @Test
    @DisplayName("Should have proper toString for passed validation")
    void shouldHaveProperToStringForPassedValidation() {
        ValidationResult result = ValidationResult.success(50L);

        assertThat(result.toString()).isEqualTo("ValidationResult{PASS}");
    }

    @Test
    @DisplayName("Should have proper toString for failed validation")
    void shouldHaveProperToStringForFailedValidation() {
        List<ValidationError> errors = Arrays.asList(
            ValidationError.missing(11),
            ValidationError.length(4, 12, 10)
        );

        ValidationResult result = ValidationResult.failure(errors, 50L);

        assertThat(result.toString()).isEqualTo("ValidationResult{FAIL, errors=2}");
    }

    @Test
    @DisplayName("Should handle errors with null field number in detailed report")
    void shouldHandleErrorsWithNullFieldNumberInDetailedReport() {
        ValidationError messageLevelError = ValidationError.builder()
            .fieldNumber(null)
            .errorType(ValidationError.ErrorType.FORMAT)
            .message("Message-level format error")
            .build();

        ValidationResult result = ValidationResult.failure(
            Collections.singletonList(messageLevelError), 50L);
        String report = result.getDetailedReport();

        assertThat(report).contains("[FORMAT]");
        assertThat(report).contains("Message-level format error");
        assertThat(report).doesNotContain("Fnull:");
    }

    @Test
    @DisplayName("Should create failure result that does not modify original list")
    void shouldCreateFailureResultThatDoesNotModifyOriginalList() {
        List<ValidationError> originalErrors = new java.util.ArrayList<>();
        originalErrors.add(ValidationError.missing(11));

        ValidationResult result = ValidationResult.failure(originalErrors, 50L);

        // Modify original list
        originalErrors.add(ValidationError.missing(41));

        // Result should still have only 1 error
        assertThat(result.getErrorCount()).isEqualTo(1);
    }
}
