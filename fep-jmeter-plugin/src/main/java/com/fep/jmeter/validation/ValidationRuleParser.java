package com.fep.jmeter.validation;

import com.fep.jmeter.validation.rules.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses validation rule configurations.
 *
 * <p>Configuration format:
 * <pre>
 * # Required fields
 * REQUIRED:2,3,4,11,41,42
 *
 * # Field format (type and length)
 * FORMAT:2=N(13-19);3=N(6);4=N(12);11=N(6);41=AN(8)
 *
 * # Allowed values
 * VALUE:3=010000|400000|310000|200000
 *
 * # Exact length
 * LENGTH:4=12;11=6;37=12
 *
 * # Regex pattern
 * PATTERN:37=^[A-Z0-9]{12}$
 *
 * # MTI-specific rules
 * MTI:0200=REQUIRED:2,3,4,11;VALUE:3=010000|400000
 * MTI:0800=REQUIRED:70;VALUE:70=001|101|301
 * </pre>
 */
@Slf4j
public class ValidationRuleParser {

    /**
     * Parses validation rules from configuration string.
     *
     * @param config the configuration string (multi-line or space-separated supported)
     * @return parsed validation rules
     */
    public ParsedRules parse(String config) {
        ParsedRules result = new ParsedRules();

        if (config == null || config.isEmpty()) {
            return result;
        }

        // Normalize: replace newlines with a marker, then split by rule keywords
        // This handles both multi-line and single-line (space-separated) formats
        String normalized = config.replace("\r\n", "\n").replace("\r", "\n");

        // Split by newlines first
        String[] lines = normalized.split("\n");
        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Check if line contains multiple rules (space-separated keywords)
            // Pattern: "REQUIRED:... FORMAT:... VALUE:..."
            if (containsMultipleRules(line)) {
                // Split by rule keywords while preserving the keyword
                String[] rules = splitByRuleKeywords(line);
                for (String rule : rules) {
                    if (!rule.trim().isEmpty()) {
                        parseRuleLine(rule.trim(), result);
                    }
                }
            } else {
                parseRuleLine(line, result);
            }
        }

        return result;
    }

    /**
     * Checks if a line contains multiple rules (space-separated).
     */
    private boolean containsMultipleRules(String line) {
        int count = 0;
        for (String keyword : new String[]{"REQUIRED:", "FORMAT:", "VALUE:", "LENGTH:", "PATTERN:", "MTI:"}) {
            if (line.toUpperCase().contains(keyword)) {
                count++;
            }
        }
        return count > 1;
    }

    /**
     * Splits a line by rule keywords, preserving each keyword with its value.
     */
    private String[] splitByRuleKeywords(String line) {
        // Insert a delimiter before each keyword (except the first)
        String delimited = line;
        for (String keyword : new String[]{"REQUIRED:", "FORMAT:", "VALUE:", "LENGTH:", "PATTERN:", "MTI:"}) {
            // Case-insensitive replacement, insert delimiter before keyword
            delimited = delimited.replaceAll("(?i)\\s+(" + keyword.replace(":", "\\:") + ")", "\u0000$1");
        }
        return delimited.split("\u0000");
    }

    private void parseRuleLine(String line, ParsedRules result) {
        int colonIndex = line.indexOf(':');
        if (colonIndex <= 0) {
            log.warn("[Parser] Invalid rule line: {}", line);
            return;
        }

        String ruleType = line.substring(0, colonIndex).trim().toUpperCase();
        String ruleSpec = line.substring(colonIndex + 1).trim();

        switch (ruleType) {
            case "REQUIRED" -> result.globalRules.add(new RequiredFieldRule(ruleSpec));
            case "FORMAT" -> result.globalRules.add(new FieldFormatRule(ruleSpec));
            case "VALUE" -> result.globalRules.add(new FieldValueRule(ruleSpec));
            case "LENGTH" -> result.globalRules.add(new FieldLengthRule(ruleSpec));
            case "PATTERN" -> result.globalRules.add(new PatternMatchRule(ruleSpec));
            case "MTI" -> parseMtiSpecificRule(ruleSpec, result);
            default -> log.warn("[Parser] Unknown rule type: {}", ruleType);
        }
    }

    private void parseMtiSpecificRule(String spec, ParsedRules result) {
        // Format: 0200=REQUIRED:2,3,4,11;VALUE:3=010000|400000
        int eqIndex = spec.indexOf('=');
        if (eqIndex <= 0) {
            log.warn("[Parser] Invalid MTI rule: {}", spec);
            return;
        }

        String mti = spec.substring(0, eqIndex).trim();
        String rulesStr = spec.substring(eqIndex + 1).trim();

        List<ValidationRule> mtiRules = result.mtiRules.computeIfAbsent(mti, k -> new ArrayList<>());

        // Parse embedded rules separated by semicolons
        // Need to handle nested semicolons carefully
        StringBuilder currentRule = new StringBuilder();
        int parenDepth = 0;

        for (char c : rulesStr.toCharArray()) {
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;

            if (c == ';' && parenDepth == 0) {
                processEmbeddedRule(currentRule.toString().trim(), mtiRules);
                currentRule = new StringBuilder();
            } else {
                currentRule.append(c);
            }
        }

        // Process last rule
        if (!currentRule.isEmpty()) {
            processEmbeddedRule(currentRule.toString().trim(), mtiRules);
        }
    }

    private void processEmbeddedRule(String ruleStr, List<ValidationRule> rules) {
        if (ruleStr.isEmpty()) return;

        int colonIndex = ruleStr.indexOf(':');
        if (colonIndex <= 0) return;

        String type = ruleStr.substring(0, colonIndex).trim().toUpperCase();
        String spec = ruleStr.substring(colonIndex + 1).trim();

        switch (type) {
            case "REQUIRED" -> rules.add(new RequiredFieldRule(spec));
            case "FORMAT" -> rules.add(new FieldFormatRule(spec));
            case "VALUE" -> rules.add(new FieldValueRule(spec));
            case "LENGTH" -> rules.add(new FieldLengthRule(spec));
            case "PATTERN" -> rules.add(new PatternMatchRule(spec));
        }
    }

    /**
     * Container for parsed validation rules.
     */
    public static class ParsedRules {
        /** Global rules applied to all messages */
        public final List<ValidationRule> globalRules = new ArrayList<>();

        /** MTI-specific rules */
        public final Map<String, List<ValidationRule>> mtiRules = new HashMap<>();

        /**
         * Gets all rules applicable to a specific MTI.
         */
        public List<ValidationRule> getRulesForMti(String mti) {
            List<ValidationRule> result = new ArrayList<>(globalRules);
            List<ValidationRule> mtiSpecific = mtiRules.get(mti);
            if (mtiSpecific != null) {
                result.addAll(mtiSpecific);
            }
            return result;
        }

        /**
         * Checks if any rules are defined.
         */
        public boolean hasRules() {
            return !globalRules.isEmpty() || !mtiRules.isEmpty();
        }
    }
}
