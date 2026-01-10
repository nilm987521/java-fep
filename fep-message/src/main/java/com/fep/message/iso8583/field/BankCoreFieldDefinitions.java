package com.fep.message.iso8583.field;

import java.nio.file.Path;
import java.util.Map;

/**
 * Field definitions for Bank Core System protocol.
 * Used for internal FEP &lt;-&gt; Core Banking System communication.
 *
 * <p>Key differences from FISC:
 * <ul>
 *   <li>ASCII encoding for most fields (instead of BCD)</li>
 *   <li>ASCII length prefix for variable fields</li>
 *   <li>Additional internal fields (120-127) for bank-specific data</li>
 *   <li>Shorter MAC field (8 bytes instead of 16)</li>
 * </ul>
 *
 * <p>Field definitions are loaded from: /bankcore-field-definitions.csv
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Static access
 * FieldDefinition pan = BankCoreFieldDefinitions.get(2);
 *
 * // Instance access
 * FieldDefinitionProvider provider = BankCoreFieldDefinitions.getInstance();
 * FieldDefinition pan = provider.getDefinition(2);
 * }</pre>
 *
 * @see AbstractFieldDefinitionProvider
 * @see FieldDefinitionProvider
 */
public final class BankCoreFieldDefinitions extends AbstractFieldDefinitionProvider {

    /** Provider name */
    private static final String PROVIDER_NAME = "BankCore";

    /** Default resource path for field definitions */
    private static final String DEFAULT_RESOURCE_PATH = "/bankcore-field-definitions.csv";

    /** System property to override the default CSV file path */
    public static final String CSV_PATH_PROPERTY = "bankcore.field.definitions.path";

    /** Singleton instance */
    private static final BankCoreFieldDefinitions INSTANCE = new BankCoreFieldDefinitions();

    /**
     * Private constructor for singleton pattern.
     */
    private BankCoreFieldDefinitions() {
        // Singleton
    }

    /**
     * Gets the singleton instance.
     *
     * @return the BankCoreFieldDefinitions instance
     */
    public static BankCoreFieldDefinitions getInstance() {
        return INSTANCE;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected String getDefaultResourcePath() {
        return DEFAULT_RESOURCE_PATH;
    }

    @Override
    protected String getSystemPropertyKey() {
        return CSV_PATH_PROPERTY;
    }

    // ========== Static methods for convenience ==========

    /**
     * Gets the field definition for a specific field number.
     *
     * @param fieldNumber the field number (1-128)
     * @return the field definition, or null if not defined
     */
    public static FieldDefinition get(int fieldNumber) {
        return INSTANCE.getDefinition(fieldNumber);
    }

    /**
     * Gets all defined field definitions.
     *
     * @return unmodifiable map of field definitions
     */
    public static Map<Integer, FieldDefinition> getAll() {
        return INSTANCE.getAllDefinitions();
    }

    /**
     * Checks if a field is defined.
     *
     * @param fieldNumber the field number
     * @return true if the field is defined
     */
    public static boolean isFieldDefined(int fieldNumber) {
        return INSTANCE.isDefined(fieldNumber);
    }

    /**
     * Gets the number of defined fields.
     *
     * @return the count of defined fields
     */
    public static int getFieldCount() {
        return INSTANCE.getDefinedFieldCount();
    }

    /**
     * Loads field definitions from a classpath resource.
     *
     * @param resourcePath the classpath resource path
     */
    public static void loadResource(String resourcePath) {
        INSTANCE.loadFromResource(resourcePath);
    }

    /**
     * Loads field definitions from a file path.
     *
     * @param filePath the file path
     */
    public static void loadFile(String filePath) {
        INSTANCE.loadFromFile(filePath);
    }

    /**
     * Loads field definitions from a file path.
     *
     * @param filePath the file path
     */
    public static void loadFile(Path filePath) {
        INSTANCE.loadFromFile(filePath);
    }

    /**
     * Reloads field definitions from the default source.
     */
    public static void reloadDefinitions() {
        INSTANCE.reload();
    }

    /**
     * Clears the cached definitions.
     */
    public static void clear() {
        INSTANCE.clearCache();
    }
}
