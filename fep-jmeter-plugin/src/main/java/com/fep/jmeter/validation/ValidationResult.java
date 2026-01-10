package com.fep.jmeter.validation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of message validation containing all errors found.
 */
@Getter
public class ValidationResult {

    private final List<ValidationError> errors;
    private final long validationTimeMs;

    private ValidationResult(List<ValidationError> errors, long validationTimeMs) {
        this.errors = Collections.unmodifiableList(errors);
        this.validationTimeMs = validationTimeMs;
    }

    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success(long validationTimeMs) {
        return new ValidationResult(Collections.emptyList(), validationTimeMs);
    }

    /**
     * Creates a failed validation result with errors.
     */
    public static ValidationResult failure(List<ValidationError> errors, long validationTimeMs) {
        return new ValidationResult(new ArrayList<>(errors), validationTimeMs);
    }

    /**
     * Checks if validation passed (no errors).
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Gets the number of errors.
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Gets errors for a specific field.
     */
    public List<ValidationError> getErrorsForField(int fieldNumber) {
        return errors.stream()
            .filter(e -> e.getFieldNumber() != null && e.getFieldNumber() == fieldNumber)
            .collect(Collectors.toList());
    }

    /**
     * Gets errors of a specific type.
     */
    public List<ValidationError> getErrorsByType(ValidationError.ErrorType type) {
        return errors.stream()
            .filter(e -> e.getErrorType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Gets a summary of all errors as a single string.
     */
    public String getErrorSummary() {
        if (errors.isEmpty()) {
            return "Validation passed";
        }
        return errors.stream()
            .map(ValidationError::getMessage)
            .collect(Collectors.joining("; "));
    }

    /**
     * Gets a detailed report of the validation.
     */
    public String getDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Result: ").append(isValid() ? "PASS" : "FAIL").append("\n");
        sb.append("Validation Time: ").append(validationTimeMs).append(" ms\n");

        if (!errors.isEmpty()) {
            sb.append("Errors (").append(errors.size()).append("):\n");
            for (int i = 0; i < errors.size(); i++) {
                ValidationError error = errors.get(i);
                sb.append("  ").append(i + 1).append(". ");
                sb.append("[").append(error.getErrorType()).append("] ");
                if (error.getFieldNumber() != null) {
                    sb.append("F").append(error.getFieldNumber()).append(": ");
                }
                sb.append(error.getMessage()).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return isValid() ? "ValidationResult{PASS}" :
            "ValidationResult{FAIL, errors=" + errors.size() + "}";
    }
}
