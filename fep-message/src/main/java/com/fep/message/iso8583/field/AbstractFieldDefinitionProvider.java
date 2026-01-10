package com.fep.message.iso8583.field;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for CSV-based field definition providers.
 *
 * <p>Provides common functionality for loading field definitions from CSV files:
 * <ul>
 *   <li>Thread-safe lazy initialization</li>
 *   <li>Loading from classpath resources or file paths</li>
 *   <li>System property override support</li>
 *   <li>Reload and cache clear capabilities</li>
 * </ul>
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #getProviderName()} - provider identifier</li>
 *   <li>{@link #getDefaultResourcePath()} - default classpath resource</li>
 *   <li>{@link #getSystemPropertyKey()} - system property for custom path override</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractFieldDefinitionProvider implements FieldDefinitionProvider {

    /** Cached field definitions */
    private final AtomicReference<Map<Integer, FieldDefinition>> fieldMap = new AtomicReference<>();

    /** Loader instance */
    private final CsvFieldDefinitionLoader loader = new CsvFieldDefinitionLoader();

    /**
     * Gets the default classpath resource path for field definitions.
     *
     * @return classpath resource path (e.g., "/fisc-field-definitions.csv")
     */
    protected abstract String getDefaultResourcePath();

    /**
     * Gets the system property key for overriding the default CSV path.
     *
     * @return system property key (e.g., "fisc.field.definitions.path")
     */
    protected abstract String getSystemPropertyKey();

    /**
     * Ensures field definitions are loaded.
     * Thread-safe lazy initialization.
     */
    protected void ensureLoaded() {
        if (fieldMap.get() == null) {
            synchronized (this) {
                if (fieldMap.get() == null) {
                    loadDefault();
                }
            }
        }
    }

    /**
     * Loads field definitions from the default source.
     * Checks system property for custom path, otherwise uses classpath resource.
     */
    protected void loadDefault() {
        String propertyKey = getSystemPropertyKey();
        String customPath = propertyKey != null ? System.getProperty(propertyKey) : null;

        if (customPath != null && !customPath.isEmpty()) {
            log.info("[{}] Loading field definitions from system property path: {}",
                getProviderName(), customPath);
            loadFromFile(customPath);
        } else {
            String resourcePath = getDefaultResourcePath();
            log.info("[{}] Loading field definitions from default resource: {}",
                getProviderName(), resourcePath);
            loadFromResource(resourcePath);
        }
    }

    /**
     * Loads field definitions from a classpath resource.
     *
     * @param resourcePath the classpath resource path
     */
    public void loadFromResource(String resourcePath) {
        try {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource(resourcePath);
            fieldMap.set(definitions);
            log.info("[{}] Loaded {} field definitions from resource: {}",
                getProviderName(), definitions.size(), resourcePath);
        } catch (Exception e) {
            log.error("[{}] Failed to load field definitions from resource: {}",
                getProviderName(), resourcePath, e);
            throw e;
        }
    }

    /**
     * Loads field definitions from a file path.
     *
     * @param filePath the file path
     */
    public void loadFromFile(String filePath) {
        loadFromFile(Path.of(filePath));
    }

    /**
     * Loads field definitions from a file path.
     *
     * @param filePath the file path
     */
    public void loadFromFile(Path filePath) {
        try {
            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(filePath);
            fieldMap.set(definitions);
            log.info("[{}] Loaded {} field definitions from file: {}",
                getProviderName(), definitions.size(), filePath);
        } catch (Exception e) {
            log.error("[{}] Failed to load field definitions from file: {}",
                getProviderName(), filePath, e);
            throw e;
        }
    }

    @Override
    public void reload() {
        log.info("[{}] Reloading field definitions", getProviderName());
        loadDefault();
    }

    @Override
    public FieldDefinition getDefinition(int fieldNumber) {
        ensureLoaded();
        return fieldMap.get().get(fieldNumber);
    }

    @Override
    public Map<Integer, FieldDefinition> getAllDefinitions() {
        ensureLoaded();
        return Collections.unmodifiableMap(fieldMap.get());
    }

    @Override
    public boolean isDefined(int fieldNumber) {
        ensureLoaded();
        return fieldMap.get().containsKey(fieldNumber);
    }

    @Override
    public int getDefinedFieldCount() {
        ensureLoaded();
        return fieldMap.get().size();
    }

    @Override
    public void clearCache() {
        fieldMap.set(null);
        log.debug("[{}] Field definitions cache cleared", getProviderName());
    }
}
