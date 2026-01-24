package com.fep.message.generic.expression;

import java.util.UUID;

/**
 * Resolves @UUID expression - generates a 12-character UUID.
 *
 * <p>Example: @UUID → a1b2c3d4e5f6
 */
public class UuidExpressionResolver implements DynamicExpressionResolver {

    private static final int UUID_LENGTH = 12;

    @Override
    public boolean supports(String functionName) {
        return "UUID".equalsIgnoreCase(functionName);
    }

    @Override
    public Object resolve(String functionName, Object args) {
        int length = UUID_LENGTH;
        if (args instanceof String strArgs && !strArgs.isEmpty()) {
            try {
                length = Integer.parseInt(strArgs);
            } catch (NumberFormatException ignored) {
                // Use default length
            }
        } else if (args instanceof Number numArgs) {
            length = numArgs.intValue();
        }
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(length, uuid.length()));
    }
}
