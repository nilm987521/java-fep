package com.fep.communication.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ChannelFailureStrategy enum.
 */
@DisplayName("ChannelFailureStrategy Tests")
class ChannelFailureStrategyTest {

    @Test
    @DisplayName("Should have FAIL_WHEN_BOTH_DOWN strategy")
    void shouldHaveFailWhenBothDownStrategy() {
        assertThat(ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN).isNotNull();
        assertThat(ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN.name()).isEqualTo("FAIL_WHEN_BOTH_DOWN");
    }

    @Test
    @DisplayName("Should have FAIL_WHEN_ANY_DOWN strategy")
    void shouldHaveFailWhenAnyDownStrategy() {
        assertThat(ChannelFailureStrategy.FAIL_WHEN_ANY_DOWN).isNotNull();
        assertThat(ChannelFailureStrategy.FAIL_WHEN_ANY_DOWN.name()).isEqualTo("FAIL_WHEN_ANY_DOWN");
    }

    @Test
    @DisplayName("Should have FALLBACK_TO_SINGLE strategy")
    void shouldHaveFallbackToSingleStrategy() {
        assertThat(ChannelFailureStrategy.FALLBACK_TO_SINGLE).isNotNull();
        assertThat(ChannelFailureStrategy.FALLBACK_TO_SINGLE.name()).isEqualTo("FALLBACK_TO_SINGLE");
    }

    @Test
    @DisplayName("Should have exactly 3 strategies")
    void shouldHaveExactlyThreeStrategies() {
        assertThat(ChannelFailureStrategy.values()).hasSize(3);
    }

    @Test
    @DisplayName("Should parse strategy from string")
    void shouldParseStrategyFromString() {
        assertThat(ChannelFailureStrategy.valueOf("FAIL_WHEN_BOTH_DOWN"))
            .isEqualTo(ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN);
        assertThat(ChannelFailureStrategy.valueOf("FAIL_WHEN_ANY_DOWN"))
            .isEqualTo(ChannelFailureStrategy.FAIL_WHEN_ANY_DOWN);
        assertThat(ChannelFailureStrategy.valueOf("FALLBACK_TO_SINGLE"))
            .isEqualTo(ChannelFailureStrategy.FALLBACK_TO_SINGLE);
    }

    @Test
    @DisplayName("Should throw for invalid strategy name")
    void shouldThrowForInvalidStrategyName() {
        assertThatThrownBy(() -> ChannelFailureStrategy.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
