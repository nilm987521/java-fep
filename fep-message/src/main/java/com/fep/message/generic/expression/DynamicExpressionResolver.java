package com.fep.message.generic.expression;

/**
 * Interface for resolving dynamic expressions in field default values.
 * Implements Chain of Responsibility pattern for extensibility.
 *
 * <p>Usage in schema:
 * <pre>
 * fields:
 *   - id: txDate
 *     defaultValue: "@NOW(yyyyMMdd)"
 * </pre>
 */
public interface DynamicExpressionResolver {

    /**
     * Checks if this resolver can handle the given function name.
     *
     * @param functionName the function name (e.g., "NOW", "DATE", "UUID")
     * @return true if this resolver handles this function
     */
    boolean supports(String functionName);

    /**
     * Resolves the expression and returns the result.
     *
     * @param functionName the function name
     * @param args the arguments (may be null)
     * @return the resolved value (String, Number, byte[], or any Object)
     */
    Object resolve(String functionName, Object args);
}
