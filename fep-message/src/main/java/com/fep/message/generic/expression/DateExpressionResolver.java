package com.fep.message.generic.expression;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Resolves @DATE expression - shortcut for current date.
 *
 * <p>Example: @DATE → 20260124
 */
public class DateExpressionResolver implements DynamicExpressionResolver {

    private static final String DATE_FORMAT = "yyyyMMdd";

    @Override
    public boolean supports(String functionName) {
        return "DATE".equalsIgnoreCase(functionName);
    }

    @Override
    public Object resolve(String functionName, Object args) {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT));
    }
}
