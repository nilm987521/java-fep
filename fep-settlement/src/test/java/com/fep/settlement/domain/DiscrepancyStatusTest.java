package com.fep.settlement.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DiscrepancyStatus enum.
 */
@DisplayName("DiscrepancyStatus Tests")
class DiscrepancyStatusTest {

    @Test
    @DisplayName("Should have all expected statuses")
    void shouldHaveAllExpectedStatuses() {
        assertThat(DiscrepancyStatus.values()).containsExactlyInAnyOrder(
            DiscrepancyStatus.OPEN,
            DiscrepancyStatus.INVESTIGATING,
            DiscrepancyStatus.PENDING_APPROVAL,
            DiscrepancyStatus.RESOLVED,
            DiscrepancyStatus.WRITTEN_OFF,
            DiscrepancyStatus.ESCALATED,
            DiscrepancyStatus.CLOSED
        );
    }

    @Test
    @DisplayName("OPEN should be active but not terminal")
    void openShouldHaveCorrectProperties() {
        DiscrepancyStatus open = DiscrepancyStatus.OPEN;

        assertThat(open.getChineseDescription()).isEqualTo("待處理");
        assertThat(open.isActive()).isTrue();
        assertThat(open.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("INVESTIGATING should be active but not terminal")
    void investigatingShouldHaveCorrectProperties() {
        DiscrepancyStatus investigating = DiscrepancyStatus.INVESTIGATING;

        assertThat(investigating.getChineseDescription()).isEqualTo("調查中");
        assertThat(investigating.isActive()).isTrue();
        assertThat(investigating.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("PENDING_APPROVAL should be active but not terminal")
    void pendingApprovalShouldHaveCorrectProperties() {
        DiscrepancyStatus pendingApproval = DiscrepancyStatus.PENDING_APPROVAL;

        assertThat(pendingApproval.getChineseDescription()).isEqualTo("待核准");
        assertThat(pendingApproval.isActive()).isTrue();
        assertThat(pendingApproval.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RESOLVED should be terminal but not active")
    void resolvedShouldHaveCorrectProperties() {
        DiscrepancyStatus resolved = DiscrepancyStatus.RESOLVED;

        assertThat(resolved.getChineseDescription()).isEqualTo("已解決");
        assertThat(resolved.isActive()).isFalse();
        assertThat(resolved.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("WRITTEN_OFF should be terminal but not active")
    void writtenOffShouldHaveCorrectProperties() {
        DiscrepancyStatus writtenOff = DiscrepancyStatus.WRITTEN_OFF;

        assertThat(writtenOff.getChineseDescription()).isEqualTo("已沖銷");
        assertThat(writtenOff.isActive()).isFalse();
        assertThat(writtenOff.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ESCALATED should be active but not terminal")
    void escalatedShouldHaveCorrectProperties() {
        DiscrepancyStatus escalated = DiscrepancyStatus.ESCALATED;

        assertThat(escalated.getChineseDescription()).isEqualTo("已上報");
        assertThat(escalated.isActive()).isTrue();
        assertThat(escalated.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("CLOSED should be terminal but not active")
    void closedShouldHaveCorrectProperties() {
        DiscrepancyStatus closed = DiscrepancyStatus.CLOSED;

        assertThat(closed.getChineseDescription()).isEqualTo("已結案");
        assertThat(closed.isActive()).isFalse();
        assertThat(closed.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyStatus.class)
    @DisplayName("All statuses should have non-empty Chinese description")
    void allStatusesShouldHaveNonEmptyChineseDescription(DiscrepancyStatus status) {
        assertThat(status.getChineseDescription()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyStatus.class)
    @DisplayName("Should parse all statuses from string")
    void shouldParseAllStatusesFromString(DiscrepancyStatus status) {
        assertThat(DiscrepancyStatus.valueOf(status.name())).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyStatus.class)
    @DisplayName("Active and terminal should be mutually exclusive")
    void activAndTerminalShouldBeMutuallyExclusive(DiscrepancyStatus status) {
        assertThat(status.isActive() && status.isTerminal()).isFalse();
    }
}
