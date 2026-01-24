package com.fep.message.generic.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chain of Responsibility for resolving dynamic expressions in field values.
 *
 * <p>Supports expressions like @NOW(yyyyMMdd), @DATE, @TIME, @UUID.
 * New expression types can be added by implementing {@link DynamicExpressionResolver}
 * and registering with {@link #addResolver(DynamicExpressionResolver)}.
 *
 * <p>Example usage:
 * <pre>
 * DynamicExpressionChain chain = DynamicExpressionChain.createDefault();
 * String result = chain.resolve("TXN@NOW(yyyyMMdd)001");
 * // result: "TXN20260124001"
 * </pre>
 */
public class DynamicExpressionChain {

    private static final Pattern DYNAMIC_EXPR_PATTERN =
            Pattern.compile("@(\\w+)(?:\\(([^)]*)\\))?");

    private final List<DynamicExpressionResolver> resolvers = new ArrayList<>();

    /**
     * Creates a chain with default resolvers (@NOW, @DATE, @TIME, @UUID).
     */
    public static DynamicExpressionChain createDefault() {
        DynamicExpressionChain chain = new DynamicExpressionChain();
        chain.addResolver(new NowExpressionResolver());
        chain.addResolver(new DateExpressionResolver());
        chain.addResolver(new TimeExpressionResolver());
        chain.addResolver(new UuidExpressionResolver());
        return chain;
    }

    /**
     * Adds a resolver to the chain.
     *
     * @param resolver the resolver to add
     * @return this chain for fluent API
     */
    public DynamicExpressionChain addResolver(DynamicExpressionResolver resolver) {
        resolvers.add(resolver);
        return this;
    }

    /**
     * Removes a resolver from the chain.
     *
     * @param resolver the resolver to remove
     * @return true if the resolver was found and removed
     */
    public boolean removeResolver(DynamicExpressionResolver resolver) {
        return resolvers.remove(resolver);
    }

    /**
     * Clears all resolvers from the chain.
     */
    public void clearResolvers() {
        resolvers.clear();
    }

    /**
     * Returns the number of resolvers in the chain.
     */
    public int size() {
        return resolvers.size();
    }

    /**
     * Resolves all dynamic expressions in the given value (String mode).
     * All resolved values are converted to String for text replacement.
     *
     * @param value the value that may contain dynamic expressions
     * @return the resolved value with expressions replaced as String
     */
    public String resolve(String value) {
        if (value == null || !value.contains("@")) {
            return value;
        }

        Matcher matcher = DYNAMIC_EXPR_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String funcName = matcher.group(1);
            String args = matcher.group(2);

            Object resolved = resolveExpression(funcName, args);
            String replacement = (resolved != null) ? resolved.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a single expression and returns the result as Object.
     * Use this when you need the native type (e.g., byte[], Number).
     *
     * <p>Example:
     * <pre>
     * Object result = chain.resolveToObject("@UUID");
     * // result: "a1b2c3d4e5f6" (String)
     *
     * Object bytes = chain.resolveToObject("@HEX(AABBCC)");
     * // result: byte[] (if HexExpressionResolver returns byte[])
     * </pre>
     *
     * @param expression the expression (e.g., "@NOW(yyyyMMdd)")
     * @return the resolved value as Object, or the original expression if not resolved
     */
    public Object resolveToObject(String expression) {
        if (expression == null || !expression.contains("@")) {
            return expression;
        }

        Matcher matcher = DYNAMIC_EXPR_PATTERN.matcher(expression);
        if (matcher.matches()) {
            String funcName = matcher.group(1);
            String args = matcher.group(2);
            Object resolved = resolveExpression(funcName, args);
            return (resolved != null) ? resolved : expression;
        }

        // If mixed content, fall back to String resolution
        return resolve(expression);
    }

    /**
     * Finds the appropriate resolver and resolves the expression.
     *
     * @return the resolved Object, or null if no resolver found
     */
    private Object resolveExpression(String funcName, Object args) {
        for (DynamicExpressionResolver resolver : resolvers) {
            if (resolver.supports(funcName)) {
                return resolver.resolve(funcName, args);
            }
        }
        return null;
    }
}
