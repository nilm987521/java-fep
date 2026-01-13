package com.fep.message.generic.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SchemaExampleGenerator.
 */
class SchemaExampleGeneratorTest {

    @Test
    void generateSchemaExample_withMinimalSchema() {
        // Given
        MessageSchema schema = SchemaExampleGenerator.createMinimalExample();

        // When
        String example = SchemaExampleGenerator.generateSchemaExample(schema);

        // Then
        assertNotNull(example);
        assertTrue(example.contains("\"name\" : \"Example Protocol\""));
        assertTrue(example.contains("\"fields\""));
        assertTrue(example.contains("\"field1\""));
        assertTrue(example.contains("\"field2\""));

        System.out.println("=== Minimal Schema Example ===");
        System.out.println(example);
    }

    @Test
    void generateSchemaExample_withFiscAtmSchema() {
        // Given
        MessageSchema schema = SchemaExampleGenerator.createFiscAtmExample();

        // When
        String example = SchemaExampleGenerator.generateSchemaExample(schema);

        // Then
        assertNotNull(example);
        assertTrue(example.contains("\"name\" : \"FISC ATM Protocol\""));
        assertTrue(example.contains("\"vendor\" : \"FISC\""));
        assertTrue(example.contains("\"mti\""));
        assertTrue(example.contains("\"bitmap\""));
        assertTrue(example.contains("\"pan\""));
        assertTrue(example.contains("\"encoding\""));
        assertTrue(example.contains("\"header\""));

        System.out.println("=== FISC ATM Schema Example ===");
        System.out.println(example);
    }

    @Test
    void generateMessageDataExample_generatesValidData() {
        // Given
        MessageSchema schema = SchemaExampleGenerator.createFiscAtmExample();

        // When
        String messageData = SchemaExampleGenerator.generateMessageDataExample(schema);

        // Then
        assertNotNull(messageData);
        assertTrue(messageData.contains("\"mti\""));
        assertTrue(messageData.contains("\"pan\""));
        assertTrue(messageData.contains("\"amount\""));
        assertTrue(messageData.contains("\"terminalId\""));

        System.out.println("=== Message Data Example ===");
        System.out.println(messageData);
    }

    @Test
    void generateFieldValue_numericField() {
        // Given
        FieldSchema field = FieldSchema.builder()
                .id("amount")
                .type(FieldSchema.FieldDataType.NUMERIC)
                .length(12)
                .build();

        // When
        Object value = SchemaExampleGenerator.generateFieldValue(field);

        // Then
        assertNotNull(value);
        assertTrue(value instanceof String);
        assertEquals(12, ((String) value).length());
        assertTrue(((String) value).matches("\\d+"));

        System.out.println("Numeric value: " + value);
    }

    @Test
    void generateFieldValue_alphanumericField() {
        // Given
        FieldSchema field = FieldSchema.builder()
                .id("terminalId")
                .type(FieldSchema.FieldDataType.ALPHANUMERIC)
                .length(8)
                .build();

        // When
        Object value = SchemaExampleGenerator.generateFieldValue(field);

        // Then
        assertNotNull(value);
        assertTrue(value instanceof String);
        assertEquals(8, ((String) value).length());
        assertTrue(((String) value).matches("[A-Z0-9]+"));

        System.out.println("Alphanumeric value: " + value);
    }

    @Test
    void generateFieldValue_withDefaultValue() {
        // Given
        FieldSchema field = FieldSchema.builder()
                .id("processingCode")
                .type(FieldSchema.FieldDataType.NUMERIC)
                .length(6)
                .defaultValue("010000")
                .build();

        // When
        Object value = SchemaExampleGenerator.generateFieldValue(field);

        // Then
        assertEquals("010000", value);
    }

    @Test
    void generateFieldValue_track2Field() {
        // Given
        FieldSchema field = FieldSchema.builder()
                .id("track2")
                .type(FieldSchema.FieldDataType.TRACK2)
                .length(37)
                .build();

        // When
        Object value = SchemaExampleGenerator.generateFieldValue(field);

        // Then
        assertNotNull(value);
        assertTrue(value instanceof String);
        assertTrue(((String) value).startsWith(";"));
        assertTrue(((String) value).endsWith("?"));
        assertTrue(((String) value).contains("="));

        System.out.println("Track2 value: " + value);
    }

    @Test
    void generateFieldValue_compositeField() {
        // Given
        FieldSchema compositeField = FieldSchema.builder()
                .id("additionalData")
                .type(FieldSchema.FieldDataType.COMPOSITE)
                .length(100)
                .fields(List.of(
                        FieldSchema.builder()
                                .id("subfield1")
                                .type(FieldSchema.FieldDataType.ALPHANUMERIC)
                                .length(10)
                                .build(),
                        FieldSchema.builder()
                                .id("subfield2")
                                .type(FieldSchema.FieldDataType.NUMERIC)
                                .length(6)
                                .build()
                ))
                .build();

        // When
        Object value = SchemaExampleGenerator.generateFieldValue(compositeField);

        // Then
        assertNotNull(value);
        assertTrue(value instanceof java.util.Map);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> composite = (java.util.Map<String, Object>) value;
        assertTrue(composite.containsKey("subfield1"));
        assertTrue(composite.containsKey("subfield2"));

        System.out.println("Composite value: " + value);
    }

    @Test
    void generateFieldsOnlyExample_generatesValidJson() {
        // Given
        List<FieldSchema> fields = List.of(
                FieldSchema.builder()
                        .id("field1")
                        .name("First Field")
                        .type(FieldSchema.FieldDataType.ALPHANUMERIC)
                        .length(10)
                        .build(),
                FieldSchema.builder()
                        .id("field2")
                        .name("Second Field")
                        .type(FieldSchema.FieldDataType.NUMERIC)
                        .length(12)
                        .lengthType(FieldSchema.GenericLengthType.LLVAR)
                        .required(true)
                        .build()
        );

        // When
        String example = SchemaExampleGenerator.generateFieldsOnlyExample(fields);

        // Then
        assertNotNull(example);
        assertTrue(example.contains("\"field1\""));
        assertTrue(example.contains("\"field2\""));
        assertTrue(example.contains("\"required\" : true"));

        System.out.println("=== Fields Only Example ===");
        System.out.println(example);
    }

    @Test
    void createMinimalExample_isValid() {
        // When
        MessageSchema schema = SchemaExampleGenerator.createMinimalExample();

        // Then
        assertNotNull(schema);
        assertEquals("Example Protocol", schema.getName());
        assertEquals("1.0.0", schema.getVersion());
        assertNotNull(schema.getFields());
        assertEquals(2, schema.getFields().size());

        // Validate with JsonSchemaLoader
        assertDoesNotThrow(() -> JsonSchemaLoader.validateSchema(schema));
    }

    @Test
    void createFiscAtmExample_isValid() {
        // When
        MessageSchema schema = SchemaExampleGenerator.createFiscAtmExample();

        // Then
        assertNotNull(schema);
        assertEquals("FISC ATM Protocol", schema.getName());
        assertEquals("FISC", schema.getVendor());
        assertNotNull(schema.getFields());
        assertTrue(schema.getFields().size() >= 5);
        assertNotNull(schema.getEncoding());
        assertNotNull(schema.getHeader());

        // Validate with JsonSchemaLoader
        assertDoesNotThrow(() -> JsonSchemaLoader.validateSchema(schema));
    }
}
