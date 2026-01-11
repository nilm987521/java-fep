package com.fep.jmeter.validation;

import com.fep.message.iso8583.Iso8583Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine for validating ISO 8583 messages against configured rules.
 *
 * <p>Supports both text-based and JSON configuration formats.
 *
 * <p>Text format usage:
 * <pre>{@code
 * MessageValidationEngine engine = new MessageValidationEngine();
 * engine.configure("""
 *     REQUIRED:2,3,4,11,41,42
 *     FORMAT:2=N(13-19);3=N(6);4=N(12)
 *     VALUE:3=010000|400000|310000
 *     MTI:0800=REQUIRED:70;VALUE:70=001|101|301
 *     """);
 * }</pre>
 *
 * <p>JSON format usage:
 * <pre>{@code
 * engine.configure("""
 *     {
 *       "globalRules": {
 *         "required": [2, 3, 4, 11, 41, 42],
 *         "format": {
 *           "2": "N(13-19)",
 *           "3": "N(6)"
 *         },
 *         "value": {
 *           "3": ["010000", "400000", "310000"]
 *         }
 *       },
 *       "mtiRules": {
 *         "0800": {
 *           "required": [70],
 *           "value": {
 *             "70": ["001", "101", "301"]
 *           }
 *         }
 *       }
 *     }
 *     """);
 *
 * ValidationResult result = engine.validate(message);
 * if (!result.isValid()) {
 *     System.out.println(result.getErrorSummary());
 * }
 * }</pre>
 */
@Slf4j
public class MessageValidationEngine {

    private final ValidationRuleParser textParser;
    private final ValidationRuleJsonParser jsonParser;

    @Getter
    private ValidationRuleParser.ParsedRules rules;

    @Getter
    private boolean enabled = true;

    @Getter
    private boolean jsonFormat = false;

    public MessageValidationEngine() {
        this.textParser = new ValidationRuleParser();
        this.jsonParser = new ValidationRuleJsonParser();
        this.rules = new ValidationRuleParser.ParsedRules();
    }

    /**
     * Configures the engine with validation rules.
     *
     * <p>Automatically detects whether the config is JSON or text format.
     *
     * @param config the rule configuration string (JSON or text format)
     */
    public void configure(String config) {
        if (config == null || config.trim().isEmpty()) {
            this.rules = new ValidationRuleParser.ParsedRules();
            this.jsonFormat = false;
            return;
        }

        if (ValidationRuleJsonParser.isJson(config)) {
            this.rules = jsonParser.parse(config);
            this.jsonFormat = true;
            log.info("[ValidationEngine] Configured with JSON format: {} global rules and {} MTI-specific rule sets",
                rules.globalRules.size(), rules.mtiRules.size());
        } else {
            this.rules = textParser.parse(config);
            this.jsonFormat = false;
            log.info("[ValidationEngine] Configured with text format: {} global rules and {} MTI-specific rule sets",
                rules.globalRules.size(), rules.mtiRules.size());
        }
    }

    /**
     * Configures the engine with JSON validation rules.
     *
     * @param jsonConfig the JSON rule configuration string
     */
    public void configureJson(String jsonConfig) {
        this.rules = jsonParser.parse(jsonConfig);
        this.jsonFormat = true;
        log.info("[ValidationEngine] Configured with JSON: {} global rules and {} MTI-specific rule sets",
            rules.globalRules.size(), rules.mtiRules.size());
    }

    /**
     * Configures the engine with text-based validation rules.
     *
     * @param textConfig the text rule configuration string
     */
    public void configureText(String textConfig) {
        this.rules = textParser.parse(textConfig);
        this.jsonFormat = false;
        log.info("[ValidationEngine] Configured with text: {} global rules and {} MTI-specific rule sets",
            rules.globalRules.size(), rules.mtiRules.size());
    }

    /**
     * Sets whether validation is enabled.
     *
     * @param enabled true to enable validation
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Validates a message against configured rules.
     *
     * @param message the message to validate
     * @return validation result
     */
    public ValidationResult validate(Iso8583Message message) {
        long startTime = System.currentTimeMillis();

        if (!enabled || !rules.hasRules()) {
            return ValidationResult.success(System.currentTimeMillis() - startTime);
        }

        String mti = message.getMti();
        List<ValidationRule> applicableRules = rules.getRulesForMti(mti);

        if (applicableRules.isEmpty()) {
            return ValidationResult.success(System.currentTimeMillis() - startTime);
        }

        List<ValidationError> allErrors = new ArrayList<>();

        for (ValidationRule rule : applicableRules) {
            try {
                List<ValidationError> errors = rule.validate(message);
                allErrors.addAll(errors);

                if (!errors.isEmpty()) {
                    log.debug("[ValidationEngine] Rule {} found {} errors for MTI={}",
                        rule.getRuleType(), errors.size(), mti);
                }
            } catch (Exception e) {
                log.error("[ValidationEngine] Error executing rule {}: {}",
                    rule.getRuleType(), e.getMessage(), e);
            }
        }

        long validationTime = System.currentTimeMillis() - startTime;

        if (allErrors.isEmpty()) {
            log.debug("[ValidationEngine] Validation passed for MTI={} in {}ms", mti, validationTime);
            return ValidationResult.success(validationTime);
        } else {
            log.debug("[ValidationEngine] Validation failed for MTI={}: {} errors in {}ms",
                mti, allErrors.size(), validationTime);
            return ValidationResult.failure(allErrors, validationTime);
        }
    }

    /**
     * Creates a validation callback function for use with FiscDualChannelSimulatorEngine.
     *
     * <p>Returns null if validation passes, error message if fails.
     *
     * @return callback function
     */
    public java.util.function.Function<Iso8583Message, String> createValidationCallback() {
        return message -> {
            ValidationResult result = validate(message);
            return result.isValid() ? null : result.getErrorSummary();
        };
    }

    /**
     * Converts the given text-based configuration to JSON format.
     *
     * @param textConfig the text-based configuration
     * @return JSON string representation
     */
    public static String convertToJson(String textConfig) {
        return ValidationRuleJsonParser.convertToJson(textConfig);
    }

    /**
     * Gets a summary of configured rules.
     */
    public String getRulesSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Rules Summary:\n");
        sb.append("Format: ").append(jsonFormat ? "JSON" : "Text").append("\n");
        sb.append("Global Rules: ").append(rules.globalRules.size()).append("\n");

        for (ValidationRule rule : rules.globalRules) {
            sb.append("  - ").append(rule.getRuleType()).append(": ")
              .append(rule.getDescription()).append("\n");
        }

        if (!rules.mtiRules.isEmpty()) {
            sb.append("MTI-Specific Rules:\n");
            rules.mtiRules.forEach((mti, mtiRules) -> {
                sb.append("  MTI ").append(mti).append(": ").append(mtiRules.size()).append(" rules\n");
                for (ValidationRule rule : mtiRules) {
                    sb.append("    - ").append(rule.getRuleType()).append(": ")
                      .append(rule.getDescription()).append("\n");
                }
            });
        }

        return sb.toString();
    }
}
