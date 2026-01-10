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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates field values against regex patterns.
 *
 * <p>Configuration format: {@code PATTERN:37=^[A-Z0-9]{12}$;7=^\d{10}$}
 */
@Slf4j
@Getter
public class PatternMatchRule implements ValidationRule {

    private final Map<Integer, Pattern> patterns;
    private final Map<Integer, String> patternStrings;

    /**
     * Creates a rule from pattern specifications.
     *
     * @param patternSpec e.g., "37=^[A-Z0-9]{12}$;7=^\d{10}$"
     */
    public PatternMatchRule(String patternSpec) {
        this.patterns = new HashMap<>();
        this.patternStrings = new HashMap<>();
        parsePatternSpec(patternSpec);
    }

    private void parsePatternSpec(String spec) {
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
                    String patternStr = part.substring(eqIndex + 1).trim();
                    Pattern pattern = Pattern.compile(patternStr);
                    patterns.put(fieldNum, pattern);
                    patternStrings.put(fieldNum, patternStr);
                } catch (NumberFormatException e) {
                    log.warn("[Pattern Rule] Invalid field number in: {}", part);
                } catch (PatternSyntaxException e) {
                    log.warn("[Pattern Rule] Invalid regex pattern in: {}", part);
                }
            }
        }
    }

    @Override
    public String getRuleType() {
        return "PATTERN";
    }

    @Override
    public List<ValidationError> validate(Iso8583Message message) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<Integer, Pattern> entry : patterns.entrySet()) {
            int fieldNum = entry.getKey();
            Pattern pattern = entry.getValue();

            if (!message.hasField(fieldNum)) {
                continue; // Skip missing fields
            }

            String value = message.getFieldAsString(fieldNum);
            if (value != null && !pattern.matcher(value).matches()) {
                errors.add(ValidationError.pattern(fieldNum,
                    patternStrings.get(fieldNum), value));
            }
        }

        return errors;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("Field patterns: ");
        patternStrings.forEach((field, pattern) ->
            sb.append("F").append(field).append("=").append(pattern).append("; "));
        return sb.toString();
    }
}
