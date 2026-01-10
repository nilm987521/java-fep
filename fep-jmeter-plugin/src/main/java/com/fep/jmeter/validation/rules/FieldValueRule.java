package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.jmeter.validation.ValidationRule;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that field values are within an allowed set.
 *
 * <p>Configuration format: {@code VALUE:3=010000|400000|310000}
 */
@Slf4j
@Getter
public class FieldValueRule implements ValidationRule {

    private final Map<Integer, Set<String>> allowedValues;

    /**
     * Creates a rule from value specifications.
     *
     * @param valueSpec e.g., "3=010000|400000|310000;70=001|101|301"
     */
    public FieldValueRule(String valueSpec) {
        this.allowedValues = new HashMap<>();
        parseValueSpec(valueSpec);
    }

    private void parseValueSpec(String spec) {
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
                    String valuesStr = part.substring(eqIndex + 1).trim();
                    Set<String> values = Arrays.stream(valuesStr.split("\\|"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                    allowedValues.put(fieldNum, values);
                } catch (NumberFormatException e) {
                    log.warn("[Value Rule] Invalid field number in: {}", part);
                }
            }
        }
    }

    @Override
    public String getRuleType() {
        return "VALUE";
    }

    @Override
    public List<ValidationError> validate(Iso8583Message message) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<Integer, Set<String>> entry : allowedValues.entrySet()) {
            int fieldNum = entry.getKey();
            Set<String> allowed = entry.getValue();

            if (!message.hasField(fieldNum)) {
                continue; // Skip missing fields
            }

            String value = message.getFieldAsString(fieldNum);
            if (value != null && !allowed.contains(value)) {
                errors.add(ValidationError.value(fieldNum,
                    String.join("|", allowed), value));
            }
        }

        return errors;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("Allowed values: ");
        allowedValues.forEach((field, values) ->
            sb.append("F").append(field).append("=").append(String.join("|", values)).append("; "));
        return sb.toString();
    }
}
