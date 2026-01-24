package com.fep.message.generic.expression;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Resolves @TIME expression - shortcut for current time.
 *
 * <p>Example: @TIME → 153052
 */
public class TimeExpressionResolver implements DynamicExpressionResolver {

    private static final String TIME_FORMAT = "HHmmss";

    @Override
    public boolean supports(String functionName) {
        return "TIME".equalsIgnoreCase(functionName);
    }

    @Override
    public Object resolve(String functionName, Object args) {
        return LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT));
    }
}
