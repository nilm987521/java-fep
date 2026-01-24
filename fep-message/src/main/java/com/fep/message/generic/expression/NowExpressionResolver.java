package com.fep.message.generic.expression;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Resolves @NOW expressions with custom date/time format.
 *
 * <p>Examples:
 * <ul>
 *   <li>@NOW(yyyyMMdd) → 20260124</li>
 *   <li>@NOW(HHmmss) → 153052</li>
 *   <li>@NOW(yyyyMMddHHmmss) → 20260124153052</li>
 *   <li>@NOW() → 20260124153052 (default format)</li>
 * </ul>
 */
public class NowExpressionResolver implements DynamicExpressionResolver {

    private static final String DEFAULT_FORMAT = "yyyyMMddHHmmss";

    @Override
    public boolean supports(String functionName) {
        return "NOW".equalsIgnoreCase(functionName);
    }

    @Override
    public Object resolve(String functionName, Object args) {
        String format = DEFAULT_FORMAT;
        if (args instanceof String strArgs && !strArgs.isEmpty()) {
            format = strArgs;
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }
}
