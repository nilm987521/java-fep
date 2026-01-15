package com.fep.message.generic.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fep.message.exception.MessageException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches message schemas from JSON files or strings.
 */
@Slf4j
public class JsonSchemaLoader {

    private static final ObjectMapper objectMapper = createObjectMapper();
    private static final Map<String, MessageSchema> schemaCache = new ConcurrentHashMap<>();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
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
            MessageSchema schema = objectMapper.readValue(json, MessageSchema.class);
            validateSchema(schema);
            return schema;
        } catch (IOException e) {
            throw MessageException.parseError("Failed to parse schema JSON: " + e.getMessage());
        }
    }

    /**
     * Loads a schema from a file path.
     *
     * @param path the file path
     * @return the parsed MessageSchema
     * @throws MessageException if loading or parsing fails
     */
    public static MessageSchema fromFile(Path path) {
        String cacheKey = path.toAbsolutePath().toString();
        return schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                String json = Files.readString(path);
                MessageSchema schema = fromJson(json);
                log.info("Loaded schema '{}' from file: {}", schema.getName(), path);
                return schema;
            } catch (IOException e) {
                throw MessageException.parseError("Failed to load schema from file: " + path + " - " + e.getMessage());
            }
        });
    }

    /**
     * Loads a schema from classpath resource.
     *
     * @param resourcePath the classpath resource path (e.g., "schemas/ncr-ndc-v1.json")
     * @return the parsed MessageSchema
     * @throws MessageException if loading or parsing fails
     */
    public static MessageSchema fromResource(String resourcePath) {
        return schemaCache.computeIfAbsent("resource:" + resourcePath, k -> {
            try (InputStream is = JsonSchemaLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw MessageException.parseError("Schema resource not found: " + resourcePath);
                }
                MessageSchema schema = objectMapper.readValue(is, MessageSchema.class);
                validateSchema(schema);
                log.info("Loaded schema '{}' from resource: {}", schema.getName(), resourcePath);
                return schema;
            } catch (IOException e) {
                throw MessageException.parseError("Failed to load schema from resource: " + resourcePath + " - " + e.getMessage());
            }
        });
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
                String json = Files.readString(path);
                JsonNode root = objectMapper.readTree(json);

                if (root == null || !root.isArray()) {
                    throw MessageException.parseError("Schema collection file must contain an array: " + path);
                }

                for (JsonNode schemaNode : root) {
                    String name = schemaNode.has("name") ? schemaNode.get("name").asText() : null;
                    if (schemaName.equals(name)) {
                        MessageSchema schema = objectMapper.treeToValue(schemaNode, MessageSchema.class);
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
     * Gets all available schema names from a collection file.
     *
     * @param path the file path to the collection file
     * @return list of schema names
     * @throws MessageException if loading fails
     */
    public static List<String> getSchemaNames(Path path) {
        try {
            String json = Files.readString(path);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return  Collections.emptyList();
            }

            List<String> schemaNames = new ArrayList<>();
            for (JsonNode schemaNode : root) {
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
     * Removes a specific schema from cache.
     *
     * @param key the cache key (file path or resource path)
     */
    public static void removeFromCache(String key) {
        schemaCache.remove(key);
    }
}
