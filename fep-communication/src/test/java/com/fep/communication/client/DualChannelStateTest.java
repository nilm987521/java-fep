package com.fep.communication.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DualChannelState enum.
 */
@DisplayName("DualChannelState Tests")
class DualChannelStateTest {

    @Test
    @DisplayName("Should have all expected states")
    void shouldHaveAllExpectedStates() {
        assertThat(DualChannelState.values()).containsExactlyInAnyOrder(
            DualChannelState.DISCONNECTED,
            DualChannelState.CONNECTING,
            DualChannelState.SEND_ONLY_CONNECTED,
            DualChannelState.RECEIVE_ONLY_CONNECTED,
            DualChannelState.BOTH_CONNECTED,
            DualChannelState.SIGNED_ON,
            DualChannelState.RECONNECTING,
            DualChannelState.CLOSING,
            DualChannelState.CLOSED,
            DualChannelState.FAILED
        );
    }

    @Test
    @DisplayName("Should have exactly 10 states")
    void shouldHaveExactlyTenStates() {
        assertThat(DualChannelState.values()).hasSize(10);
    }

    @Test
    @DisplayName("DISCONNECTED should not be operational")
    void disconnectedShouldNotBeOperational() {
        assertThat(DualChannelState.DISCONNECTED.isOperational()).isFalse();
        assertThat(DualChannelState.DISCONNECTED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.DISCONNECTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("CONNECTING should not be operational")
    void connectingShouldNotBeOperational() {
        assertThat(DualChannelState.CONNECTING.isOperational()).isFalse();
        assertThat(DualChannelState.CONNECTING.isFullyOperational()).isFalse();
        assertThat(DualChannelState.CONNECTING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SEND_ONLY_CONNECTED should be operational but not fully")
    void sendOnlyConnectedShouldBePartiallyOperational() {
        assertThat(DualChannelState.SEND_ONLY_CONNECTED.isOperational()).isTrue();
        assertThat(DualChannelState.SEND_ONLY_CONNECTED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.SEND_ONLY_CONNECTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RECEIVE_ONLY_CONNECTED should be operational but not fully")
    void receiveOnlyConnectedShouldBePartiallyOperational() {
        assertThat(DualChannelState.RECEIVE_ONLY_CONNECTED.isOperational()).isTrue();
        assertThat(DualChannelState.RECEIVE_ONLY_CONNECTED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.RECEIVE_ONLY_CONNECTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("BOTH_CONNECTED should be operational but not fully")
    void bothConnectedShouldBePartiallyOperational() {
        assertThat(DualChannelState.BOTH_CONNECTED.isOperational()).isTrue();
        assertThat(DualChannelState.BOTH_CONNECTED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.BOTH_CONNECTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SIGNED_ON should be fully operational")
    void signedOnShouldBeFullyOperational() {
        assertThat(DualChannelState.SIGNED_ON.isOperational()).isTrue();
        assertThat(DualChannelState.SIGNED_ON.isFullyOperational()).isTrue();
        assertThat(DualChannelState.SIGNED_ON.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RECONNECTING should not be operational")
    void reconnectingShouldNotBeOperational() {
        assertThat(DualChannelState.RECONNECTING.isOperational()).isFalse();
        assertThat(DualChannelState.RECONNECTING.isFullyOperational()).isFalse();
        assertThat(DualChannelState.RECONNECTING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("CLOSING should not be operational")
    void closingShouldNotBeOperational() {
        assertThat(DualChannelState.CLOSING.isOperational()).isFalse();
        assertThat(DualChannelState.CLOSING.isFullyOperational()).isFalse();
        assertThat(DualChannelState.CLOSING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("CLOSED should be terminal")
    void closedShouldBeTerminal() {
        assertThat(DualChannelState.CLOSED.isOperational()).isFalse();
        assertThat(DualChannelState.CLOSED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.CLOSED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("FAILED should be terminal")
    void failedShouldBeTerminal() {
        assertThat(DualChannelState.FAILED.isOperational()).isFalse();
        assertThat(DualChannelState.FAILED.isFullyOperational()).isFalse();
        assertThat(DualChannelState.FAILED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(DualChannelState.class)
    @DisplayName("Should parse all states from string")
    void shouldParseAllStatesFromString(DualChannelState state) {
        assertThat(DualChannelState.valueOf(state.name())).isEqualTo(state);
    }
}
