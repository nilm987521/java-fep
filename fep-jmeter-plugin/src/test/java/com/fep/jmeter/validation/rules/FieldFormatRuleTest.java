package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FieldFormatRule.
 */
@DisplayName("FieldFormatRule Tests")
class FieldFormatRuleTest {

    @Test
    @DisplayName("Should return FORMAT as rule type")
    void shouldReturnFormatAsRuleType() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12)");

        assertThat(rule.getRuleType()).isEqualTo("FORMAT");
    }

    @Test
    @DisplayName("Should parse exact length format")
    void shouldParseExactLengthFormat() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12)");

        assertThat(rule.getFormats()).containsKey(4);
    }

    @Test
    @DisplayName("Should parse range length format")
    void shouldParseRangeLengthFormat() {
        FieldFormatRule rule = new FieldFormatRule("2=N(13-19)");

        assertThat(rule.getFormats()).containsKey(2);
    }

    @Test
    @DisplayName("Should parse up-to length format")
    void shouldParseUpToLengthFormat() {
        FieldFormatRule rule = new FieldFormatRule("44=AN(..25)");

        assertThat(rule.getFormats()).containsKey(44);
    }

    @Test
    @DisplayName("Should parse multiple format specifications")
    void shouldParseMultipleFormatSpecifications() {
        FieldFormatRule rule = new FieldFormatRule("2=N(13-19);3=N(6);4=N(12);41=AN(8)");

        assertThat(rule.getFormats()).hasSize(4);
        assertThat(rule.getFormats()).containsKeys(2, 3, 4, 41);
    }

    @Test
    @DisplayName("Should handle empty specification")
    void shouldHandleEmptySpecification() {
        FieldFormatRule rule = new FieldFormatRule("");

        assertThat(rule.getFormats()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null specification")
    void shouldHandleNullSpecification() {
        FieldFormatRule rule = new FieldFormatRule(null);

        assertThat(rule.getFormats()).isEmpty();
    }

    @Test
    @DisplayName("Should skip invalid format specifications")
    void shouldSkipInvalidFormatSpecifications() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12);invalid;3=N(6)");

        assertThat(rule.getFormats()).hasSize(2);
        assertThat(rule.getFormats()).containsKeys(4, 3);
    }

    @Test
    @DisplayName("Should pass validation for numeric field with correct length")
    void shouldPassValidationForNumericFieldWithCorrectLength() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "000000010000");  // 12 numeric digits

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation for field with incorrect length")
    void shouldFailValidationForFieldWithIncorrectLength() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "12345");  // Only 5 digits, expected 12

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(4);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.FORMAT);
    }

    @Test
    @DisplayName("Should pass validation for field within range length")
    void shouldPassValidationForFieldWithinRangeLength() {
        FieldFormatRule rule = new FieldFormatRule("2=N(13-19)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "4111111111111111");  // 16 digits, within 13-19

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation for field outside range length")
    void shouldFailValidationForFieldOutsideRangeLength() {
        FieldFormatRule rule = new FieldFormatRule("2=N(13-19)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "411111111");  // Only 9 digits, expected 13-19

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should fail validation for numeric field with alpha characters")
    void shouldFailValidationForNumericFieldWithAlphaCharacters() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "00000ABC0000");  // Contains alpha characters

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(4);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.FORMAT);
    }

    @Test
    @DisplayName("Should pass validation for alphanumeric field")
    void shouldPassValidationForAlphanumericField() {
        FieldFormatRule rule = new FieldFormatRule("41=AN(8)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(41, "ATM00001");  // 8 alphanumeric characters

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should pass validation for alpha field")
    void shouldPassValidationForAlphaField() {
        FieldFormatRule rule = new FieldFormatRule("43=A(5)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(43, "ABCDE");  // 5 alpha characters

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation for alpha field with numbers")
    void shouldFailValidationForAlphaFieldWithNumbers() {
        FieldFormatRule rule = new FieldFormatRule("43=A(5)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(43, "ABC12");  // Contains numbers

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
    }

    @Test
    @DisplayName("Should pass validation for ANS field with special characters")
    void shouldPassValidationForANSFieldWithSpecialCharacters() {
        FieldFormatRule rule = new FieldFormatRule("63=ANS(10)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(63, "ABC-123@#$");  // Special characters allowed

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should skip missing fields")
    void shouldSkipMissingFields() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12);11=N(6)");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(4, "000000010000");
        // Field 11 is not present

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should generate proper description")
    void shouldGenerateProperDescription() {
        FieldFormatRule rule = new FieldFormatRule("4=N(12);2=N(13-19)");

        String description = rule.getDescription();

        assertThat(description).contains("Field formats:");
        assertThat(description).contains("F4=");
        assertThat(description).contains("F2=");
    }
}
