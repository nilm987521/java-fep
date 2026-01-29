package com.fep.communication.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChannelMdcUtil.
 */
@DisplayName("ChannelMdcUtil Tests")
class ChannelMdcUtilTest {

    @BeforeEach
    void setUp() {
        // Clear all MDC before each test
        ChannelMdcUtil.clearAll();
    }

    @AfterEach
    void tearDown() {
        // Clear all MDC after each test
        ChannelMdcUtil.clearAll();
    }

    @Nested
    @DisplayName("TraceId Tests")
    class TraceIdTests {

        @Test
        @DisplayName("should generate UUID trace ID")
        void shouldGenerateUuidTraceId() {
            // When
            String traceId = ChannelMdcUtil.generateTraceId();

            // Then
            assertThat(traceId).isNotNull();
            assertThat(traceId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should generate unique trace IDs")
        void shouldGenerateUniqueTraceIds() {
            // When
            String traceId1 = ChannelMdcUtil.generateTraceId();
            String traceId2 = ChannelMdcUtil.generateTraceId();

            // Then
            assertThat(traceId1).isNotEqualTo(traceId2);
        }

        @Test
        @DisplayName("should set trace ID in MDC")
        void shouldSetTraceIdInMdc() {
            // Given
            String traceId = "test-trace-id-123";

            // When
            ChannelMdcUtil.setTraceId(traceId);

            // Then
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isEqualTo(traceId);
        }

        @Test
        @DisplayName("should get trace ID from MDC")
        void shouldGetTraceIdFromMdc() {
            // Given
            String traceId = "test-trace-id-456";
            MDC.put(ChannelMdcUtil.TRACE_ID_KEY, traceId);

            // When
            String result = ChannelMdcUtil.getTraceId();

            // Then
            assertThat(result).isEqualTo(traceId);
        }

        @Test
        @DisplayName("should return null when trace ID not set")
        void shouldReturnNullWhenTraceIdNotSet() {
            // When
            String result = ChannelMdcUtil.getTraceId();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should clear trace ID from MDC")
        void shouldClearTraceIdFromMdc() {
            // Given
            ChannelMdcUtil.setTraceId("test-trace-id");

            // When
            ChannelMdcUtil.clearTraceId();

            // Then
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isNull();
        }

        @Test
        @DisplayName("should not set null trace ID")
        void shouldNotSetNullTraceId() {
            // Given
            ChannelMdcUtil.setTraceId("existing-trace-id");

            // When
            ChannelMdcUtil.setTraceId(null);

            // Then - should keep the existing value since null is ignored
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isEqualTo("existing-trace-id");
        }
    }

    @Nested
    @DisplayName("clearAll Tests")
    class ClearAllTests {

        @Test
        @DisplayName("should clear all MDC including trace ID")
        void shouldClearAllMdcIncludingTraceId() {
            // Given
            ChannelMdcUtil.setChannel("test-channel");
            ChannelMdcUtil.setTransactionContext("123456", "0200");
            ChannelMdcUtil.setTraceId("test-trace-id");

            // When
            ChannelMdcUtil.clearAll();

            // Then
            assertThat(MDC.get(ChannelMdcUtil.CHANNEL_NAME_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.STAN_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.MTI_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("withFullTransaction Tests")
    class WithFullTransactionTests {

        @Test
        @DisplayName("should set all MDC values including trace ID")
        void shouldSetAllMdcValues() {
            // When
            try (var scope = ChannelMdcUtil.withFullTransaction("ATM_CHANNEL", "654321", "0200", "trace-123")) {
                // Then
                assertThat(MDC.get(ChannelMdcUtil.CHANNEL_NAME_KEY)).isEqualTo("ATM_CHANNEL");
                assertThat(MDC.get(ChannelMdcUtil.STAN_KEY)).isEqualTo("654321");
                assertThat(MDC.get(ChannelMdcUtil.MTI_KEY)).isEqualTo("0200");
                assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isEqualTo("trace-123");
            }
        }

        @Test
        @DisplayName("should clear all MDC values after scope closes")
        void shouldClearAllMdcValuesAfterScopeCloses() {
            // Given
            try (var scope = ChannelMdcUtil.withFullTransaction("ATM_CHANNEL", "654321", "0200", "trace-123")) {
                // Inside scope, values are set
            }

            // Then - after scope closes, all values should be cleared
            assertThat(MDC.get(ChannelMdcUtil.CHANNEL_NAME_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.STAN_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.MTI_KEY)).isNull();
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("MdcScope Tests")
    class MdcScopeTests {

        @Test
        @DisplayName("withTransaction should not clear trace ID on close")
        void withTransactionShouldNotClearTraceIdOnClose() {
            // Given - set trace ID before creating scope
            ChannelMdcUtil.setTraceId("pre-existing-trace");

            // When - use withTransaction (without trace ID management)
            try (var scope = ChannelMdcUtil.withTransaction("ATM_CHANNEL", "654321", "0200")) {
                // Inside scope
            }

            // Then - trace ID should still exist (not cleared by withTransaction)
            assertThat(MDC.get(ChannelMdcUtil.TRACE_ID_KEY)).isEqualTo("pre-existing-trace");
        }
    }
}
