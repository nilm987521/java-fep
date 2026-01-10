package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.jmeter.validation.ValidationRule;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates field format (data type and length).
 *
 * <p>Configuration format: {@code FORMAT:2=N(13-19);3=N(6);4=N(12);41=AN(8)}
 *
 * <p>Supported types:
 * <ul>
 *   <li>N - Numeric (digits only)</li>
 *   <li>A - Alpha (letters only)</li>
 *   <li>AN - Alphanumeric</li>
 *   <li>ANS - Alphanumeric with special characters</li>
 *   <li>B - Binary</li>
 * </ul>
 *
 * <p>Length formats:
 * <ul>
 *   <li>N(6) - Exactly 6 characters</li>
 *   <li>N(6-12) - Between 6 and 12 characters</li>
 *   <li>N(..12) - Up to 12 characters</li>
 * </ul>
 */
@Slf4j
@Getter
public class FieldFormatRule implements ValidationRule {

    private static final Pattern FORMAT_PATTERN = Pattern.compile(
        "(\\d+)=([A-Z]+)\\((?:(\\d+)(?:-(\\d+))?|\\.\\.(\\d+))\\)");

    private final Map<Integer, FieldFormat> formats;

    /**
     * Creates a rule from format specifications.
     *
     * @param formatSpec e.g., "2=N(13-19);3=N(6);4=N(12);41=AN(8)"
     */
    public FieldFormatRule(String formatSpec) {
        this.formats = new HashMap<>();
        parseFormatSpec(formatSpec);
    }

    private void parseFormatSpec(String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }

        for (String part : spec.split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            Matcher matcher = FORMAT_PATTERN.matcher(part);
            if (matcher.matches()) {
                int fieldNum = Integer.parseInt(matcher.group(1));
                String type = matcher.group(2);
                int minLen, maxLen;

                if (matcher.group(5) != null) {
                    // Format: ..12 (up to 12)
                    minLen = 0;
                    maxLen = Integer.parseInt(matcher.group(5));
                } else if (matcher.group(4) != null) {
                    // Format: 6-12 (range)
                    minLen = Integer.parseInt(matcher.group(3));
                    maxLen = Integer.parseInt(matcher.group(4));
                } else {
                    // Format: 6 (exact)
                    minLen = Integer.parseInt(matcher.group(3));
                    maxLen = minLen;
                }

                formats.put(fieldNum, new FieldFormat(type, minLen, maxLen));
            } else {
                log.warn("[Format Rule] Invalid format specification: {}", part);
            }
        }
    }

    @Override
    public String getRuleType() {
        return "FORMAT";
    }

    @Override
    public List<ValidationError> validate(Iso8583Message message) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<Integer, FieldFormat> entry : formats.entrySet()) {
            int fieldNum = entry.getKey();
            FieldFormat format = entry.getValue();

            if (!message.hasField(fieldNum)) {
                continue; // Skip missing fields (handled by RequiredFieldRule)
            }

            String value = message.getFieldAsString(fieldNum);
            if (value == null) {
                continue;
            }

            // Check length
            int len = value.length();
            if (len < format.minLen || len > format.maxLen) {
                String expected = format.minLen == format.maxLen ?
                    String.valueOf(format.minLen) :
                    format.minLen + "-" + format.maxLen;
                errors.add(ValidationError.format(fieldNum,
                    format.type + "(" + expected + ")",
                    format.type + "(" + len + ")"));
                continue;
            }

            // Check type
            if (!matchesType(value, format.type)) {
                errors.add(ValidationError.format(fieldNum,
                    format.type, getActualType(value)));
            }
        }

        return errors;
    }

    private boolean matchesType(String value, String type) {
        return switch (type) {
            case "N" -> value.matches("\\d*");
            case "A" -> value.matches("[a-zA-Z]*");
            case "AN" -> value.matches("[a-zA-Z0-9]*");
            case "ANS" -> true; // Allow any printable character
            case "B" -> true; // Binary data - allow any
            default -> true;
        };
    }

    private String getActualType(String value) {
        if (value.matches("\\d*")) return "N";
        if (value.matches("[a-zA-Z]*")) return "A";
        if (value.matches("[a-zA-Z0-9]*")) return "AN";
        return "ANS";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("Field formats: ");
        formats.forEach((field, format) ->
            sb.append("F").append(field).append("=").append(format).append("; "));
        return sb.toString();
    }

    private record FieldFormat(String type, int minLen, int maxLen) {
        @Override
        public String toString() {
            if (minLen == maxLen) {
                return type + "(" + minLen + ")";
            }
            return type + "(" + minLen + "-" + maxLen + ")";
        }
    }
}
