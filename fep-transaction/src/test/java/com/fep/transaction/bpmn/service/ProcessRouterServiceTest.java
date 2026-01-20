package com.fep.transaction.bpmn.service;

import com.fep.transaction.bpmn.config.ProcessRoutingProperties;
import com.fep.transaction.bpmn.config.ProcessRoutingProperties.RoutingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProcessRouterService.
 */
@DisplayName("ProcessRouterService Tests")
class ProcessRouterServiceTest {

    private ProcessRoutingProperties properties;
    private ProcessRouterService routerService;

    private static final String DEFAULT_PROCESS = "Process_InterbankTransfer";
    private static final String ATM_2500_PROCESS = "Only_Log_Process";
    private static final String TRANSFER_PROCESS = "Process_InterbankTransfer";

    @BeforeEach
    void setUp() {
        properties = new ProcessRoutingProperties();
        properties.setEnabled(true);
        properties.setDefaultProcess(DEFAULT_PROCESS);
    }

    @Nested
    @DisplayName("Routing Disabled Tests")
    class RoutingDisabledTests {

        @Test
        @DisplayName("should return default process when routing is disabled")
        void shouldReturnDefaultProcessWhenDisabled() {
            // Given
            properties.setEnabled(false);
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "2500", null);

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should report disabled status correctly")
        void shouldReportDisabledStatus() {
            // Given
            properties.setEnabled(false);
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // Then
            assertThat(routerService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Simple MTI Matching Tests")
    class SimpleMtiMatchingTests {

        @Test
        @DisplayName("should match rule by MTI only")
        void shouldMatchRuleByMtiOnly() {
            // Given
            RoutingRule rule = createRule("ATM 2500", ".*", "2500", null, ATM_2500_PROCESS, 10);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "2500", null);

            // Then
            assertThat(processKey).isEqualTo(ATM_2500_PROCESS);
        }

        @Test
        @DisplayName("should return default when MTI not matched")
        void shouldReturnDefaultWhenMtiNotMatched() {
            // Given
            RoutingRule rule = createRule("ATM 2500", ".*", "2500", null, ATM_2500_PROCESS, 10);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", null);

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }
    }

    @Nested
    @DisplayName("Channel Pattern Matching Tests")
    class ChannelPatternMatchingTests {

        @Test
        @DisplayName("should match rule by channel pattern regex")
        void shouldMatchRuleByChannelPatternRegex() {
            // Given
            RoutingRule rule = createRule("ATM 2500", "ATM.*", "2500", null, ATM_2500_PROCESS, 10);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "2500", null);

            // Then
            assertThat(processKey).isEqualTo(ATM_2500_PROCESS);
        }

        @Test
        @DisplayName("should not match when channel pattern does not match")
        void shouldNotMatchWhenChannelPatternDoesNotMatch() {
            // Given
            RoutingRule rule = createRule("ATM 2500", "ATM.*", "2500", null, ATM_2500_PROCESS, 10);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("POS_FISC_V1", "2500", null);

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should match any channel with .* pattern")
        void shouldMatchAnyChannelWithWildcardPattern() {
            // Given
            RoutingRule rule = createRule("All 0200", ".*", "0200", null, TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String atmResult = routerService.resolveProcessKey("ATM_FISC_V1", "0200", null);
            String posResult = routerService.resolveProcessKey("POS_FISC_V1", "0200", null);
            String webResult = routerService.resolveProcessKey("WEB_FISC_V1", "0200", null);

            // Then
            assertThat(atmResult).isEqualTo(TRANSFER_PROCESS);
            assertThat(posResult).isEqualTo(TRANSFER_PROCESS);
            assertThat(webResult).isEqualTo(TRANSFER_PROCESS);
        }
    }

    @Nested
    @DisplayName("Processing Code Matching Tests")
    class ProcessingCodeMatchingTests {

        @Test
        @DisplayName("should match rule with processing code prefix")
        void shouldMatchRuleWithProcessingCodePrefix() {
            // Given
            RoutingRule rule = createRule("Transfer", ".*", "0200", "40", TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", "400000");

            // Then
            assertThat(processKey).isEqualTo(TRANSFER_PROCESS);
        }

        @Test
        @DisplayName("should not match when processing code prefix does not match")
        void shouldNotMatchWhenProcessingCodePrefixDoesNotMatch() {
            // Given
            RoutingRule rule = createRule("Transfer", ".*", "0200", "40", TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", "010000");

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should match when rule has no processing code requirement")
        void shouldMatchWhenRuleHasNoProcessingCodeRequirement() {
            // Given
            RoutingRule rule = createRule("All 0200", ".*", "0200", null, TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", "400000");

            // Then
            assertThat(processKey).isEqualTo(TRANSFER_PROCESS);
        }
    }

    @Nested
    @DisplayName("Priority Tests")
    class PriorityTests {

        @Test
        @DisplayName("should select rule with higher priority (lower number)")
        void shouldSelectRuleWithHigherPriority() {
            // Given
            RoutingRule lowPriorityRule = createRule("Generic 0200", ".*", "0200", null, "LowPriorityProcess", 100);
            RoutingRule highPriorityRule = createRule("ATM 0200", "ATM.*", "0200", null, "HighPriorityProcess", 10);
            properties.setRules(Arrays.asList(lowPriorityRule, highPriorityRule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", null);

            // Then
            assertThat(processKey).isEqualTo("HighPriorityProcess");
        }

        @Test
        @DisplayName("should fallback to lower priority rule when high priority does not match")
        void shouldFallbackToLowerPriorityRule() {
            // Given
            RoutingRule lowPriorityRule = createRule("Generic 0200", ".*", "0200", null, "LowPriorityProcess", 100);
            RoutingRule highPriorityRule = createRule("ATM 0200", "ATM.*", "0200", null, "HighPriorityProcess", 10);
            properties.setRules(Arrays.asList(lowPriorityRule, highPriorityRule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("POS_FISC_V1", "0200", null);

            // Then
            assertThat(processKey).isEqualTo("LowPriorityProcess");
        }
    }

    @Nested
    @DisplayName("Complex Routing Scenarios")
    class ComplexRoutingScenariosTests {

        @Test
        @DisplayName("should handle multiple rules with different conditions")
        void shouldHandleMultipleRulesWithDifferentConditions() {
            // Given
            RoutingRule atm2500Rule = createRule("ATM 2500", "ATM.*", "2500", null, ATM_2500_PROCESS, 10);
            RoutingRule transferRule = createRule("Transfer", ".*", "0200", "40", TRANSFER_PROCESS, 100);
            RoutingRule withdrawalRule = createRule("Withdrawal", ".*", "0200", "01", "WithdrawalProcess", 100);
            properties.setRules(Arrays.asList(atm2500Rule, transferRule, withdrawalRule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When & Then
            assertThat(routerService.resolveProcessKey("ATM_FISC_V1", "2500", null))
                    .isEqualTo(ATM_2500_PROCESS);
            assertThat(routerService.resolveProcessKey("ATM_FISC_V1", "0200", "400000"))
                    .isEqualTo(TRANSFER_PROCESS);
            assertThat(routerService.resolveProcessKey("ATM_FISC_V1", "0200", "010000"))
                    .isEqualTo("WithdrawalProcess");
            assertThat(routerService.resolveProcessKey("ATM_FISC_V1", "0200", "310000"))
                    .isEqualTo(DEFAULT_PROCESS);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null channelId")
        void shouldHandleNullChannelId() {
            // Given
            RoutingRule rule = createRule("All 0200", ".*", "0200", null, TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey(null, "0200", null);

            // Then - should return default as null doesn't match ".*"
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should handle null mti")
        void shouldHandleNullMti() {
            // Given
            RoutingRule rule = createRule("All 0200", ".*", "0200", null, TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", null, null);

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should handle empty rules list")
        void shouldHandleEmptyRulesList() {
            // Given
            properties.setRules(Collections.emptyList());
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", null);

            // Then
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should handle invalid regex pattern gracefully")
        void shouldHandleInvalidRegexPatternGracefully() {
            // Given
            RoutingRule rule = createRule("Invalid Regex", "[invalid", "0200", null, TRANSFER_PROCESS, 100);
            properties.setRules(Collections.singletonList(rule));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // When
            String processKey = routerService.resolveProcessKey("ATM_FISC_V1", "0200", null);

            // Then - should fallback to default since regex is invalid
            assertThat(processKey).isEqualTo(DEFAULT_PROCESS);
        }

        @Test
        @DisplayName("should return correct rule count")
        void shouldReturnCorrectRuleCount() {
            // Given
            RoutingRule rule1 = createRule("Rule 1", ".*", "0200", null, TRANSFER_PROCESS, 100);
            RoutingRule rule2 = createRule("Rule 2", ".*", "0400", null, "ReversalProcess", 100);
            properties.setRules(Arrays.asList(rule1, rule2));
            routerService = new ProcessRouterService(properties);
            routerService.init();

            // Then
            assertThat(routerService.getRuleCount()).isEqualTo(2);
        }
    }

    // Helper method to create routing rules
    private RoutingRule createRule(String name, String channelPattern, String mti,
                                   String processingCode, String processKey, int priority) {
        RoutingRule rule = new RoutingRule();
        rule.setName(name);
        rule.setChannelPattern(channelPattern);
        rule.setMti(mti);
        rule.setProcessingCode(processingCode);
        rule.setProcessKey(processKey);
        rule.setPriority(priority);
        return rule;
    }
}
