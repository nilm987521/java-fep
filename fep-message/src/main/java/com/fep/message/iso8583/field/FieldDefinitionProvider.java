package com.fep.message.iso8583.field;

import java.util.Map;

/**
 * Interface for providing ISO 8583 field definitions.
 *
 * <p>Different implementations can provide field definitions for various protocols:
 * <ul>
 *   <li>{@link FiscFieldDefinitions} - Taiwan FISC protocol</li>
 *   <li>{@link BankCoreFieldDefinitions} - Bank core system protocol</li>
 *   <li>{@link AtmFieldDefinitions} - ATM protocol (NDC/DDC)</li>
 * </ul>
 *
 * <p>Implementations should be thread-safe and support lazy loading.
 */
public interface FieldDefinitionProvider {

    /**
     * Gets the field definition for a specific field number.
     *
     * @param fieldNumber the field number (1-128)
     * @return the field definition, or null if not defined
     */
    FieldDefinition getDefinition(int fieldNumber);

    /**
     * Gets all defined field definitions.
     *
     * @return unmodifiable map of field number to field definition
     */
    Map<Integer, FieldDefinition> getAllDefinitions();

    /**
     * Checks if a field is defined.
     *
     * @param fieldNumber the field number
     * @return true if the field is defined
     */
    boolean isDefined(int fieldNumber);

    /**
     * Gets the number of defined fields.
     *
     * @return the count of defined fields
     */
    int getDefinedFieldCount();

    /**
     * Gets the name/identifier of this provider.
     *
     * @return provider name (e.g., "FISC", "BankCore", "ATM")
     */
    String getProviderName();

    /**
     * Reloads field definitions from the source.
     * Useful for runtime configuration updates.
     */
    void reload();

    /**
     * Clears the cached definitions, forcing reload on next access.
     */
    void clearCache();
}
