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
     * Checks if the given configuration string is JSON format.
     *
     * @param config the configuration string
     * @return true if the string appears to be JSON
     */
    public static boolean isJson(String config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        String trimmed = config.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

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

    /**
     * Converts text-based rules to JSON format for migration.
     *
     * <p>This method parses the text configuration directly and converts it to JSON.
     *
     * @param textConfig the text-based configuration
     * @return JSON string representation
     */
    public static String convertToJson(String textConfig) {
        if (textConfig == null || textConfig.isEmpty()) {
            return "{}";
        }

        Map<String, Object> jsonMap = new LinkedHashMap<>();
        Map<String, Object> globalRulesMap = new LinkedHashMap<>();
        Map<String, Object> mtiRulesMap = new LinkedHashMap<>();

        // Normalize and parse line by line
        String normalized = textConfig.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }

            String ruleType = line.substring(0, colonIndex).trim().toUpperCase();
            String ruleSpec = line.substring(colonIndex + 1).trim();

            switch (ruleType) {
                case "REQUIRED" -> parseRequiredToMap(ruleSpec, globalRulesMap);
                case "FORMAT" -> parseFormatToMap(ruleSpec, globalRulesMap);
                case "VALUE" -> parseValueToMap(ruleSpec, globalRulesMap);
                case "LENGTH" -> parseLengthToMap(ruleSpec, globalRulesMap);
                case "PATTERN" -> parsePatternToMap(ruleSpec, globalRulesMap);
                case "MTI" -> parseMtiToMap(ruleSpec, mtiRulesMap);
            }
        }

        if (!globalRulesMap.isEmpty()) {
            jsonMap.put("globalRules", globalRulesMap);
        }
        if (!mtiRulesMap.isEmpty()) {
            jsonMap.put("mtiRules", mtiRulesMap);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap);
        } catch (Exception e) {
            log.error("[JsonParser] Failed to convert to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private static void parseRequiredToMap(String spec, Map<String, Object> rulesMap) {
        // Format: 2,3,4,11,41,42
        List<Integer> fields = new ArrayList<>();
        for (String field : spec.split(",")) {
            try {
                fields.add(Integer.parseInt(field.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (!fields.isEmpty()) {
            rulesMap.put("required", fields);
        }
    }

    private static void parseFormatToMap(String spec, Map<String, Object> rulesMap) {
        // Format: 2=N(13-19);3=N(6);4=N(12)
        Map<String, String> formatMap = new LinkedHashMap<>();
        for (String part : spec.split(";")) {
            int eqIndex = part.indexOf('=');
            if (eqIndex > 0) {
                String fieldNum = part.substring(0, eqIndex).trim();
                String format = part.substring(eqIndex + 1).trim();
                formatMap.put(fieldNum, format);
            }
        }
        if (!formatMap.isEmpty()) {
            rulesMap.put("format", formatMap);
        }
    }

    private static void parseValueToMap(String spec, Map<String, Object> rulesMap) {
        // Format: 3=010000|400000|310000;70=001|101
        Map<String, List<String>> valueMap = new LinkedHashMap<>();
        for (String part : spec.split(";")) {
            int eqIndex = part.indexOf('=');
            if (eqIndex > 0) {
                String fieldNum = part.substring(0, eqIndex).trim();
                String valuesStr = part.substring(eqIndex + 1).trim();
                List<String> values = Arrays.asList(valuesStr.split("\\|"));
                valueMap.put(fieldNum, values);
            }
        }
        if (!valueMap.isEmpty()) {
            rulesMap.put("value", valueMap);
        }
    }

    private static void parseLengthToMap(String spec, Map<String, Object> rulesMap) {
        // Format: 4=12;11=6;37=12
        Map<String, Integer> lengthMap = new LinkedHashMap<>();
        for (String part : spec.split(";")) {
            int eqIndex = part.indexOf('=');
            if (eqIndex > 0) {
                String fieldNum = part.substring(0, eqIndex).trim();
                try {
                    int length = Integer.parseInt(part.substring(eqIndex + 1).trim());
                    lengthMap.put(fieldNum, length);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!lengthMap.isEmpty()) {
            rulesMap.put("length", lengthMap);
        }
    }

    private static void parsePatternToMap(String spec, Map<String, Object> rulesMap) {
        // Format: 37=^[A-Z0-9]{12}$;7=^\d{10}$
        Map<String, String> patternMap = new LinkedHashMap<>();
        for (String part : spec.split(";")) {
            int eqIndex = part.indexOf('=');
            if (eqIndex > 0) {
                String fieldNum = part.substring(0, eqIndex).trim();
                String pattern = part.substring(eqIndex + 1).trim();
                patternMap.put(fieldNum, pattern);
            }
        }
        if (!patternMap.isEmpty()) {
            rulesMap.put("pattern", patternMap);
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseMtiToMap(String spec, Map<String, Object> mtiRulesMap) {
        // Format: 0200=REQUIRED:2,3,4;VALUE:3=010000|400000
        int eqIndex = spec.indexOf('=');
        if (eqIndex <= 0) {
            return;
        }

        String mti = spec.substring(0, eqIndex).trim();
        String rulesStr = spec.substring(eqIndex + 1).trim();

        Map<String, Object> mtiRuleMap = (Map<String, Object>) mtiRulesMap.computeIfAbsent(
            mti, k -> new LinkedHashMap<>());

        // Parse embedded rules
        StringBuilder currentRule = new StringBuilder();
        int parenDepth = 0;

        for (char c : rulesStr.toCharArray()) {
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;

            if (c == ';' && parenDepth == 0) {
                processEmbeddedRuleToMap(currentRule.toString().trim(), mtiRuleMap);
                currentRule = new StringBuilder();
            } else {
                currentRule.append(c);
            }
        }

        if (!currentRule.isEmpty()) {
            processEmbeddedRuleToMap(currentRule.toString().trim(), mtiRuleMap);
        }
    }

    private static void processEmbeddedRuleToMap(String ruleStr, Map<String, Object> rulesMap) {
        if (ruleStr.isEmpty()) return;

        int colonIndex = ruleStr.indexOf(':');
        if (colonIndex <= 0) return;

        String type = ruleStr.substring(0, colonIndex).trim().toUpperCase();
        String spec = ruleStr.substring(colonIndex + 1).trim();

        switch (type) {
            case "REQUIRED" -> parseRequiredToMap(spec, rulesMap);
            case "FORMAT" -> parseFormatToMap(spec, rulesMap);
            case "VALUE" -> parseValueToMap(spec, rulesMap);
            case "LENGTH" -> parseLengthToMap(spec, rulesMap);
            case "PATTERN" -> parsePatternToMap(spec, rulesMap);
        }
    }
}
