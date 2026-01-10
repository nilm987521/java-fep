package com.fep.jmeter.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.message.iso8583.Iso8583Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.threads.JMeterVariables;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Engine for processing MTI-based response rules.
 *
 * <p>Parses JSON configuration and applies rules to create appropriate responses
 * for different MTI types and field conditions.
 *
 * <p>JSON Configuration Format:
 * <pre>{@code
 * {
 *   "defaultResponseCode": "12",
 *   "handlers": [
 *     {
 *       "mti": "0200",
 *       "rules": [
 *         {
 *           "condition": {"field": 3, "value": "010000"},
 *           "response": {"39": "00", "38": "AUTH${STAN}"}
 *         },
 *         {
 *           "condition": "DEFAULT",
 *           "response": {"39": "00"}
 *         }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
public class MtiResponseRuleEngine {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Default response code when no rules match or MTI is not defined */
    private String defaultResponseCode = "12";

    /** Map of MTI to list of rules */
    private final Map<String, List<MtiRule>> handlers = new HashMap<>();

    /**
     * Configures the engine from a JSON string.
     *
     * @param jsonConfig the JSON configuration
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public void configure(String jsonConfig) {
        if (jsonConfig == null || jsonConfig.isBlank()) {
            log.warn("[RuleEngine] Empty configuration provided");
            return;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonConfig);

            // Parse default response code
            if (root.has("defaultResponseCode")) {
                this.defaultResponseCode = root.get("defaultResponseCode").asText("12");
            }

            // Parse handlers
            if (root.has("handlers")) {
                JsonNode handlersNode = root.get("handlers");
                if (handlersNode.isArray()) {
                    for (JsonNode handlerNode : handlersNode) {
                        parseHandler(handlerNode);
                    }
                }
            }

            log.info("[RuleEngine] Configured with {} MTI handlers, default response code: {}",
                handlers.size(), defaultResponseCode);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a single MTI handler from JSON.
     */
    private void parseHandler(JsonNode handlerNode) {
        if (!handlerNode.has("mti")) {
            log.warn("[RuleEngine] Handler missing 'mti' field, skipping");
            return;
        }

        String mti = handlerNode.get("mti").asText();
        List<MtiRule> rules = new ArrayList<>();

        if (handlerNode.has("rules") && handlerNode.get("rules").isArray()) {
            for (JsonNode ruleNode : handlerNode.get("rules")) {
                MtiRule rule = parseRule(ruleNode);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }

        if (!rules.isEmpty()) {
            handlers.put(mti, rules);
            log.debug("[RuleEngine] Registered {} rules for MTI {}", rules.size(), mti);
        }
    }

    /**
     * Parses a single rule from JSON.
     */
    private MtiRule parseRule(JsonNode ruleNode) {
        if (!ruleNode.has("condition") || !ruleNode.has("response")) {
            log.warn("[RuleEngine] Rule missing 'condition' or 'response' field, skipping");
            return null;
        }

        // Parse condition
        RuleCondition condition = parseCondition(ruleNode.get("condition"));
        if (condition == null) {
            return null;
        }

        // Parse response fields
        Map<Integer, String> responseFields = new LinkedHashMap<>();
        JsonNode responseNode = ruleNode.get("response");
        Iterator<Map.Entry<String, JsonNode>> fields = responseNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                int fieldNum = Integer.parseInt(entry.getKey());
                String fieldValue = entry.getValue().asText();
                responseFields.put(fieldNum, fieldValue);
            } catch (NumberFormatException e) {
                log.warn("[RuleEngine] Invalid field number: {}", entry.getKey());
            }
        }

        return new MtiRule(condition, responseFields);
    }

    /**
     * Parses a condition from JSON.
     */
    private RuleCondition parseCondition(JsonNode conditionNode) {
        // String "DEFAULT" condition
        if (conditionNode.isTextual()) {
            String condText = conditionNode.asText();
            if ("DEFAULT".equalsIgnoreCase(condText)) {
                return new RuleCondition.Default();
            }
            log.warn("[RuleEngine] Unknown condition type: {}", condText);
            return null;
        }

        // Object condition (field equals)
        if (conditionNode.isObject()) {
            if (conditionNode.has("field") && conditionNode.has("value")) {
                int field = conditionNode.get("field").asInt();
                String value = conditionNode.get("value").asText();
                return new RuleCondition.FieldEquals(field, value);
            }
        }

        log.warn("[RuleEngine] Invalid condition format");
        return null;
    }

    /**
     * Applies rules to create a response for the given request.
     *
     * @param request the incoming message
     * @param vars JMeter variables for substitution (may be null)
     * @return the response message
     */
    public Iso8583Message applyRules(Iso8583Message request, JMeterVariables vars) {
        String mti = request.getMti();
        List<MtiRule> rules = handlers.get(mti);

        if (rules == null) {
            log.debug("[RuleEngine] No handler for MTI {}, returning default error", mti);
            return createErrorResponse(request, defaultResponseCode);
        }

        for (MtiRule rule : rules) {
            if (rule.matches(request)) {
                log.debug("[RuleEngine] Rule matched for MTI {}: {}", mti, rule.condition());
                return rule.createResponse(request, vars);
            }
        }

        log.debug("[RuleEngine] No rule matched for MTI {}, returning default error", mti);
        return createErrorResponse(request, defaultResponseCode);
    }

    /**
     * Creates an error response with the given response code.
     */
    private Iso8583Message createErrorResponse(Iso8583Message request, String responseCode) {
        Iso8583Message response = request.createResponse();
        response.setField(39, responseCode);

        // Copy standard fields
        copyStandardFields(request, response);

        return response;
    }

    /**
     * Copies standard fields from request to response.
     */
    private void copyStandardFields(Iso8583Message request, Iso8583Message response) {
        // Copy STAN
        String stan = request.getFieldAsString(11);
        if (stan != null) {
            response.setField(11, stan);
        }

        // Set transmission date/time
        LocalDateTime now = LocalDateTime.now();
        response.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Copy RRN if present
        String rrn = request.getFieldAsString(37);
        if (rrn != null) {
            response.setField(37, rrn);
        }
    }

    /**
     * Checks if this engine has a handler for the given MTI.
     *
     * @param mti the MTI to check
     * @return true if a handler exists
     */
    public boolean hasHandler(String mti) {
        return handlers.containsKey(mti);
    }

    /**
     * Gets all supported MTI types.
     *
     * @return set of MTI strings
     */
    public Set<String> getSupportedMtis() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Gets the default response code.
     *
     * @return the default response code
     */
    public String getDefaultResponseCode() {
        return defaultResponseCode;
    }

    /**
     * Gets the number of rules for a given MTI.
     *
     * @param mti the MTI to check
     * @return the number of rules, or 0 if no handler exists
     */
    public int getRuleCount(String mti) {
        List<MtiRule> rules = handlers.get(mti);
        return rules != null ? rules.size() : 0;
    }

    /**
     * Clears all handlers.
     */
    public void clear() {
        handlers.clear();
        defaultResponseCode = "12";
        log.info("[RuleEngine] Cleared all handlers");
    }
}
