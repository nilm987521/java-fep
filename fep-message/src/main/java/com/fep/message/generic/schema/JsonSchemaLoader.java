package com.fep.message.generic.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fep.message.exception.MessageException;
import com.fep.message.interfaces.SchemaSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads and caches message schemas from JSON or YAML files.
 */
@Slf4j
public class JsonSchemaLoader {

    private static final ObjectMapper jsonMapper = createObjectMapper(null);
    private static final ObjectMapper yamlMapper = createObjectMapper(new YAMLFactory());
    private static final Map<String, MessageSchema> schemaCache = new ConcurrentHashMap<>();
    private static final List<WeakReference<SchemaSubscriber>> subscribers = new CopyOnWriteArrayList<>();

    private static ObjectMapper createObjectMapper(com.fasterxml.jackson.core.JsonFactory factory) {
        ObjectMapper mapper = factory != null ? new ObjectMapper(factory) : new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Returns the appropriate ObjectMapper based on file extension.
     *
     * @param filePath the file path
     * @return the appropriate ObjectMapper (YAML or JSON)
     */
    private static ObjectMapper getMapperForFile(String filePath) {
        String lower = filePath.toLowerCase();
        return (lower.endsWith(".yml") || lower.endsWith(".yaml")) ? yamlMapper : jsonMapper;
    }

    /**
     * Loads a schema from a JSON string.
     *
     * @param json the JSON string
     * @return the parsed MessageSchema
     * @throws MessageException if parsing fails
     */
    public static MessageSchema fromJson(String json) {
        try {
            MessageSchema schema = jsonMapper.readValue(json, MessageSchema.class);
            validateSchema(schema);
            return schema;
        } catch (IOException e) {
            throw MessageException.parseError("Failed to parse schema JSON: " + e.getMessage());
        }
    }

    /**
     * Validates a schema for required fields and consistency.
     *
     * @param schema the schema to validate
     * @throws MessageException if validation fails
     */
    public static void validateSchema(MessageSchema schema) {
        if (schema.getName() == null || schema.getName().isBlank()) {
            throw MessageException.parseError("Schema must have a name");
        }
        if (schema.getFields() == null || schema.getFields().isEmpty()) {
            throw MessageException.parseError("Schema must have at least one field");
        }

        // Validate each field
        for (FieldSchema field : schema.getFields()) {
            validateField(field, schema.getName());
        }

        // Validate header fields if present
        if (schema.getHeader() != null && schema.getHeader().getFields() != null) {
            for (FieldSchema field : schema.getHeader().getFields()) {
                validateField(field, schema.getName() + ".header");
            }
        }

        // Validate trailer fields if present
        if (schema.getTrailer() != null && schema.getTrailer().getFields() != null) {
            for (FieldSchema field : schema.getTrailer().getFields()) {
                validateField(field, schema.getName() + ".trailer");
            }
        }

        log.debug("Schema '{}' validation passed", schema.getName());
    }

    private static void validateField(FieldSchema field, String context) {
        if (field.getId() == null || field.getId().isBlank()) {
            throw MessageException.parseError("Field in " + context + " must have an id");
        }

        // Fixed-length fields must have a length > 0
        if (field.isFixedLength() && field.getLength() <= 0 && !field.isComposite()) {
            throw MessageException.parseError("Fixed-length field '" + field.getId() + "' must have length > 0");
        }

        // Variable-length fields must have a max length > 0
        if (field.isVariableLength() && field.getLength() <= 0) {
            throw MessageException.parseError("Variable-length field '" + field.getId() + "' must have maxLength > 0");
        }

        // Validate composite fields recursively
        if (field.isComposite()) {
            for (FieldSchema child : field.getFields()) {
                validateField(child, context + "." + field.getId());
            }
        }

        // Bitmap fields must have controls defined
        if (field.isBitmap() && (field.getControls() == null || field.getControls().isEmpty())) {
            throw MessageException.parseError("Bitmap field '" + field.getId() + "' must have controls defined");
        }
    }

    /**
     * Loads a specific schema by name from a collection file.
     * The collection file should have a "schemas" array containing multiple schema definitions.
     * Supports both JSON and YAML file formats.
     *
     * @param path the file path to the collection file
     * @param schemaName the name of the schema to load
     * @return the parsed MessageSchema
     * @throws MessageException if loading or parsing fails, or schema not found
     */
    public static MessageSchema fromCollectionFile(Path path, String schemaName) {
        String cacheKey = path.toAbsolutePath() + ":" + schemaName;
        return schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                String content = Files.readString(path);
                ObjectMapper mapper = getMapperForFile(path.toString());
                JsonNode root = mapper.readTree(content);
                JsonNode schemasNode = extractSchemasNodeFromPath(root, path);

                for (JsonNode schemaNode : schemasNode) {
                    String name = schemaNode.has("name") ? schemaNode.get("name").asText() : null;
                    if (schemaName.equals(name)) {
                        MessageSchema schema = mapper.treeToValue(schemaNode, MessageSchema.class);
                        validateSchema(schema);
                        log.info("Loaded schema '{}' from collection file: {}", schemaName, path);
                        return schema;
                    }
                }

                throw MessageException.parseError("Schema '" + schemaName + "' not found in collection file: " + path);
            } catch (IOException e) {
                throw MessageException.parseError("Failed to load schema from collection file: " + path + " - " + e.getMessage());
            }
        });
    }

    /**
     * Extracts the schemas array node from the root JSON (Path version).
     */
    private static JsonNode extractSchemasNodeFromPath(JsonNode root, Path path) {
        if (root == null) {
            throw MessageException.parseError("Schema collection file is empty: " + path);
        }
        if (root.isArray()) {
            return root;
        }
        if (root.isObject() && root.has("schemas") && root.get("schemas").isArray()) {
            return root.get("schemas");
        }
        throw MessageException.parseError(
                "Schema collection file must contain an array or object with 'schemas' array: " + path);
    }

    /**
     * Gets all available schema names from a collection file.
     * Supports both JSON and YAML file formats.
     *
     * @param path the file path to the collection file
     * @return list of schema names
     * @throws MessageException if loading fails
     */
    public static List<String> getSchemaNames(Path path) {
        try {
            String content = Files.readString(path);
            ObjectMapper mapper = getMapperForFile(path.toString());
            JsonNode root = mapper.readTree(content);
            JsonNode schemasNode = extractSchemasNodeFromPath(root, path);

            List<String> schemaNames = new ArrayList<>();
            for (JsonNode schemaNode : schemasNode) {
                if (schemaNode.has("name")) {
                    schemaNames.add(schemaNode.get("name").asText());
                }
            }
            return schemaNames;
        } catch (IOException e) {
            throw MessageException.parseError("Failed to read schema collection file: " + path + " - " + e.getMessage());
        }
    }

    /**
     * Clears the schema cache.
     */
    public static void clearCache() {
        schemaCache.clear();
        log.debug("Schema cache cleared");
    }

    /**
     * Removes a specific schema from the cache.
     *
     * @param key the cache key (file path or resource path)
     */
    public static void removeFromCache(String key) {
        schemaCache.remove(key);
    }

    /**
     * Reloads all schemas from a file path.
     * Supports both JSON and YAML file formats (detected by file extension).
     *
     * @param filePath the file path to the schema collection file
     * @throws MessageException if the file is not found or parsing fails
     */
    public static void reloadFromFilePath(String filePath) {
        schemaCache.clear();
        File schemaFile = new File(filePath);
        if (!schemaFile.exists()) {
            throw MessageException.parseError("Schema file not found: " + filePath);
        }

        try {
            ObjectMapper mapper = getMapperForFile(filePath);
            JsonNode root = mapper.readTree(schemaFile);
            JsonNode schemasNode = extractSchemasNode(root, filePath);

            for (JsonNode schemaNode : schemasNode) {
                String name = schemaNode.has("name") ? schemaNode.get("name").asText() : null;
                MessageSchema schema = mapper.treeToValue(schemaNode, MessageSchema.class);
                validateSchema(schema);
                schemaCache.put(name, schema);
            }
            log.info("Loaded {} schemas from {}", schemaCache.size(), filePath);
            publishSchemaMap();
        } catch (IOException e) {
            throw MessageException.parseError("Failed to load schemas from: " + filePath + " - " + e.getMessage());
        }
    }

    /**
     * Extracts the schemas array node from the root JSON.
     * Supports two formats:
     * 1. Direct array: [{...}, {...}]
     * 2. Object with schemas property: { "schemas": [{...}, {...}] }
     */
    private static JsonNode extractSchemasNode(JsonNode root, String filePath) {
        if (root.isArray()) {
            return root;
        }
        if (root.isObject() && root.has("schemas") && root.get("schemas").isArray()) {
            return root.get("schemas");
        }
        throw MessageException.parseError(
                "Schema json file must contain an array or object with 'schemas' array: " + filePath);
    }

    public static Map<String, MessageSchema> getSchemaMap() {
        return Collections.unmodifiableMap(schemaCache);
    }

    /**
     * Registers a subscriber to receive schema updates.
     * Uses WeakReference to prevent memory leaks.
     * If schemas are already loaded, immediately notifies the new subscriber.
     *
     * @param subscriber the subscriber to register
     */
    public static void registerSubscriber(SchemaSubscriber subscriber) {
        if (subscriber == null) return;

        // Check if already registered (compare actual subscribers, not WeakReferences)
        boolean hasRegistry = subscribers.stream().anyMatch(ref -> ref.get() == subscriber);
        if (!hasRegistry) {
            subscribers.add(new WeakReference<>(subscriber));
            log.debug("Registered schema subscriber: {}", subscriber.getClass().getSimpleName());
        }

        // Immediately notify the new subscriber if schemas are already loaded
        if (!schemaCache.isEmpty()) {
            try {
                subscriber.updateSchemaMap(getSchemaMap());
                log.debug("Immediately notified new subscriber with {} schemas", schemaCache.size());
            } catch (Exception e) {
                log.error("Failed to notify new subscriber {}: {}",
                    subscriber.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Unregisters a subscriber from receiving schema updates.
     * Also cleans up any GC'd references.
     *
     * @param subscriber the subscriber to unregister
     */
    public static void unregisterSubscriber(SchemaSubscriber subscriber) {
        if (subscriber == null) return;
        subscribers.removeIf(ref -> {
            SchemaSubscriber s = ref.get();
            return s == null || s == subscriber;  // Also clean up GC'd references
        });
        log.debug("Unregistered schema subscriber: {}", subscriber.getClass().getSimpleName());
    }

    /**
     * Notifies all registered subscribers of schema changes.
     * Automatically cleans up GC'd weak references.
     */
    private static void publishSchemaMap() {
        Map<String, MessageSchema> schemaMap = getSchemaMap();
        List<WeakReference<SchemaSubscriber>> toRemove = new ArrayList<>();

        for (WeakReference<SchemaSubscriber> ref : subscribers) {
            SchemaSubscriber subscriber = ref.get();
            if (subscriber == null) {
                toRemove.add(ref);  // Mark for removal
                continue;
            }
            try {
                subscriber.updateSchemaMap(schemaMap);
            } catch (Exception e) {
                log.error("Failed to notify subscriber {}: {}",
                    subscriber.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Clean up GC'd references
        if (!toRemove.isEmpty()) {
            subscribers.removeAll(toRemove);
            log.debug("Cleaned up {} GC'd subscriber references", toRemove.size());
        }
    }

}
