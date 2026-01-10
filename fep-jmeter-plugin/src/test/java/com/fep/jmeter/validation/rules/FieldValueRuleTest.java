package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FieldValueRule.
 */
@DisplayName("FieldValueRule Tests")
class FieldValueRuleTest {

    @Test
    @DisplayName("Should return VALUE as rule type")
    void shouldReturnValueAsRuleType() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000");

        assertThat(rule.getRuleType()).isEqualTo("VALUE");
    }

    @Test
    @DisplayName("Should parse single value specification")
    void shouldParseSingleValueSpecification() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000|310000");

        assertThat(rule.getAllowedValues()).containsKey(3);
        assertThat(rule.getAllowedValues().get(3))
            .containsExactlyInAnyOrder("010000", "400000", "310000");
    }

    @Test
    @DisplayName("Should parse multiple value specifications")
    void shouldParseMultipleValueSpecifications() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000;70=001|101|301");

        assertThat(rule.getAllowedValues()).hasSize(2);
        assertThat(rule.getAllowedValues().get(3)).contains("010000", "400000");
        assertThat(rule.getAllowedValues().get(70)).contains("001", "101", "301");
    }

    @Test
    @DisplayName("Should handle specifications with spaces")
    void shouldHandleSpecificationsWithSpaces() {
        FieldValueRule rule = new FieldValueRule(" 3 = 010000 | 400000 ");

        assertThat(rule.getAllowedValues().get(3)).contains("010000", "400000");
    }

    @Test
    @DisplayName("Should handle empty specification")
    void shouldHandleEmptySpecification() {
        FieldValueRule rule = new FieldValueRule("");

        assertThat(rule.getAllowedValues()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null specification")
    void shouldHandleNullSpecification() {
        FieldValueRule rule = new FieldValueRule(null);

        assertThat(rule.getAllowedValues()).isEmpty();
    }

    @Test
    @DisplayName("Should skip invalid field number")
    void shouldSkipInvalidFieldNumber() {
        FieldValueRule rule = new FieldValueRule("abc=010000|400000;3=010000");

        assertThat(rule.getAllowedValues()).hasSize(1);
        assertThat(rule.getAllowedValues()).containsKey(3);
    }

    @Test
    @DisplayName("Should pass validation when field value is allowed")
    void shouldPassValidationWhenFieldValueIsAllowed() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000|310000");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(3, "010000");

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when field value is not allowed")
    void shouldFailValidationWhenFieldValueIsNotAllowed() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000|310000");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(3, "999999");

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(3);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.VALUE);
        assertThat(errors.get(0).getActual()).isEqualTo("999999");
    }

    @Test
    @DisplayName("Should skip missing fields")
    void shouldSkipMissingFields() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000;70=001|101");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(3, "010000");
        // Field 70 is not present

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple value errors")
    void shouldReportMultipleValueErrors() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000;70=001|101");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(3, "999999");  // Invalid
        message.setField(70, "999");    // Invalid

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ValidationError::getFieldNumber)
            .containsExactlyInAnyOrder(3, 70);
    }

    @Test
    @DisplayName("Should generate proper description")
    void shouldGenerateProperDescription() {
        FieldValueRule rule = new FieldValueRule("3=010000|400000;70=001");

        String description = rule.getDescription();

        assertThat(description).contains("Allowed values:");
        assertThat(description).contains("F3=");
        assertThat(description).contains("F70=");
    }
}
