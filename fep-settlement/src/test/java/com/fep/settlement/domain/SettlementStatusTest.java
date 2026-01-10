package com.fep.settlement.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SettlementStatus enum.
 */
@DisplayName("SettlementStatus Tests")
class SettlementStatusTest {

    @Test
    @DisplayName("Should have all expected statuses")
    void shouldHaveAllExpectedStatuses() {
        assertThat(SettlementStatus.values()).containsExactlyInAnyOrder(
            SettlementStatus.PENDING,
            SettlementStatus.MATCHED,
            SettlementStatus.AMOUNT_MISMATCH,
            SettlementStatus.NOT_FOUND,
            SettlementStatus.DUPLICATE,
            SettlementStatus.MISSING,
            SettlementStatus.INVESTIGATING,
            SettlementStatus.RESOLVED,
            SettlementStatus.CONFIRMED_DISCREPANCY,
            SettlementStatus.WRITTEN_OFF,
            SettlementStatus.CLEARED
        );
    }

    @Test
    @DisplayName("PENDING should not be discrepancy, terminal, or require action")
    void pendingShouldHaveCorrectProperties() {
        SettlementStatus pending = SettlementStatus.PENDING;

        assertThat(pending.getChineseDescription()).isEqualTo("待對帳");
        assertThat(pending.isDiscrepancy()).isFalse();
        assertThat(pending.isTerminal()).isFalse();
        assertThat(pending.requiresAction()).isFalse();
    }

    @Test
    @DisplayName("MATCHED should be terminal but not discrepancy")
    void matchedShouldHaveCorrectProperties() {
        SettlementStatus matched = SettlementStatus.MATCHED;

        assertThat(matched.getChineseDescription()).isEqualTo("已配對");
        assertThat(matched.isDiscrepancy()).isFalse();
        assertThat(matched.isTerminal()).isTrue();
        assertThat(matched.requiresAction()).isFalse();
    }

    @Test
    @DisplayName("AMOUNT_MISMATCH should be discrepancy and require action")
    void amountMismatchShouldHaveCorrectProperties() {
        SettlementStatus amountMismatch = SettlementStatus.AMOUNT_MISMATCH;

        assertThat(amountMismatch.getChineseDescription()).isEqualTo("金額不符");
        assertThat(amountMismatch.isDiscrepancy()).isTrue();
        assertThat(amountMismatch.isTerminal()).isFalse();
        assertThat(amountMismatch.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("NOT_FOUND should be discrepancy and require action")
    void notFoundShouldHaveCorrectProperties() {
        SettlementStatus notFound = SettlementStatus.NOT_FOUND;

        assertThat(notFound.getChineseDescription()).isEqualTo("查無交易");
        assertThat(notFound.isDiscrepancy()).isTrue();
        assertThat(notFound.isTerminal()).isFalse();
        assertThat(notFound.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("DUPLICATE should be discrepancy and require action")
    void duplicateShouldHaveCorrectProperties() {
        SettlementStatus duplicate = SettlementStatus.DUPLICATE;

        assertThat(duplicate.getChineseDescription()).isEqualTo("重複記錄");
        assertThat(duplicate.isDiscrepancy()).isTrue();
        assertThat(duplicate.isTerminal()).isFalse();
        assertThat(duplicate.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("MISSING should be discrepancy and require action")
    void missingShouldHaveCorrectProperties() {
        SettlementStatus missing = SettlementStatus.MISSING;

        assertThat(missing.getChineseDescription()).isEqualTo("遺漏記錄");
        assertThat(missing.isDiscrepancy()).isTrue();
        assertThat(missing.isTerminal()).isFalse();
        assertThat(missing.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("INVESTIGATING should require action but not be discrepancy")
    void investigatingShouldHaveCorrectProperties() {
        SettlementStatus investigating = SettlementStatus.INVESTIGATING;

        assertThat(investigating.getChineseDescription()).isEqualTo("調查中");
        assertThat(investigating.isDiscrepancy()).isFalse();
        assertThat(investigating.isTerminal()).isFalse();
        assertThat(investigating.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("RESOLVED should be terminal")
    void resolvedShouldHaveCorrectProperties() {
        SettlementStatus resolved = SettlementStatus.RESOLVED;

        assertThat(resolved.getChineseDescription()).isEqualTo("已解決");
        assertThat(resolved.isDiscrepancy()).isFalse();
        assertThat(resolved.isTerminal()).isTrue();
        assertThat(resolved.requiresAction()).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED_DISCREPANCY should be discrepancy")
    void confirmedDiscrepancyShouldHaveCorrectProperties() {
        SettlementStatus confirmed = SettlementStatus.CONFIRMED_DISCREPANCY;

        assertThat(confirmed.getChineseDescription()).isEqualTo("確認差異");
        assertThat(confirmed.isDiscrepancy()).isTrue();
        assertThat(confirmed.isTerminal()).isFalse();
        assertThat(confirmed.requiresAction()).isTrue();
    }

    @Test
    @DisplayName("WRITTEN_OFF should be terminal")
    void writtenOffShouldHaveCorrectProperties() {
        SettlementStatus writtenOff = SettlementStatus.WRITTEN_OFF;

        assertThat(writtenOff.getChineseDescription()).isEqualTo("已沖銷");
        assertThat(writtenOff.isDiscrepancy()).isFalse();
        assertThat(writtenOff.isTerminal()).isTrue();
        assertThat(writtenOff.requiresAction()).isFalse();
    }

    @Test
    @DisplayName("CLEARED should be terminal")
    void clearedShouldHaveCorrectProperties() {
        SettlementStatus cleared = SettlementStatus.CLEARED;

        assertThat(cleared.getChineseDescription()).isEqualTo("已清算");
        assertThat(cleared.isDiscrepancy()).isFalse();
        assertThat(cleared.isTerminal()).isTrue();
        assertThat(cleared.requiresAction()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    @DisplayName("All statuses should have non-empty Chinese description")
    void allStatusesShouldHaveNonEmptyChineseDescription(SettlementStatus status) {
        assertThat(status.getChineseDescription()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    @DisplayName("Should parse all statuses from string")
    void shouldParseAllStatusesFromString(SettlementStatus status) {
        assertThat(SettlementStatus.valueOf(status.name())).isEqualTo(status);
    }
}
