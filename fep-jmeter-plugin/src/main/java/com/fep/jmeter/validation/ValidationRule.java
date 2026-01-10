package com.fep.jmeter.validation;

import com.fep.message.iso8583.Iso8583Message;

import java.util.List;

/**
 * Interface for message validation rules.
 *
 * <p>Each rule validates a specific aspect of an ISO 8583 message,
 * such as required fields, field format, allowed values, etc.
 */
public interface ValidationRule {

    /**
     * Gets the rule type identifier.
     *
     * @return the rule type (e.g., "REQUIRED", "FORMAT", "VALUE")
     */
    String getRuleType();

    /**
     * Validates the message against this rule.
     *
     * @param message the message to validate
     * @return list of validation errors (empty if validation passes)
     */
    List<ValidationError> validate(Iso8583Message message);

    /**
     * Gets a description of this rule for logging/debugging.
     *
     * @return rule description
     */
    String getDescription();
}
