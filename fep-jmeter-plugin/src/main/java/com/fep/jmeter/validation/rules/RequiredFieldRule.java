package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.jmeter.validation.ValidationRule;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that required fields are present in the message.
 *
 * <p>Configuration format: {@code REQUIRED:2,3,4,11,41,42}
 */
@Getter
public class RequiredFieldRule implements ValidationRule {

    private final Set<Integer> requiredFields;

    /**
     * Creates a rule with specified required fields.
     *
     * @param fields the field numbers that must be present
     */
    public RequiredFieldRule(Integer... fields) {
        this.requiredFields = Arrays.stream(fields).collect(Collectors.toSet());
    }

    /**
     * Creates a rule from a comma-separated string of field numbers.
     *
     * @param fieldList e.g., "2,3,4,11,41,42"
     */
    public RequiredFieldRule(String fieldList) {
        this.requiredFields = Arrays.stream(fieldList.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .collect(Collectors.toSet());
    }

    @Override
    public String getRuleType() {
        return "REQUIRED";
    }

    @Override
    public List<ValidationError> validate(Iso8583Message message) {
        List<ValidationError> errors = new ArrayList<>();

        for (Integer fieldNum : requiredFields) {
            if (!message.hasField(fieldNum)) {
                errors.add(ValidationError.missing(fieldNum));
            } else {
                // Also check if field value is empty
                String value = message.getFieldAsString(fieldNum);
                if (value == null || value.isEmpty()) {
                    errors.add(ValidationError.missing(fieldNum));
                }
            }
        }

        return errors;
    }

    @Override
    public String getDescription() {
        return "Required fields: " + requiredFields.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
    }
}
