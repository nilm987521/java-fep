package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PatternMatchRule.
 */
@DisplayName("PatternMatchRule Tests")
class PatternMatchRuleTest {

    @Test
    @DisplayName("Should return PATTERN as rule type")
    void shouldReturnPatternAsRuleType() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$");

        assertThat(rule.getRuleType()).isEqualTo("PATTERN");
    }

    @Test
    @DisplayName("Should parse single pattern specification")
    void shouldParseSinglePatternSpecification() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$");

        assertThat(rule.getPatterns()).containsKey(37);
        assertThat(rule.getPatternStrings()).containsEntry(37, "^[A-Z0-9]{12}$");
    }

    @Test
    @DisplayName("Should parse multiple pattern specifications")
    void shouldParseMultiplePatternSpecifications() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$;7=^\\d{10}$");

        assertThat(rule.getPatterns()).hasSize(2);
        assertThat(rule.getPatterns()).containsKeys(37, 7);
    }

    @Test
    @DisplayName("Should handle empty specification")
    void shouldHandleEmptySpecification() {
        PatternMatchRule rule = new PatternMatchRule("");

        assertThat(rule.getPatterns()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null specification")
    void shouldHandleNullSpecification() {
        PatternMatchRule rule = new PatternMatchRule(null);

        assertThat(rule.getPatterns()).isEmpty();
    }

    @Test
    @DisplayName("Should skip invalid field number")
    void shouldSkipInvalidFieldNumber() {
        PatternMatchRule rule = new PatternMatchRule("abc=^[A-Z]+$;37=^[A-Z0-9]{12}$");

        assertThat(rule.getPatterns()).hasSize(1);
        assertThat(rule.getPatterns()).containsKey(37);
    }

    @Test
    @DisplayName("Should skip invalid regex pattern")
    void shouldSkipInvalidRegexPattern() {
        PatternMatchRule rule = new PatternMatchRule("37=[invalid(;7=^\\d{10}$");

        assertThat(rule.getPatterns()).hasSize(1);
        assertThat(rule.getPatterns()).containsKey(7);
    }

    @Test
    @DisplayName("Should pass validation when field matches pattern")
    void shouldPassValidationWhenFieldMatchesPattern() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(37, "ABC123DEF456");  // Matches pattern

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when field does not match pattern")
    void shouldFailValidationWhenFieldDoesNotMatchPattern() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(37, "abc123");  // Lower case, wrong length

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(37);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.PATTERN);
        assertThat(errors.get(0).getExpected()).isEqualTo("^[A-Z0-9]{12}$");
        assertThat(errors.get(0).getActual()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("Should pass validation for numeric pattern")
    void shouldPassValidationForNumericPattern() {
        PatternMatchRule rule = new PatternMatchRule("7=^\\d{10}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(7, "0109123456");  // 10 digits

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation for numeric pattern with non-digits")
    void shouldFailValidationForNumericPatternWithNonDigits() {
        PatternMatchRule rule = new PatternMatchRule("7=^\\d{10}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(7, "01091234AB");  // Contains letters

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
    }

    @Test
    @DisplayName("Should skip missing fields")
    void shouldSkipMissingFields() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$;7=^\\d{10}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(37, "ABC123DEF456");
        // Field 7 is not present

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should report multiple pattern errors")
    void shouldReportMultiplePatternErrors() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$;7=^\\d{10}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(37, "invalid");      // Does not match
        message.setField(7, "invalid123");    // Does not match

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ValidationError::getFieldNumber)
            .containsExactlyInAnyOrder(37, 7);
    }

    @Test
    @DisplayName("Should validate complex patterns")
    void shouldValidateComplexPatterns() {
        // Pattern for Taiwan mobile phone number format
        PatternMatchRule rule = new PatternMatchRule("102=^09\\d{8}$");

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(102, "0912345678");  // Valid Taiwan mobile

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should generate proper description")
    void shouldGenerateProperDescription() {
        PatternMatchRule rule = new PatternMatchRule("37=^[A-Z0-9]{12}$;7=^\\d{10}$");

        String description = rule.getDescription();

        assertThat(description).contains("Field patterns:");
        assertThat(description).contains("F37=");
        assertThat(description).contains("F7=");
    }
}
