package com.fep.settlement.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of file validation before parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileValidationResult {

    /** Whether the file is valid */
    @Builder.Default
    private boolean valid = true;

    /** List of validation errors */
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    /** List of validation warnings */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** File size in bytes */
    private long fileSizeBytes;

    /** Number of lines in file */
    private int lineCount;

    /** Detected encoding */
    private String detectedEncoding;

    /** Detected file type */
    private String detectedFileType;

    /**
     * Add an error.
     */
    public void addError(String message, int lineNumber) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(ValidationError.builder()
                .message(message)
                .lineNumber(lineNumber)
                .build());
        this.valid = false;
    }

    /**
     * Add an error without line number.
     */
    public void addError(String message) {
        addError(message, -1);
    }

    /**
     * Add a warning.
     */
    public void addWarning(String message) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(message);
    }

    /**
     * Check if there are any errors.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Check if there are any warnings.
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Create a success result.
     */
    public static FileValidationResult success() {
        return FileValidationResult.builder().valid(true).build();
    }

    /**
     * Create a failure result.
     */
    public static FileValidationResult failure(String errorMessage) {
        FileValidationResult result = new FileValidationResult();
        result.setValid(false);
        result.addError(errorMessage);
        return result;
    }

    /**
     * Validation error detail.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String message;
        private int lineNumber;
        private String fieldName;
        private String expectedValue;
        private String actualValue;
    }
}
