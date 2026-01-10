package com.fep.communication.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ChannelRole enum.
 */
@DisplayName("ChannelRole Tests")
class ChannelRoleTest {

    @Test
    @DisplayName("Should have SEND role")
    void shouldHaveSendRole() {
        assertThat(ChannelRole.SEND).isNotNull();
        assertThat(ChannelRole.SEND.name()).isEqualTo("SEND");
    }

    @Test
    @DisplayName("Should have RECEIVE role")
    void shouldHaveReceiveRole() {
        assertThat(ChannelRole.RECEIVE).isNotNull();
        assertThat(ChannelRole.RECEIVE.name()).isEqualTo("RECEIVE");
    }

    @Test
    @DisplayName("Should have exactly 2 roles")
    void shouldHaveExactlyTwoRoles() {
        assertThat(ChannelRole.values()).hasSize(2);
    }

    @Test
    @DisplayName("Should parse role from string")
    void shouldParseRoleFromString() {
        assertThat(ChannelRole.valueOf("SEND")).isEqualTo(ChannelRole.SEND);
        assertThat(ChannelRole.valueOf("RECEIVE")).isEqualTo(ChannelRole.RECEIVE);
    }

    @Test
    @DisplayName("Should throw for invalid role name")
    void shouldThrowForInvalidRoleName() {
        assertThatThrownBy(() -> ChannelRole.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
