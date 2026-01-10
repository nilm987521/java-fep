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

/**
 * Validates that field lengths are exactly as specified.
 *
 * <p>Configuration format: {@code LENGTH:4=12;11=6;37=12}
 */
@Slf4j
@Getter
public class FieldLengthRule implements ValidationRule {

    private final Map<Integer, Integer> expectedLengths;

    /**
     * Creates a rule from length specifications.
     *
     * @param lengthSpec e.g., "4=12;11=6;37=12"
     */
    public FieldLengthRule(String lengthSpec) {
        this.expectedLengths = new HashMap<>();
        parseLengthSpec(lengthSpec);
    }

    private void parseLengthSpec(String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }

        for (String part : spec.split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int eqIndex = part.indexOf('=');
            if (eqIndex > 0) {
                try {
                    int fieldNum = Integer.parseInt(part.substring(0, eqIndex).trim());
                    int length = Integer.parseInt(part.substring(eqIndex + 1).trim());
                    expectedLengths.put(fieldNum, length);
                } catch (NumberFormatException e) {
                    log.warn("[Length Rule] Invalid specification: {}", part);
                }
            }
        }
    }

    @Override
    public String getRuleType() {
        return "LENGTH";
    }

    @Override
    public List<ValidationError> validate(Iso8583Message message) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : expectedLengths.entrySet()) {
            int fieldNum = entry.getKey();
            int expectedLen = entry.getValue();

            if (!message.hasField(fieldNum)) {
                continue; // Skip missing fields
            }

            String value = message.getFieldAsString(fieldNum);
            if (value != null) {
                int actualLen = value.length();
                if (actualLen != expectedLen) {
                    errors.add(ValidationError.length(fieldNum, expectedLen, actualLen));
                }
            }
        }

        return errors;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("Field lengths: ");
        expectedLengths.forEach((field, length) ->
            sb.append("F").append(field).append("=").append(length).append("; "));
        return sb.toString();
    }
}
