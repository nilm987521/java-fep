package com.fep.message.generic.message;

import com.fep.message.generic.schema.FieldSchema;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for dynamic default value expressions in GenericMessage.
 * Supports expressions like @NOW(format), @DATE, @TIME, @UUID.
 */
class GenericMessageDynamicDefaultTest {

    @Test
    void shouldResolveDynamicNowWithDateFormat() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txDate", "length": 8, "defaultValue": "@NOW(yyyyMMdd)" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(msg.getFieldAsString("txDate")).isEqualTo(today);
    }

    @Test
    void shouldResolveDynamicNowWithTimeFormat() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txTime", "length": 6, "defaultValue": "@NOW(HHmmss)" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String actualTime = msg.getFieldAsString("txTime");
        assertThat(actualTime).matches("\\d{6}");
        // Time should be close to now (within a minute to account for test execution time)
        int actualHour = Integer.parseInt(actualTime.substring(0, 2));
        int expectedHour = LocalTime.now().getHour();
        assertThat(actualHour).isEqualTo(expectedHour);
    }

    @Test
    void shouldResolveDynamicNowWithFullTimestampFormat() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "timestamp", "length": 14, "defaultValue": "@NOW(yyyyMMddHHmmss)" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String timestamp = msg.getFieldAsString("timestamp");
        assertThat(timestamp).matches("\\d{14}");
        // Should start with today's date
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(timestamp).startsWith(today);
    }

    @Test
    void shouldResolveDynamicNowWithDefaultFormat() {
        // Given - @NOW() without format should use default yyyyMMddHHmmss
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "timestamp", "length": 14, "defaultValue": "@NOW()" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String timestamp = msg.getFieldAsString("timestamp");
        assertThat(timestamp).matches("\\d{14}");
    }

    @Test
    void shouldResolveMixedExpression() {
        // Given - "TXN@NOW(yyyyMMdd)001" → "TXN20260124001"
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txnId", "length": 17, "defaultValue": "TXN@NOW(yyyyMMdd)001" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String expected = "TXN" + today + "001";
        assertThat(msg.getFieldAsString("txnId")).isEqualTo(expected);
    }

    @Test
    void shouldResolveDateShortcut() {
        // Given - @DATE is shortcut for @NOW(yyyyMMdd)
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "date", "length": 8, "defaultValue": "@DATE" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(msg.getFieldAsString("date")).isEqualTo(today);
    }

    @Test
    void shouldResolveTimeShortcut() {
        // Given - @TIME is shortcut for @NOW(HHmmss)
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "time", "length": 6, "defaultValue": "@TIME" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String actualTime = msg.getFieldAsString("time");
        assertThat(actualTime).matches("\\d{6}");
    }

    @Test
    void shouldResolveUuidExpression() {
        // Given - @UUID generates a 12-character UUID
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "uuid", "length": 12, "defaultValue": "@UUID" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String uuid = msg.getFieldAsString("uuid");
        assertThat(uuid).hasSize(12);
        assertThat(uuid).matches("[a-f0-9]{12}");
    }

    @Test
    void shouldPreserveStaticDefaults() {
        // Given - static default values should not be affected
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "staticField", "length": 10, "defaultValue": "STATIC123" },
                { "id": "dynamicField", "length": 8, "defaultValue": "@DATE" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        assertThat(msg.getFieldAsString("staticField")).isEqualTo("STATIC123");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(msg.getFieldAsString("dynamicField")).isEqualTo(today);
    }

    @Test
    void shouldNotOverrideExplicitlySetField() {
        // Given - explicitly set fields should not be overwritten by defaults
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txDate", "length": 8, "defaultValue": "@DATE" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);
        msg.setField("txDate", "20200101");

        // When
        msg.populateDefaults();

        // Then - explicit value should be preserved
        assertThat(msg.getFieldAsString("txDate")).isEqualTo("20200101");
    }

    @Test
    void shouldPreserveUnknownExpressions() {
        // Given - unknown expressions should be kept as-is
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "unknown", "length": 20, "defaultValue": "@UNKNOWN(test)" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        assertThat(msg.getFieldAsString("unknown")).isEqualTo("@UNKNOWN(test)");
    }

    @Test
    void shouldResolveDynamicInGetAllFieldsWithDefaults() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txDate", "length": 8, "defaultValue": "@DATE" },
                { "id": "amount", "length": 12 }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);
        msg.setField("amount", "100");

        // When - don't call populateDefaults(), test getAllFieldsWithDefaults() directly
        var allFields = msg.getAllFieldsWithDefaults();

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(allFields.get("txDate")).isEqualTo(today);
        assertThat(allFields.get("amount")).isEqualTo("100");
    }

    @Test
    void shouldResolveDynamicInGetFieldWithDefault() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "txDate", "length": 8, "defaultValue": "@DATE" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When - don't call populateDefaults(), test getFieldWithDefault() directly
        Object value = msg.getFieldWithDefault("txDate");

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(value).isEqualTo(today);
    }

    @Test
    void shouldHandleNullValue() {
        // Given
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "field1", "length": 8 }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then - field without default should remain null
        assertThat(msg.getFieldAsString("field1")).isNull();
    }

    @Test
    void shouldHandleValueWithoutAtSymbol() {
        // Given - no @ symbol means no dynamic expression
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "field1", "length": 20, "defaultValue": "NORMAL_VALUE" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        assertThat(msg.getFieldAsString("field1")).isEqualTo("NORMAL_VALUE");
    }

    @Test
    void shouldResolveMultipleDynamicExpressionsInSameValue() {
        // Given - multiple @NOW expressions in the same value
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "combined", "length": 20, "defaultValue": "@DATE-@TIME" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        GenericMessage msg = new GenericMessage(schema);

        // When
        msg.populateDefaults();

        // Then
        String combined = msg.getFieldAsString("combined");
        assertThat(combined).matches("\\d{8}-\\d{6}");
    }
}
