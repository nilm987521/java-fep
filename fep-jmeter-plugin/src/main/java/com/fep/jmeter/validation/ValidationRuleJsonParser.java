package com.fep.jmeter.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.jmeter.validation.rules.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Parses validation rules from JSON configuration.
 *
 * <p>JSON format:
 * <pre>{@code
 * {
 *   "globalRules": {
 *     "required": [2, 3, 4, 11, 41, 42],
 *     "format": {
 *       "2": "N(13-19)",
 *       "3": "N(6)",
 *       "4": "N(12)",
 *       "41": "AN(8)"
 *     },
 *     "value": {
 *       "3": ["010000", "400000", "310000"]
 *     },
 *     "length": {
 *       "4": 12,
 *       "11": 6,
 *       "37": 12
 *     },
 *     "pattern": {
 *       "37": "^[A-Z0-9]{12}$",
 *       "7": "^\\d{10}$"
 *     }
 *   },
 *   "mtiRules": {
 *     "0200": {
 *       "required": [2, 3, 4, 11],
 *       "format": {
 *         "2": "N(13-19)"
 *       },
 *       "value": {
 *         "3": ["010000", "400000"]
 *       }
 *     },
 *     "0800": {
 *       "required": [70],
 *       "value": {
 *         "70": ["001", "101", "301", "161"]
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
@Slf4j
public class ValidationRuleJsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses validation rules from JSON configuration.
     *
     * @param jsonConfig the JSON configuration string
     * @return parsed validation rules
     */
    public ValidationRuleParser.ParsedRules parse(String jsonConfig) {
        ValidationRuleParser.ParsedRules result = new ValidationRuleParser.ParsedRules();

        if (jsonConfig == null || jsonConfig.isEmpty()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonConfig);

            // Parse global rules
            JsonNode globalRulesNode = root.get("globalRules");
            if (globalRulesNode != null) {
                parseRuleSet(globalRulesNode, result.globalRules);
            }

            // Parse MTI-specific rules
            JsonNode mtiRulesNode = root.get("mtiRules");
            if (mtiRulesNode != null && mtiRulesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mtiRulesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String mti = entry.getKey();
                    List<ValidationRule> mtiRules = new ArrayList<>();
                    parseRuleSet(entry.getValue(), mtiRules);
                    if (!mtiRules.isEmpty()) {
                        result.mtiRules.put(mti, mtiRules);
                    }
                }
            }

            log.info("[JsonParser] Parsed {} global rules and {} MTI-specific rule sets",
                result.globalRules.size(), result.mtiRules.size());

        } catch (Exception e) {
            log.error("[JsonParser] Failed to parse JSON config: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Parses a rule set (either global or MTI-specific) from a JSON node.
     */
    private void parseRuleSet(JsonNode ruleSetNode, List<ValidationRule> rules) {
        if (ruleSetNode == null || !ruleSetNode.isObject()) {
            return;
        }

        // Parse REQUIRED rules
        JsonNode requiredNode = ruleSetNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            String spec = buildRequiredSpec(requiredNode);
            if (!spec.isEmpty()) {
                rules.add(new RequiredFieldRule(spec));
            }
        }

        // Parse FORMAT rules
        JsonNode formatNode = ruleSetNode.get("format");
        if (formatNode != null && formatNode.isObject()) {
            String spec = buildFormatSpec(formatNode);
            if (!spec.isEmpty()) {
                rules.add(new FieldFormatRule(spec));
            }
        }

        // Parse VALUE rules
        JsonNode valueNode = ruleSetNode.get("value");
        if (valueNode != null && valueNode.isObject()) {
            String spec = buildValueSpec(valueNode);
            if (!spec.isEmpty()) {
                rules.add(new FieldValueRule(spec));
            }
        }

        // Parse LENGTH rules
        JsonNode lengthNode = ruleSetNode.get("length");
        if (lengthNode != null && lengthNode.isObject()) {
            String spec = buildLengthSpec(lengthNode);
            if (!spec.isEmpty()) {
                rules.add(new FieldLengthRule(spec));
            }
        }

        // Parse PATTERN rules
        JsonNode patternNode = ruleSetNode.get("pattern");
        if (patternNode != null && patternNode.isObject()) {
            String spec = buildPatternSpec(patternNode);
            if (!spec.isEmpty()) {
                rules.add(new PatternMatchRule(spec));
            }
        }
    }

    /**
     * Builds REQUIRED specification: "2,3,4,11,41,42"
     */
    private String buildRequiredSpec(JsonNode arrayNode) {
        List<String> fields = new ArrayList<>();
        for (JsonNode field : arrayNode) {
            if (field.isNumber()) {
                fields.add(String.valueOf(field.asInt()));
            } else if (field.isTextual()) {
                fields.add(field.asText());
            }
        }
        return String.join(",", fields);
    }

    /**
     * Builds FORMAT specification: "2=N(13-19);3=N(6);4=N(12)"
     */
    private String buildFormatSpec(JsonNode objectNode) {
        List<String> specs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldNum = entry.getKey();
            String format = entry.getValue().asText();
            specs.add(fieldNum + "=" + format);
        }
        return String.join(";", specs);
    }

    /**
     * Builds VALUE specification: "3=010000|400000|310000;70=001|101"
     */
    private String buildValueSpec(JsonNode objectNode) {
        List<String> specs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldNum = entry.getKey();
            JsonNode valuesNode = entry.getValue();

            List<String> values = new ArrayList<>();
            if (valuesNode.isArray()) {
                for (JsonNode val : valuesNode) {
                    values.add(val.asText());
                }
            } else if (valuesNode.isTextual()) {
                // Single value
                values.add(valuesNode.asText());
            }

            if (!values.isEmpty()) {
                specs.add(fieldNum + "=" + String.join("|", values));
            }
        }
        return String.join(";", specs);
    }

    /**
     * Builds LENGTH specification: "4=12;11=6;37=12"
     */
    private String buildLengthSpec(JsonNode objectNode) {
        List<String> specs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldNum = entry.getKey();
            int length = entry.getValue().asInt();
            specs.add(fieldNum + "=" + length);
        }
        return String.join(";", specs);
    }

    /**
     * Builds PATTERN specification: "37=^[A-Z0-9]{12}$;7=^\\d{10}$"
     */
    private String buildPatternSpec(JsonNode objectNode) {
        List<String> specs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldNum = entry.getKey();
            String pattern = entry.getValue().asText();
            specs.add(fieldNum + "=" + pattern);
        }
        return String.join(";", specs);
    }
}
