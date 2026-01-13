package com.fep.message.generic.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates example JSON data based on MessageSchema definitions.
 * Uses @Schema(example) annotations or generates sample data based on field types.
 *
 * <p>Usage examples:</p>
 * <pre>{@code
 * // Generate FISC ATM example
 * MessageSchema schema = SchemaExampleGenerator.createFiscAtmExample();
 * String schemaJson = SchemaExampleGenerator.generateSchemaExample(schema);
 *
 * // Generate message data with random values
 * String messageData = SchemaExampleGenerator.generateMessageDataExample(schema);
 *
 * // Load existing schema and generate example
 * MessageSchema mySchema = JsonSchemaLoader.fromFile(Path.of("my-schema.json"));
 * String example = SchemaExampleGenerator.generateSchemaExample(mySchema);
 * }</pre>
 */
@Slf4j
public class SchemaExampleGenerator {

    private static final ObjectMapper objectMapper = createObjectMapper();
    private static final Random random = new Random();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Generates a complete example MessageSchema JSON with sample field values.
     *
     * @param schema the MessageSchema to generate example for
     * @return example JSON string
     */
    public static String generateSchemaExample(MessageSchema schema) {
        Map<String, Object> example = new LinkedHashMap<>();

        // Add schema metadata
        example.put("$schema", schema.getSchema() != null ? schema.getSchema() : "fep-message-schema-v1");
        example.put("name", getExampleOrDefault("name", schema.getName(), "Example Protocol"));
        example.put("version", getExampleOrDefault("version", schema.getVersion(), "1.0.0"));
        example.put("vendor", getExampleOrDefault("vendor", schema.getVendor(), "FISC"));
        example.put("description", getExampleOrDefault("description", schema.getDescription(), "Example message schema"));

        // Add encoding config
        if (schema.getEncoding() != null) {
            example.put("encoding", generateEncodingExample(schema.getEncoding()));
        }

        // Add header section
        if (schema.getHeader() != null) {
            example.put("header", generateHeaderExample(schema.getHeader()));
        }

        // Add fields
        if (schema.getFields() != null && !schema.getFields().isEmpty()) {
            example.put("fields", generateFieldsExample(schema.getFields()));
        }

        // Add trailer section
        if (schema.getTrailer() != null) {
            example.put("trailer", generateTrailerExample(schema.getTrailer()));
        }

        return toJson(example);
    }

    /**
     * Generates example data for a list of FieldSchema definitions.
     *
     * @param fields the list of FieldSchema
     * @return example JSON string
     */
    public static String generateFieldsOnlyExample(List<FieldSchema> fields) {
        return toJson(generateFieldsExample(fields));
    }

    /**
     * Generates example message data (actual field values) based on schema.
     * This generates data that would be used in an actual message, not the schema definition.
     *
     * @param schema the MessageSchema
     * @return example message data as JSON
     */
    public static String generateMessageDataExample(MessageSchema schema) {
        Map<String, Object> messageData = new LinkedHashMap<>();

        if (schema.getFields() != null) {
            for (FieldSchema field : schema.getFields()) {
                messageData.put(field.getId(), generateFieldValue(field));
            }
        }

        return toJson(messageData);
    }

    /**
     * Generates a single field value based on field schema.
     *
     * @param field the FieldSchema
     * @return generated value
     */
    public static Object generateFieldValue(FieldSchema field) {
        // Use default value if specified
        if (field.getDefaultValue() != null && !field.getDefaultValue().isEmpty()) {
            return field.getDefaultValue();
        }

        // Generate based on field type
        return switch (field.getType()) {
            case NUMERIC -> generateNumericValue(field.getLength());
            case ALPHA -> generateAlphaValue(field.getLength());
            case ALPHANUMERIC -> generateAlphanumericValue(field.getLength());
            case BINARY -> generateBinaryValue(field.getLength());
            case TRACK2 -> generateTrack2Value();
            case BITMAP -> generateBitmapValue(field.getLength());
            case COMPOSITE -> generateCompositeValue(field);
        };
    }

    // --- Private helper methods ---

    private static List<Map<String, Object>> generateFieldsExample(List<FieldSchema> fields) {
        return fields.stream()
                .map(SchemaExampleGenerator::generateFieldExample)
                .toList();
    }

    private static Map<String, Object> generateFieldExample(FieldSchema field) {
        Map<String, Object> example = new LinkedHashMap<>();

        example.put("id", field.getId());

        if (field.getName() != null) {
            example.put("name", field.getName());
        }

        if (field.getDescription() != null) {
            example.put("description", field.getDescription());
        }

        example.put("type", field.getType().name());
        example.put("length", field.getLength());
        example.put("lengthType", field.getLengthType().name());

        if (!"ASCII".equals(field.getEncoding())) {
            example.put("encoding", field.getEncoding());
        }

        if (field.isVariableLength() && !"BCD".equals(field.getLengthEncoding())) {
            example.put("lengthEncoding", field.getLengthEncoding());
        }

        if (field.getPadding() != null) {
            Map<String, Object> padding = new LinkedHashMap<>();
            if (field.getPadding().getCharacter() != null) {
                padding.put("char", field.getPadding().getCharacter());
            }
            if (field.getPadding().getDirection() != null) {
                padding.put("direction", field.getPadding().getDirection().getValue());
            }
            if (!padding.isEmpty()) {
                example.put("padding", padding);
            }
        }

        if (field.isSensitive()) {
            example.put("sensitive", true);
        }

        if (field.isRequired()) {
            example.put("required", true);
        }

        if (field.getDefaultValue() != null) {
            example.put("defaultValue", field.getDefaultValue());
        }

        // Handle composite fields
        if (field.isComposite() && field.getFields() != null) {
            example.put("fields", generateFieldsExample(field.getFields()));
        }

        // Handle bitmap controls
        if (field.isBitmap() && field.getControls() != null) {
            example.put("controls", field.getControls());
        }

        return example;
    }

    private static Map<String, Object> generateEncodingExample(MessageSchema.EncodingConfig config) {
        Map<String, Object> encoding = new LinkedHashMap<>();
        encoding.put("defaultCharset", config.getDefaultCharset());
        encoding.put("bcdPacking", config.getBcdPacking().getValue());
        encoding.put("endianness", config.getEndianness().getValue());
        return encoding;
    }

    private static Map<String, Object> generateHeaderExample(MessageSchema.HeaderSection header) {
        Map<String, Object> headerExample = new LinkedHashMap<>();
        headerExample.put("includeLength", header.isIncludeLength());
        headerExample.put("lengthEncoding", header.getLengthEncoding());
        headerExample.put("lengthBytes", header.getLengthBytes());
        headerExample.put("lengthIncludesHeader", header.isLengthIncludesHeader());

        if (header.getFields() != null && !header.getFields().isEmpty()) {
            headerExample.put("fields", generateFieldsExample(header.getFields()));
        }

        return headerExample;
    }

    private static Map<String, Object> generateTrailerExample(MessageSchema.TrailerSection trailer) {
        Map<String, Object> trailerExample = new LinkedHashMap<>();

        if (trailer.getFields() != null && !trailer.getFields().isEmpty()) {
            trailerExample.put("fields", generateFieldsExample(trailer.getFields()));
        }

        return trailerExample;
    }

    private static String generateNumericValue(int length) {
        if (length <= 0) length = 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private static String generateAlphaValue(int length) {
        if (length <= 0) length = 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static String generateAlphanumericValue(int length) {
        if (length <= 0) length = 8;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String generateBinaryValue(int length) {
        if (length <= 0) length = 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", random.nextInt(256)));
        }
        return sb.toString();
    }

    private static String generateTrack2Value() {
        // Generate a sample Track 2 data format: ;PAN=EXPIRY?
        return ";4111111111111111=2512101123400001?";
    }

    private static String generateBitmapValue(int length) {
        if (length <= 0) length = 8;
        // Generate a sample bitmap (hex representation)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", random.nextInt(256)));
        }
        return sb.toString();
    }

    private static Map<String, Object> generateCompositeValue(FieldSchema field) {
        Map<String, Object> composite = new LinkedHashMap<>();
        if (field.getFields() != null) {
            for (FieldSchema child : field.getFields()) {
                composite.put(child.getId(), generateFieldValue(child));
            }
        }
        return composite;
    }

    private static String getExampleOrDefault(String fieldName, String currentValue, String defaultValue) {
        if (currentValue != null && !currentValue.isEmpty()) {
            return currentValue;
        }

        // Try to get @Schema example from MessageSchema class
        try {
            Field field = MessageSchema.class.getDeclaredField(fieldName);
            Schema schemaAnnotation = field.getAnnotation(Schema.class);
            if (schemaAnnotation != null && !schemaAnnotation.example().isEmpty()) {
                return schemaAnnotation.example();
            }
        } catch (NoSuchFieldException e) {
            // Field not found, use default
        }

        return defaultValue;
    }

    private static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "{}";
        }
    }

    /**
     * Creates a minimal valid example schema.
     *
     * @return a minimal MessageSchema example
     */
    public static MessageSchema createMinimalExample() {
        return MessageSchema.builder()
                .name("Example Protocol")
                .version("1.0.0")
                .vendor("Example Vendor")
                .description("A minimal example schema")
                .fields(List.of(
                        FieldSchema.builder()
                                .id("field1")
                                .name("Field 1")
                                .description("First field")
                                .type(FieldSchema.FieldDataType.ALPHANUMERIC)
                                .length(10)
                                .lengthType(FieldSchema.GenericLengthType.FIXED)
                                .build(),
                        FieldSchema.builder()
                                .id("field2")
                                .name("Field 2")
                                .description("Second field")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(12)
                                .lengthType(FieldSchema.GenericLengthType.LLVAR)
                                .build()
                ))
                .build();
    }

    /**
     * Creates a FISC ATM example schema.
     *
     * @return a FISC ATM MessageSchema example
     */
    public static MessageSchema createFiscAtmExample() {
        return MessageSchema.builder()
                .name("FISC ATM Protocol")
                .version("1.0.0")
                .vendor("FISC")
                .description("Taiwan FISC ATM interbank transaction protocol")
                .encoding(MessageSchema.EncodingConfig.builder()
                        .defaultCharset("ASCII")
                        .bcdPacking(MessageSchema.BcdPacking.RIGHT_JUSTIFIED)
                        .endianness(MessageSchema.Endianness.BIG_ENDIAN)
                        .build())
                .header(MessageSchema.HeaderSection.builder()
                        .includeLength(true)
                        .lengthEncoding("BCD")
                        .lengthBytes(2)
                        .lengthIncludesHeader(false)
                        .build())
                .fields(List.of(
                        FieldSchema.builder()
                                .id("mti")
                                .name("Message Type Indicator")
                                .description("Message type indicator")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(4)
                                .encoding("BCD")
                                .build(),
                        FieldSchema.builder()
                                .id("bitmap")
                                .name("Primary Bitmap")
                                .description("Primary bitmap indicating present fields")
                                .type(FieldSchema.FieldDataType.BITMAP)
                                .length(8)
                                .encoding("BINARY")
                                .controls(List.of("pan", "processingCode", "amount", "stan", "terminalId"))
                                .build(),
                        FieldSchema.builder()
                                .id("pan")
                                .name("Primary Account Number")
                                .description("Card number")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(19)
                                .lengthType(FieldSchema.GenericLengthType.LLVAR)
                                .sensitive(true)
                                .build(),
                        FieldSchema.builder()
                                .id("processingCode")
                                .name("Processing Code")
                                .description("Transaction processing code")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(6)
                                .encoding("BCD")
                                .build(),
                        FieldSchema.builder()
                                .id("amount")
                                .name("Transaction Amount")
                                .description("Transaction amount in cents")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(12)
                                .encoding("BCD")
                                .padding(FieldSchema.PaddingConfig.builder()
                                        .character("0")
                                        .direction(FieldSchema.PaddingDirection.LEFT)
                                        .build())
                                .build(),
                        FieldSchema.builder()
                                .id("stan")
                                .name("System Trace Audit Number")
                                .description("Unique transaction trace number")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(6)
                                .encoding("BCD")
                                .build(),
                        FieldSchema.builder()
                                .id("terminalId")
                                .name("Terminal ID")
                                .description("ATM terminal identifier")
                                .type(FieldSchema.FieldDataType.ALPHANUMERIC)
                                .length(8)
                                .build()
                ))
                .build();
    }

    /**
     * Main method for quick demonstration.
     * Run: mvn exec:java -pl fep-message -Dexec.mainClass="com.fep.message.generic.schema.SchemaExampleGenerator"
     */
    public static void main(String[] args) {
        System.out.println("=== FISC ATM Schema Example ===\n");
        MessageSchema schema = createFiscAtmExample();
        System.out.println(generateSchemaExample(schema));

        System.out.println("\n=== Message Data Example ===\n");
        System.out.println(generateMessageDataExample(schema));
    }
}
