package com.fep.security.key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for KeyStatus enum.
 */
@DisplayName("KeyStatus Tests")
class KeyStatusTest {

    @Test
    @DisplayName("Should have all expected statuses")
    void shouldHaveAllExpectedStatuses() {
        assertThat(KeyStatus.values()).containsExactlyInAnyOrder(
            KeyStatus.PENDING,
            KeyStatus.ACTIVE,
            KeyStatus.SUSPENDED,
            KeyStatus.EXPIRED,
            KeyStatus.REVOKED,
            KeyStatus.ROTATING,
            KeyStatus.DESTROYED
        );
    }

    @Test
    @DisplayName("PENDING should have correct properties")
    void pendingShouldHaveCorrectProperties() {
        KeyStatus pending = KeyStatus.PENDING;

        assertThat(pending.getDescription()).isEqualTo("Pending activation");
        assertThat(pending.canEncrypt()).isFalse();
        assertThat(pending.canDecrypt()).isFalse();
    }

    @Test
    @DisplayName("ACTIVE should allow encrypt and decrypt")
    void activeShouldAllowEncryptAndDecrypt() {
        KeyStatus active = KeyStatus.ACTIVE;

        assertThat(active.getDescription()).isEqualTo("Active");
        assertThat(active.canEncrypt()).isTrue();
        assertThat(active.canDecrypt()).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED should not allow operations")
    void suspendedShouldNotAllowOperations() {
        KeyStatus suspended = KeyStatus.SUSPENDED;

        assertThat(suspended.getDescription()).isEqualTo("Suspended");
        assertThat(suspended.canEncrypt()).isFalse();
        assertThat(suspended.canDecrypt()).isFalse();
    }

    @Test
    @DisplayName("EXPIRED should allow decrypt but not encrypt")
    void expiredShouldAllowDecryptOnly() {
        KeyStatus expired = KeyStatus.EXPIRED;

        assertThat(expired.getDescription()).isEqualTo("Expired");
        assertThat(expired.canEncrypt()).isFalse();
        assertThat(expired.canDecrypt()).isTrue();
    }

    @Test
    @DisplayName("REVOKED should not allow operations")
    void revokedShouldNotAllowOperations() {
        KeyStatus revoked = KeyStatus.REVOKED;

        assertThat(revoked.getDescription()).isEqualTo("Revoked");
        assertThat(revoked.canEncrypt()).isFalse();
        assertThat(revoked.canDecrypt()).isFalse();
    }

    @Test
    @DisplayName("ROTATING should not allow operations")
    void rotatingShouldNotAllowOperations() {
        KeyStatus rotating = KeyStatus.ROTATING;

        assertThat(rotating.getDescription()).isEqualTo("Rotation in progress");
        assertThat(rotating.canEncrypt()).isFalse();
        assertThat(rotating.canDecrypt()).isFalse();
    }

    @Test
    @DisplayName("DESTROYED should not allow operations")
    void destroyedShouldNotAllowOperations() {
        KeyStatus destroyed = KeyStatus.DESTROYED;

        assertThat(destroyed.getDescription()).isEqualTo("Destroyed");
        assertThat(destroyed.canEncrypt()).isFalse();
        assertThat(destroyed.canDecrypt()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(KeyStatus.class)
    @DisplayName("All statuses should have non-empty description")
    void allStatusesShouldHaveNonEmptyDescription(KeyStatus status) {
        assertThat(status.getDescription()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(KeyStatus.class)
    @DisplayName("Should parse all statuses from string")
    void shouldParseAllStatusesFromString(KeyStatus status) {
        assertThat(KeyStatus.valueOf(status.name())).isEqualTo(status);
    }
}
