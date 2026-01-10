package com.fep.jmeter.engine;

import com.fep.message.iso8583.Iso8583Message;

/**
 * Sealed interface for MTI response rule conditions.
 *
 * <p>Supports two types of conditions:
 * <ul>
 *   <li>{@link FieldEquals} - Matches when a specific field equals a value</li>
 *   <li>{@link Default} - Always matches (catch-all)</li>
 * </ul>
 */
public sealed interface RuleCondition permits RuleCondition.FieldEquals, RuleCondition.Default {

    /**
     * Checks if this condition matches the given request message.
     *
     * @param request the incoming ISO 8583 message
     * @return true if the condition matches
     */
    boolean matches(Iso8583Message request);

    /**
     * Condition that matches when a field equals a specific value.
     *
     * @param field the field number to check
     * @param value the expected value
     */
    record FieldEquals(int field, String value) implements RuleCondition {
        @Override
        public boolean matches(Iso8583Message request) {
            String fieldValue = request.getFieldAsString(field);
            return value.equals(fieldValue);
        }

        @Override
        public String toString() {
            return "F" + field + "=" + value;
        }
    }

    /**
     * Default condition that always matches.
     * Used as a catch-all rule when no other conditions match.
     */
    record Default() implements RuleCondition {
        @Override
        public boolean matches(Iso8583Message request) {
            return true;
        }

        @Override
        public String toString() {
            return "DEFAULT";
        }
    }
}
