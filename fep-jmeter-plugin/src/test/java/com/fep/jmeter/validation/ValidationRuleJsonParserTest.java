package com.fep.jmeter.validation;

import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValidationRuleJsonParser.
 */
class ValidationRuleJsonParserTest {

    private ValidationRuleJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new ValidationRuleJsonParser();
    }

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsing {

        @Test
        @DisplayName("Should parse empty JSON")
        void parsesEmptyJson() {
            ValidationRuleParser.ParsedRules rules = parser.parse("{}");
            assertNotNull(rules);
            assertTrue(rules.globalRules.isEmpty());
            assertTrue(rules.mtiRules.isEmpty());
        }

        @Test
        @DisplayName("Should parse required fields")
        void parsesRequiredFields() {
            String json = """
                {
                    "globalRules": {
                        "required": [2, 3, 4, 11, 41, 42]
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(1, rules.globalRules.size());
            assertEquals("REQUIRED", rules.globalRules.get(0).getRuleType());

            // Test validation
            Iso8583Message message = new Iso8583Message("0200");
            message.setField(2, "4111111111111111");
            message.setField(3, "010000");
            message.setField(4, "000000010000");
            message.setField(11, "000001");
            message.setField(41, "TERM0001");
            message.setField(42, "MERCHANT00001");

            var errors = rules.globalRules.get(0).validate(message);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should parse format rules")
        void parsesFormatRules() {
            String json = """
                {
                    "globalRules": {
                        "format": {
                            "2": "N(13-19)",
                            "3": "N(6)",
                            "4": "N(12)"
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(1, rules.globalRules.size());
            assertEquals("FORMAT", rules.globalRules.get(0).getRuleType());
        }

        @Test
        @DisplayName("Should parse value rules")
        void parsesValueRules() {
            String json = """
                {
                    "globalRules": {
                        "value": {
                            "3": ["010000", "400000", "310000"]
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(1, rules.globalRules.size());
            assertEquals("VALUE", rules.globalRules.get(0).getRuleType());

            // Test validation
            Iso8583Message message = new Iso8583Message("0200");
            message.setField(3, "010000");
            var errors = rules.globalRules.get(0).validate(message);
            assertTrue(errors.isEmpty());

            message.setField(3, "999999");
            errors = rules.globalRules.get(0).validate(message);
            assertFalse(errors.isEmpty());
        }

        @Test
        @DisplayName("Should parse length rules")
        void parsesLengthRules() {
            String json = """
                {
                    "globalRules": {
                        "length": {
                            "4": 12,
                            "11": 6,
                            "37": 12
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(1, rules.globalRules.size());
            assertEquals("LENGTH", rules.globalRules.get(0).getRuleType());
        }

        @Test
        @DisplayName("Should parse pattern rules")
        void parsesPatternRules() {
            String json = """
                {
                    "globalRules": {
                        "pattern": {
                            "37": "^[A-Z0-9]{12}$",
                            "7": "^\\\\d{10}$"
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(1, rules.globalRules.size());
            assertEquals("PATTERN", rules.globalRules.get(0).getRuleType());
        }

        @Test
        @DisplayName("Should parse MTI-specific rules")
        void parsesMtiSpecificRules() {
            String json = """
                {
                    "mtiRules": {
                        "0200": {
                            "required": [2, 3, 4, 11],
                            "value": {
                                "3": ["010000", "400000"]
                            }
                        },
                        "0800": {
                            "required": [70],
                            "value": {
                                "70": ["001", "101", "301"]
                            }
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertTrue(rules.globalRules.isEmpty());
            assertEquals(2, rules.mtiRules.size());
            assertTrue(rules.mtiRules.containsKey("0200"));
            assertTrue(rules.mtiRules.containsKey("0800"));

            assertEquals(2, rules.mtiRules.get("0200").size());
            assertEquals(2, rules.mtiRules.get("0800").size());
        }

        @Test
        @DisplayName("Should parse complete configuration")
        void parsesCompleteConfig() {
            String json = """
                {
                    "globalRules": {
                        "required": [2, 3, 4, 11, 41, 42],
                        "format": {
                            "2": "N(13-19)",
                            "3": "N(6)",
                            "4": "N(12)"
                        },
                        "value": {
                            "3": ["010000", "400000", "310000"]
                        },
                        "length": {
                            "39": 2
                        }
                    },
                    "mtiRules": {
                        "0800": {
                            "required": [70],
                            "value": {
                                "70": ["001", "101", "301", "161"]
                            }
                        }
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);

            assertEquals(4, rules.globalRules.size());
            assertEquals(1, rules.mtiRules.size());
        }
    }

    @Nested
    @DisplayName("Integration with MessageValidationEngine")
    class EngineIntegration {

        @Test
        @DisplayName("Should validate with JSON config")
        void validatesWithJsonConfig() {
            MessageValidationEngine engine = new MessageValidationEngine();

            String json = """
                {
                    "globalRules": {
                        "required": [2, 3, 4],
                        "value": {
                            "3": ["010000", "400000", "310000"]
                        }
                    }
                }
                """;

            engine.configure(json);

            Iso8583Message validMessage = new Iso8583Message("0200");
            validMessage.setField(2, "4111111111111111");
            validMessage.setField(3, "010000");
            validMessage.setField(4, "000000010000");

            ValidationResult result = engine.validate(validMessage);
            assertTrue(result.isValid());

            // Test invalid value
            Iso8583Message invalidMessage = new Iso8583Message("0200");
            invalidMessage.setField(2, "4111111111111111");
            invalidMessage.setField(3, "999999");  // Invalid value
            invalidMessage.setField(4, "000000010000");

            result = engine.validate(invalidMessage);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Should handle empty config")
        void handlesEmptyConfig() {
            MessageValidationEngine engine = new MessageValidationEngine();
            engine.configure("");

            Iso8583Message message = new Iso8583Message("0200");
            ValidationResult result = engine.validate(message);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should handle null config")
        void handlesNullConfig() {
            MessageValidationEngine engine = new MessageValidationEngine();
            engine.configure(null);

            Iso8583Message message = new Iso8583Message("0200");
            ValidationResult result = engine.validate(message);
            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle null config")
        void handlesNullConfig() {
            ValidationRuleParser.ParsedRules rules = parser.parse(null);
            assertNotNull(rules);
            assertTrue(rules.globalRules.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty config")
        void handlesEmptyConfig() {
            ValidationRuleParser.ParsedRules rules = parser.parse("");
            assertNotNull(rules);
            assertTrue(rules.globalRules.isEmpty());
        }

        @Test
        @DisplayName("Should handle invalid JSON gracefully")
        void handlesInvalidJson() {
            ValidationRuleParser.ParsedRules rules = parser.parse("{invalid json}");
            assertNotNull(rules);
            assertTrue(rules.globalRules.isEmpty());
        }

        @Test
        @DisplayName("Should handle missing fields gracefully")
        void handlesMissingFields() {
            String json = """
                {
                    "globalRules": {
                        "unknownRule": "value"
                    }
                }
                """;

            ValidationRuleParser.ParsedRules rules = parser.parse(json);
            assertNotNull(rules);
            assertTrue(rules.globalRules.isEmpty());
        }
    }
}
