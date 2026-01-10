package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FieldLengthRule.
 */
@DisplayName("FieldLengthRule Tests")
class FieldLengthRuleTest {

    @Test
    @DisplayName("Should return LENGTH as rule type")
    void shouldReturnLengthAsRuleType() {
        FieldLengthRule rule = new FieldLengthRule("4=12");

        assertThat(rule.getRuleType()).isEqualTo("LENGTH");
    }

    @Test
    @DisplayName("Should parse single length specification")
    void shouldParseSingleLengthSpecification() {
        FieldLengthRule rule = new FieldLengthRule("4=12");

        assertThat(rule.getExpectedLengths()).containsEntry(4, 12);
    }

    @Test
    @DisplayName("Should parse multiple length specifications")
    void shouldParseMultipleLengthSpecifications() {
        FieldLengthRule rule = new FieldLengthRule("4=12;11=6;37=12");

        assertThat(rule.getExpectedLengths()).hasSize(3);
        assertThat(rule.getExpectedLengths()).containsEntry(4, 12);
        assertThat(rule.getExpectedLengths()).containsEntry(11, 6);
        assertThat(rule.getExpectedLengths()).containsEntry(37, 12);
    }

    @Test
    @DisplayName("Should handle specifications with spaces")
    void shouldHandleSpecificationsWithSpaces() {
        FieldLengthRule rule = new FieldLengthRule(" 4 = 12 ; 11 = 6 ");

        assertThat(rule.getExpectedLengths()).hasSize(2);
        assertThat(rule.getExpectedLengths()).containsEntry(4, 12);
        assertThat(rule.getExpectedLengths()).containsEntry(11, 6);
    }

    @Test
    @DisplayName("Should handle empty specification")
    void shouldHandleEmptySpecification() {
        FieldLengthRule rule = new FieldLengthRule("");

        assertThat(rule.getExpectedLengths()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null specification")
    void shouldHandleNullSpecification() {
        FieldLengthRule rule = new FieldLengthRule(null);

        assertThat(rule.getExpectedLengths()).isEmpty();
    }

    @Test
    @DisplayName("Should skip invalid specifications")
    void shouldSkipInvalidSpecifications() {
        FieldLengthRule rule = new FieldLengthRule("4=12;invalid;11=abc;37=12");

        assertThat(rule.getExpectedLengths()).hasSize(2);
        assertThat(rule.getExpectedLengths()).containsEntry(4, 12);
        assertThat(rule.getExpectedLengths()).containsEntry(37, 12);
    }

    @Test
    @DisplayName("Should pass validation when field length matches")
    void shouldPassValidationWhenFieldLengthMatches() {
        FieldLengthRule rule = new FieldLengthRule("4=12;11=6");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "000000010000");  // 12 chars
        message.setField(11, "123456");       // 6 chars

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when field length does not match")
    void shouldFailValidationWhenFieldLengthDoesNotMatch() {
        FieldLengthRule rule = new FieldLengthRule("4=12");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "12345678");  // Only 8 chars, expected 12

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(4);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.LENGTH);
        assertThat(errors.get(0).getExpected()).isEqualTo("12");
        assertThat(errors.get(0).getActual()).isEqualTo("8");
    }

    @Test
    @DisplayName("Should skip missing fields")
    void shouldSkipMissingFields() {
        FieldLengthRule rule = new FieldLengthRule("4=12;11=6");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "000000010000");
        // Field 11 is not present

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple length errors")
    void shouldReportMultipleLengthErrors() {
        FieldLengthRule rule = new FieldLengthRule("4=12;11=6;37=12");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "12345");     // 5 chars, expected 12
        message.setField(11, "12345678"); // 8 chars, expected 6
        message.setField(37, "123456789012"); // 12 chars, correct

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ValidationError::getFieldNumber)
            .containsExactlyInAnyOrder(4, 11);
    }

    @Test
    @DisplayName("Should generate proper description")
    void shouldGenerateProperDescription() {
        FieldLengthRule rule = new FieldLengthRule("4=12;11=6");

        String description = rule.getDescription();

        assertThat(description).contains("Field lengths:");
        assertThat(description).contains("F4=12");
        assertThat(description).contains("F11=6");
    }
}
