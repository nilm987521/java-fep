package com.fep.jmeter.engine;

import com.fep.message.iso8583.Iso8583Message;
import org.apache.jmeter.threads.JMeterVariables;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a single MTI response rule.
 *
 * <p>A rule consists of:
 * <ul>
 *   <li>A condition that determines when this rule applies</li>
 *   <li>Response fields to set in the response message</li>
 * </ul>
 *
 * <p>Supports variable substitution in response field values:
 * <ul>
 *   <li>${VAR_NAME} - JMeter variable</li>
 *   <li>${Fnn} - Request field value (e.g., ${F11} for STAN)</li>
 *   <li>${STAN} - Alias for ${F11}</li>
 *   <li>${RRN} - Alias for ${F37}</li>
 * </ul>
 *
 * @param condition the condition for this rule
 * @param responseFields map of field number to value (with variable placeholders)
 */
public record MtiRule(
    RuleCondition condition,
    Map<Integer, String> responseFields
) {
    // Pattern to match ${...} variables
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    // Field aliases
    private static final Map<String, Integer> FIELD_ALIASES = Map.of(
        "STAN", 11,
        "RRN", 37,
        "PAN", 2,
        "AMOUNT", 4,
        "PROC_CODE", 3
    );

    /**
     * Checks if this rule matches the given request.
     *
     * @param request the incoming message
     * @return true if the condition matches
     */
    public boolean matches(Iso8583Message request) {
        return condition.matches(request);
    }

    /**
     * Creates a response message based on this rule.
     *
     * @param request the original request message
     * @param vars JMeter variables for substitution (may be null)
     * @return the response message with configured fields
     */
    public Iso8583Message createResponse(Iso8583Message request, JMeterVariables vars) {
        Iso8583Message response = request.createResponse();

        responseFields.forEach((field, value) -> {
            String resolvedValue = resolveVariables(value, vars, request);
            response.setField(field, resolvedValue);
        });

        return response;
    }

    /**
     * Resolves variables in the given value string.
     *
     * @param value the value with variable placeholders
     * @param vars JMeter variables
     * @param request the request message for field references
     * @return the resolved value
     */
    private String resolveVariables(String value, JMeterVariables vars, Iso8583Message request) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = resolveVariable(varName, vars, request);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a single variable reference.
     *
     * @param varName the variable name (without ${})
     * @param vars JMeter variables
     * @param request the request message
     * @return the resolved value, or original placeholder if not found
     */
    private String resolveVariable(String varName, JMeterVariables vars, Iso8583Message request) {
        // Check for field alias (STAN, RRN, etc.)
        Integer aliasField = FIELD_ALIASES.get(varName.toUpperCase());
        if (aliasField != null) {
            String fieldValue = request.getFieldAsString(aliasField);
            return fieldValue != null ? fieldValue : "";
        }

        // Check for field reference (Fnn format)
        if (varName.toUpperCase().startsWith("F") && varName.length() > 1) {
            try {
                int fieldNum = Integer.parseInt(varName.substring(1));
                String fieldValue = request.getFieldAsString(fieldNum);
                return fieldValue != null ? fieldValue : "";
            } catch (NumberFormatException ignored) {
                // Not a field reference, continue to JMeter variable check
            }
        }

        // Check JMeter variables
        if (vars != null) {
            String jmeterValue = vars.get(varName);
            if (jmeterValue != null) {
                return jmeterValue;
            }
        }

        // Return empty string if not found
        return "";
    }

    @Override
    public String toString() {
        return "MtiRule{condition=" + condition + ", fields=" + responseFields.size() + "}";
    }
}
