package com.fep.jmeter.validation.rules;

import com.fep.jmeter.validation.ValidationError;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RequiredFieldRule.
 */
@DisplayName("RequiredFieldRule Tests")
class RequiredFieldRuleTest {

    @Test
    @DisplayName("Should return REQUIRED as rule type")
    void shouldReturnRequiredAsRuleType() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3, 4);

        assertThat(rule.getRuleType()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("Should create rule from varargs")
    void shouldCreateRuleFromVarargs() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3, 4, 11, 41);

        assertThat(rule.getRequiredFields()).containsExactlyInAnyOrder(2, 3, 4, 11, 41);
    }

    @Test
    @DisplayName("Should create rule from comma-separated string")
    void shouldCreateRuleFromCommaSeparatedString() {
        RequiredFieldRule rule = new RequiredFieldRule("2,3,4,11,41");

        assertThat(rule.getRequiredFields()).containsExactlyInAnyOrder(2, 3, 4, 11, 41);
    }

    @Test
    @DisplayName("Should handle string with spaces")
    void shouldHandleStringWithSpaces() {
        RequiredFieldRule rule = new RequiredFieldRule(" 2 , 3 , 4 ");

        assertThat(rule.getRequiredFields()).containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    @DisplayName("Should handle empty string")
    void shouldHandleEmptyString() {
        RequiredFieldRule rule = new RequiredFieldRule("");

        assertThat(rule.getRequiredFields()).isEmpty();
    }

    @Test
    @DisplayName("Should pass validation when all required fields are present")
    void shouldPassValidationWhenAllRequiredFieldsArePresent() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3, 4);

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "4111111111111111");
        message.setField(3, "000000");
        message.setField(4, "000000010000");

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when required field is missing")
    void shouldFailValidationWhenRequiredFieldIsMissing() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3, 4);

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "4111111111111111");
        // Field 3 is missing
        message.setField(4, "000000010000");

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(3);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.MISSING);
    }

    @Test
    @DisplayName("Should fail validation when field is present but empty")
    void shouldFailValidationWhenFieldIsPresentButEmpty() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3);

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "4111111111111111");
        message.setField(3, "");  // Empty value

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFieldNumber()).isEqualTo(3);
        assertThat(errors.get(0).getErrorType()).isEqualTo(ValidationError.ErrorType.MISSING);
    }

    @Test
    @DisplayName("Should report multiple missing fields")
    void shouldReportMultipleMissingFields() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 3, 4, 11, 41);

        Iso8583Message message = new Iso8583Message("0200");
        message.setField(2, "4111111111111111");
        // Fields 3, 4, 11, 41 are missing

        List<ValidationError> errors = rule.validate(message);

        assertThat(errors).hasSize(4);
        assertThat(errors).extracting(ValidationError::getFieldNumber)
            .containsExactlyInAnyOrder(3, 4, 11, 41);
    }

    @Test
    @DisplayName("Should generate proper description")
    void shouldGenerateProperDescription() {
        RequiredFieldRule rule = new RequiredFieldRule(2, 11, 41);

        String description = rule.getDescription();

        assertThat(description).contains("Required fields:");
        assertThat(description).contains("2");
        assertThat(description).contains("11");
        assertThat(description).contains("41");
    }
}
