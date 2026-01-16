package com.fep.jmeter.assertion;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Field matcher supporting multiple matching modes for assertion validation.
 *
 * <p>Supports the following operators:
 * <ul>
 *   <li>{@code $eq} - Exact match (default)</li>
 *   <li>{@code $ne} - Not equal</li>
 *   <li>{@code $regex} - Regular expression match</li>
 *   <li>{@code $contains} - Contains substring</li>
 *   <li>{@code $startsWith} - Starts with prefix</li>
 *   <li>{@code $endsWith} - Ends with suffix</li>
 * </ul>
 *
 * <p>Usage examples:
 * <pre>
 * // Simple value (defaults to $eq)
 * "responseCode": "00"
 *
 * // With operator
 * "amount": { "$regex": "^\\d{12}$" }
 * "terminalId": { "$contains": "ATM" }
 * </pre>
 */
@Slf4j
public class FieldMatcher {

    private final String fieldId;
    private final MatchOperator operator;
    private final String expectedValue;

    /**
     * Match operators.
     */
    public enum MatchOperator {
        EQ("$eq"),
        NE("$ne"),
        REGEX("$regex"),
        CONTAINS("$contains"),
        STARTS_WITH("$startsWith"),
        ENDS_WITH("$endsWith");

        private final String symbol;

        MatchOperator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public static MatchOperator fromSymbol(String symbol) {
            for (MatchOperator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            return null;
        }
    }

    /**
     * Creates a FieldMatcher from a JSON value.
     *
     * @param fieldId the field ID being matched
     * @param value   the expected value (String or Map with operator)
     * @return a FieldMatcher instance
     */
    @SuppressWarnings("unchecked")
    public static FieldMatcher fromValue(String fieldId, Object value) {
        if (value == null) {
            return new FieldMatcher(fieldId, MatchOperator.EQ, null);
        }

        if (value instanceof String strValue) {
            // Simple string value - use exact match
            return new FieldMatcher(fieldId, MatchOperator.EQ, strValue);
        }

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;

            // Find operator in map
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                MatchOperator op = MatchOperator.fromSymbol(key);
                if (op != null) {
                    Object opValue = entry.getValue();
                    String strOpValue = opValue != null ? opValue.toString() : null;
                    return new FieldMatcher(fieldId, op, strOpValue);
                }
            }

            // No operator found, treat as error
            log.warn("No valid operator found in map for field [{}]: {}", fieldId, map);
            return new FieldMatcher(fieldId, MatchOperator.EQ, map.toString());
        }

        // Other types - convert to string
        return new FieldMatcher(fieldId, MatchOperator.EQ, value.toString());
    }

    /**
     * Creates a FieldMatcher with specified operator and expected value.
     *
     * @param fieldId       the field ID
     * @param operator      the match operator
     * @param expectedValue the expected value
     */
    public FieldMatcher(String fieldId, MatchOperator operator, String expectedValue) {
        this.fieldId = fieldId;
        this.operator = operator;
        this.expectedValue = expectedValue;
    }

    /**
     * Matches the actual value against the expected value using the configured operator.
     *
     * @param actualValue the actual value from the response
     * @return a MatchResult indicating success or failure
     */
    public MatchResult match(String actualValue) {
        if (expectedValue == null && actualValue == null) {
            return MatchResult.success();
        }

        if (expectedValue == null) {
            return MatchResult.failure(fieldId, operator,
                    "expected null", "actual: " + actualValue);
        }

        if (actualValue == null) {
            return MatchResult.failure(fieldId, operator,
                    expectedValue, "(field not present or null)");
        }

        return switch (operator) {
            case EQ -> matchEquals(actualValue);
            case NE -> matchNotEquals(actualValue);
            case REGEX -> matchRegex(actualValue);
            case CONTAINS -> matchContains(actualValue);
            case STARTS_WITH -> matchStartsWith(actualValue);
            case ENDS_WITH -> matchEndsWith(actualValue);
        };
    }

    private MatchResult matchEquals(String actualValue) {
        if (expectedValue.equals(actualValue)) {
            return MatchResult.success();
        }
        return MatchResult.failure(fieldId, operator, expectedValue, actualValue);
    }

    private MatchResult matchNotEquals(String actualValue) {
        if (!expectedValue.equals(actualValue)) {
            return MatchResult.success();
        }
        return MatchResult.failure(fieldId, operator,
                "not " + expectedValue, actualValue);
    }

    private MatchResult matchRegex(String actualValue) {
        try {
            Pattern pattern = Pattern.compile(expectedValue);
            if (pattern.matcher(actualValue).matches()) {
                return MatchResult.success();
            }
            return MatchResult.failure(fieldId, operator,
                    "regex: " + expectedValue, actualValue);
        } catch (PatternSyntaxException e) {
            return MatchResult.failure(fieldId, operator,
                    "invalid regex: " + expectedValue, "PatternSyntaxException: " + e.getMessage());
        }
    }

    private MatchResult matchContains(String actualValue) {
        if (actualValue.contains(expectedValue)) {
            return MatchResult.success();
        }
        return MatchResult.failure(fieldId, operator,
                "contains: " + expectedValue, actualValue);
    }

    private MatchResult matchStartsWith(String actualValue) {
        if (actualValue.startsWith(expectedValue)) {
            return MatchResult.success();
        }
        return MatchResult.failure(fieldId, operator,
                "startsWith: " + expectedValue, actualValue);
    }

    private MatchResult matchEndsWith(String actualValue) {
        if (actualValue.endsWith(expectedValue)) {
            return MatchResult.success();
        }
        return MatchResult.failure(fieldId, operator,
                "endsWith: " + expectedValue, actualValue);
    }

    public String getFieldId() {
        return fieldId;
    }

    public MatchOperator getOperator() {
        return operator;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    /**
     * Result of a field match operation.
     */
    public static class MatchResult {
        private final boolean success;
        private final String fieldId;
        private final MatchOperator operator;
        private final String expected;
        private final String actual;

        private MatchResult(boolean success, String fieldId, MatchOperator operator,
                           String expected, String actual) {
            this.success = success;
            this.fieldId = fieldId;
            this.operator = operator;
            this.expected = expected;
            this.actual = actual;
        }

        public static MatchResult success() {
            return new MatchResult(true, null, null, null, null);
        }

        public static MatchResult failure(String fieldId, MatchOperator operator,
                                          String expected, String actual) {
            return new MatchResult(false, fieldId, operator, expected, actual);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureMessage() {
            if (success) {
                return null;
            }
            return String.format("Field [%s] mismatch (%s): expected [%s], actual [%s]",
                    fieldId, operator.getSymbol(), expected, actual);
        }

        public String getFieldId() {
            return fieldId;
        }

        public String getExpected() {
            return expected;
        }

        public String getActual() {
            return actual;
        }
    }
}
