package com.fep.jmeter.engine;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import org.apache.jmeter.threads.JMeterVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MtiResponseRuleEngine.
 */
@DisplayName("MtiResponseRuleEngine")
class MtiResponseRuleEngineTest {

    private MtiResponseRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MtiResponseRuleEngine();
    }

    @Nested
    @DisplayName("JSON Configuration Parsing")
    class JsonParsing {

        @Test
        @DisplayName("should parse valid JSON configuration")
        void shouldParseValidJsonConfig() {
            String json = """
                {
                  "defaultResponseCode": "12",
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            assertThat(engine.getDefaultResponseCode()).isEqualTo("12");
            assertThat(engine.getSupportedMtis()).containsExactly("0200");
            assertThat(engine.hasHandler("0200")).isTrue();
            assertThat(engine.getRuleCount("0200")).isEqualTo(1);
        }

        @Test
        @DisplayName("should parse multiple MTI handlers")
        void shouldParseMultipleMtiHandlers() {
            String json = """
                {
                  "handlers": [
                    {"mti": "0200", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]},
                    {"mti": "0100", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]},
                    {"mti": "0800", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]},
                    {"mti": "0400", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]}
                  ]
                }
                """;

            engine.configure(json);

            assertThat(engine.getSupportedMtis()).containsExactlyInAnyOrder("0200", "0100", "0800", "0400");
        }

        @Test
        @DisplayName("should parse multiple rules per MTI")
        void shouldParseMultipleRulesPerMti() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00"}},
                        {"condition": {"field": 3, "value": "400000"}, "response": {"39": "51"}},
                        {"condition": {"field": 3, "value": "310000"}, "response": {"39": "00"}},
                        {"condition": "DEFAULT", "response": {"39": "12"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            assertThat(engine.getRuleCount("0200")).isEqualTo(4);
        }

        @Test
        @DisplayName("should handle empty configuration")
        void shouldHandleEmptyConfig() {
            engine.configure("");

            assertThat(engine.getSupportedMtis()).isEmpty();
            assertThat(engine.getDefaultResponseCode()).isEqualTo("12");
        }

        @Test
        @DisplayName("should handle null configuration")
        void shouldHandleNullConfig() {
            engine.configure(null);

            assertThat(engine.getSupportedMtis()).isEmpty();
        }

        @Test
        @DisplayName("should throw on invalid JSON")
        void shouldThrowOnInvalidJson() {
            String invalidJson = "{ invalid json }";

            assertThatThrownBy(() -> engine.configure(invalidJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
        }

        @Test
        @DisplayName("should use default response code 12 if not specified")
        void shouldUseDefaultResponseCode() {
            String json = """
                {
                  "handlers": [
                    {"mti": "0200", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]}
                  ]
                }
                """;

            engine.configure(json);

            assertThat(engine.getDefaultResponseCode()).isEqualTo("12");
        }
    }

    @Nested
    @DisplayName("Rule Matching")
    class RuleMatching {

        @Test
        @DisplayName("should match field equals condition")
        void shouldMatchFieldEqualsCondition() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00", "38": "AUTH01"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(3, "010000");
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(39)).isEqualTo("00");
            assertThat(response.getFieldAsString(38)).isEqualTo("AUTH01");
        }

        @Test
        @DisplayName("should match DEFAULT condition when no specific condition matches")
        void shouldMatchDefaultCondition() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00"}},
                        {"condition": "DEFAULT", "response": {"39": "51"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(3, "999999"); // Not matching any specific condition
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(39)).isEqualTo("51");
        }

        @Test
        @DisplayName("should return default error for undefined MTI")
        void shouldReturnDefaultErrorForUndefinedMti() {
            String json = """
                {
                  "defaultResponseCode": "12",
                  "handlers": [
                    {"mti": "0200", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]}
                  ]
                }
                """;

            engine.configure(json);

            // Use MTI 0220 which is not defined
            Iso8583Message request = new Iso8583Message("0220");
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(39)).isEqualTo("12");
        }

        @Test
        @DisplayName("should match first matching rule")
        void shouldMatchFirstMatchingRule() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00"}},
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "99"}},
                        {"condition": "DEFAULT", "response": {"39": "51"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(3, "010000");
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, null);

            // Should use first matching rule (39=00), not second (39=99)
            assertThat(response.getFieldAsString(39)).isEqualTo("00");
        }
    }

    @Nested
    @DisplayName("Variable Substitution")
    class VariableSubstitution {

        @Test
        @DisplayName("should substitute JMeter variables")
        void shouldSubstituteJMeterVariables() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "54": "${BALANCE}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            JMeterVariables vars = new JMeterVariables();
            vars.put("BALANCE", "000000010000");

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, vars);

            assertThat(response.getFieldAsString(54)).isEqualTo("000000010000");
        }

        @Test
        @DisplayName("should substitute field reference ${Fnn}")
        void shouldSubstituteFieldReference() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "38": "AUTH${F11}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "123456");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(38)).isEqualTo("AUTH123456");
        }

        @Test
        @DisplayName("should substitute ${STAN} alias")
        void shouldSubstituteStanAlias() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "38": "TXN${STAN}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "654321");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(38)).isEqualTo("TXN654321");
        }

        @Test
        @DisplayName("should substitute ${RRN} alias")
        void shouldSubstituteRrnAlias() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "48": "REF:${RRN}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "000001");
            request.setField(37, "123456789012");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(48)).isEqualTo("REF:123456789012");
        }

        @Test
        @DisplayName("should handle undefined variable as empty string")
        void shouldHandleUndefinedVariableAsEmpty() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "48": "VALUE:${UNDEFINED}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "000001");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(48)).isEqualTo("VALUE:");
        }

        @Test
        @DisplayName("should handle multiple variables in same value")
        void shouldHandleMultipleVariables() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00", "48": "${STAN}-${RRN}"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request.setField(11, "000001");
            request.setField(37, "123456789012");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getFieldAsString(48)).isEqualTo("000001-123456789012");
        }
    }

    @Nested
    @DisplayName("Network Management (0800)")
    class NetworkManagement {

        @Test
        @DisplayName("should handle sign-on request")
        void shouldHandleSignOnRequest() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0800",
                      "rules": [
                        {"condition": {"field": 70, "value": "001"}, "response": {"39": "00", "70": "001"}},
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
            request.setField(11, "000001");
            request.setField(70, "001");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getMti()).isEqualTo("0810");
            assertThat(response.getFieldAsString(39)).isEqualTo("00");
            assertThat(response.getFieldAsString(70)).isEqualTo("001");
        }

        @Test
        @DisplayName("should handle echo test request")
        void shouldHandleEchoTestRequest() {
            String json = """
                {
                  "handlers": [
                    {
                      "mti": "0800",
                      "rules": [
                        {"condition": {"field": 70, "value": "301"}, "response": {"39": "00", "70": "301"}},
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
            request.setField(11, "000001");
            request.setField(70, "301");

            Iso8583Message response = engine.applyRules(request, null);

            assertThat(response.getMti()).isEqualTo("0810");
            assertThat(response.getFieldAsString(39)).isEqualTo("00");
        }
    }

    @Nested
    @DisplayName("Complete Scenario")
    class CompleteScenario {

        @Test
        @DisplayName("should handle multiple MTI types with different rules")
        void shouldHandleMultipleMtiTypes() {
            String json = """
                {
                  "defaultResponseCode": "12",
                  "handlers": [
                    {
                      "mti": "0200",
                      "rules": [
                        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00", "38": "AUTH01"}},
                        {"condition": {"field": 3, "value": "400000"}, "response": {"39": "51", "44": "DECLINED"}},
                        {"condition": {"field": 3, "value": "310000"}, "response": {"39": "00", "54": "000000010000"}},
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    },
                    {
                      "mti": "0100",
                      "rules": [
                        {"condition": {"field": 3, "value": "300000"}, "response": {"39": "00", "38": "PRE001"}},
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    },
                    {
                      "mti": "0800",
                      "rules": [
                        {"condition": {"field": 70, "value": "001"}, "response": {"39": "00"}},
                        {"condition": {"field": 70, "value": "002"}, "response": {"39": "00"}},
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    },
                    {
                      "mti": "0400",
                      "rules": [
                        {"condition": "DEFAULT", "response": {"39": "00"}}
                      ]
                    }
                  ]
                }
                """;

            engine.configure(json);

            // Test 0200 with F3=010000 -> F39=00
            Iso8583Message request0200 = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request0200.setField(3, "010000");
            request0200.setField(11, "000001");
            Iso8583Message response0200 = engine.applyRules(request0200, null);
            assertThat(response0200.getFieldAsString(39)).isEqualTo("00");
            assertThat(response0200.getFieldAsString(38)).isEqualTo("AUTH01");

            // Test 0200 with F3=400000 -> F39=51
            Iso8583Message request0200Decline = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            request0200Decline.setField(3, "400000");
            request0200Decline.setField(11, "000002");
            Iso8583Message response0200Decline = engine.applyRules(request0200Decline, null);
            assertThat(response0200Decline.getFieldAsString(39)).isEqualTo("51");
            assertThat(response0200Decline.getFieldAsString(44)).isEqualTo("DECLINED");

            // Test 0100 -> F39=00
            Iso8583Message request0100 = new Iso8583Message(MessageType.AUTH_REQUEST);
            request0100.setField(3, "300000");
            request0100.setField(11, "000003");
            Iso8583Message response0100 = engine.applyRules(request0100, null);
            assertThat(response0100.getFieldAsString(39)).isEqualTo("00");
            assertThat(response0100.getFieldAsString(38)).isEqualTo("PRE001");

            // Test undefined MTI -> F39=12 (default)
            Iso8583Message request0220 = new Iso8583Message("0220");
            request0220.setField(11, "000004");
            Iso8583Message response0220 = engine.applyRules(request0220, null);
            assertThat(response0220.getFieldAsString(39)).isEqualTo("12");
        }
    }

    @Nested
    @DisplayName("RuleCondition")
    class RuleConditionTest {

        @Test
        @DisplayName("FieldEquals should match when field value equals expected")
        void fieldEqualsShouldMatch() {
            RuleCondition condition = new RuleCondition.FieldEquals(3, "010000");

            Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            message.setField(3, "010000");

            assertThat(condition.matches(message)).isTrue();
        }

        @Test
        @DisplayName("FieldEquals should not match when field value differs")
        void fieldEqualsShouldNotMatch() {
            RuleCondition condition = new RuleCondition.FieldEquals(3, "010000");

            Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
            message.setField(3, "400000");

            assertThat(condition.matches(message)).isFalse();
        }

        @Test
        @DisplayName("Default should always match")
        void defaultShouldAlwaysMatch() {
            RuleCondition condition = new RuleCondition.Default();

            Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);

            assertThat(condition.matches(message)).isTrue();
        }

        @Test
        @DisplayName("FieldEquals toString should show field and value")
        void fieldEqualsToStringShouldShowDetails() {
            RuleCondition condition = new RuleCondition.FieldEquals(3, "010000");

            assertThat(condition.toString()).isEqualTo("F3=010000");
        }

        @Test
        @DisplayName("Default toString should show DEFAULT")
        void defaultToStringShouldShowDefault() {
            RuleCondition condition = new RuleCondition.Default();

            assertThat(condition.toString()).isEqualTo("DEFAULT");
        }
    }

    @Nested
    @DisplayName("Engine State Management")
    class EngineStateManagement {

        @Test
        @DisplayName("should clear all handlers")
        void shouldClearAllHandlers() {
            String json = """
                {
                  "handlers": [
                    {"mti": "0200", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]},
                    {"mti": "0800", "rules": [{"condition": "DEFAULT", "response": {"39": "00"}}]}
                  ]
                }
                """;

            engine.configure(json);
            assertThat(engine.getSupportedMtis()).hasSize(2);

            engine.clear();

            assertThat(engine.getSupportedMtis()).isEmpty();
            assertThat(engine.getDefaultResponseCode()).isEqualTo("12");
        }
    }
}
