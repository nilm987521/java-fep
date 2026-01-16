package com.fep.jmeter.config;

import com.fep.jmeter.sampler.SchemaSource;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * JMeter Config Element for managing shared schema file configuration.
 *
 * <p>This element allows users to define a schema file path at the Test Plan
 * or Thread Group level. All samplers share this single schema file, and each
 * sampler can independently select which schema to use from the file.
 *
 * <p>Both request and response schemas are loaded from the same JSON file.
 *
 * <p>Usage:
 * <ol>
 *   <li>Add this config element to your Test Plan or Thread Group</li>
 *   <li>Configure the schema file path</li>
 *   <li>Samplers use {@link #getSchema(String)} to load their selected schema</li>
 * </ol>
 */
public class SchemaConfigElement extends ConfigTestElement implements TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SchemaConfigElement.class);

    // Property names
    public static final String SCHEMA_FILE = "schemaFile";

    // Static cache for the schema file path (after variable substitution)
    private static volatile String cachedSchemaFilePath;

    public SchemaConfigElement() {
        super();
        setProperty(TestElement.GUI_CLASS, "com.fep.jmeter.gui.SchemaConfigElementGui");
        setName("Schema Configuration");
    }

    /**
     * Gets a schema by name from the configured schema file.
     *
     * @param schemaName the schema name to load
     * @return the MessageSchema
     * @throws IllegalStateException if the configuration is not initialized
     */
    public static MessageSchema getSchema(String schemaName) {
        String filePath = cachedSchemaFilePath;
        if (filePath == null) {
            throw new IllegalStateException("Schema config not initialized. " +
                "Make sure SchemaConfigElement is added to your test plan.");
        }
        return JsonSchemaLoader.fromCollectionFile(Path.of(filePath), schemaName);
    }

    /**
     * Gets the schema file path.
     *
     * @return the schema file path
     * @throws IllegalStateException if the configuration is not initialized
     */
    public static String getSchemaFilePath() {
        String filePath = cachedSchemaFilePath;
        if (filePath == null) {
            throw new IllegalStateException("Schema config not initialized. " +
                "Make sure SchemaConfigElement is added to your test plan.");
        }
        return filePath;
    }

    /**
     * Gets the list of available schema names from the configured file.
     *
     * @return list of schema names
     * @throws IllegalStateException if the configuration is not initialized
     */
    public static List<String> getAvailableSchemas() {
        String filePath = cachedSchemaFilePath;
        if (filePath == null) {
            throw new IllegalStateException("Schema config not initialized. " +
                "Make sure SchemaConfigElement is added to your test plan.");
        }
        return JsonSchemaLoader.getSchemaNames(Path.of(filePath));
    }

    /**
     * Checks if the configuration is initialized.
     *
     * @return true if the configuration is initialized
     */
    public static boolean isInitialized() {
        return cachedSchemaFilePath != null;
    }

    /**
     * Registers the schema file path when test starts.
     * Also loads all schemas and notifies subscribers.
     */
    private void registerFilePath() {
        log.info("Registering schema file path");

        try {
            // Substitute JMeter variables in file path
            String schemaFilePath = substituteVariables(getSchemaFile());
            cachedSchemaFilePath = schemaFilePath;

            log.info("Schema config registered: schemaFile='{}'", schemaFilePath);

            // Store in JMeter variables for debugging
            JMeterVariables vars = JMeterContextService.getContext().getVariables();
            if (vars != null) {
                vars.put("SCHEMA_CONFIG_FILE", schemaFilePath);
            }

            // Load all schemas and notify subscribers
            JsonSchemaLoader.reloadFromFilePath(schemaFilePath);
            log.info("Schemas loaded and subscribers notified");

        } catch (Exception e) {
            log.error("Failed to register schema config: {}", e.getMessage());
            throw new RuntimeException("Failed to register schema config: " + e.getMessage(), e);
        }
    }

    /**
     * Substitutes JMeter variables in the given string.
     */
    private String substituteVariables(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        JMeterVariables vars = JMeterContextService.getContext().getVariables();
        if (vars != null) {
            while (value.contains("${")) {
                int start = value.indexOf("${");
                int end = value.indexOf("}", start);
                if (end > start) {
                    String varName = value.substring(start + 2, end);
                    String varValue = vars.get(varName);
                    if (varValue != null) {
                        value = value.substring(0, start) + varValue + value.substring(end + 1);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        return value;
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("Schema config test started on {}", host);
        registerFilePath();
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("Schema config test ended, clearing cache");
        cachedSchemaFilePath = null;
    }

    // Getters and Setters
    public String getSchemaFile() {
        String value = getPropertyAsString(SCHEMA_FILE, "");
        return value.isBlank() ? SchemaSource.getDefaultSchemaPath() : value;
    }

    public void setSchemaFile(String file) {
        setProperty(SCHEMA_FILE, file);
    }

}
