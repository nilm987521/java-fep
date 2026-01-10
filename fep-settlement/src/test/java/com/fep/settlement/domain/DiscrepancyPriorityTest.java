package com.fep.settlement.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DiscrepancyPriority enum.
 */
@DisplayName("DiscrepancyPriority Tests")
class DiscrepancyPriorityTest {

    @Test
    @DisplayName("Should have all expected priorities")
    void shouldHaveAllExpectedPriorities() {
        assertThat(DiscrepancyPriority.values()).containsExactlyInAnyOrder(
            DiscrepancyPriority.CRITICAL,
            DiscrepancyPriority.HIGH,
            DiscrepancyPriority.MEDIUM,
            DiscrepancyPriority.LOW
        );
    }

    @Test
    @DisplayName("CRITICAL should have correct properties")
    void criticalShouldHaveCorrectProperties() {
        DiscrepancyPriority critical = DiscrepancyPriority.CRITICAL;

        assertThat(critical.getChineseName()).isEqualTo("緊急");
        assertThat(critical.getLevel()).isEqualTo(1);
        assertThat(critical.getResolutionHours()).isEqualTo(1);
    }

    @Test
    @DisplayName("HIGH should have correct properties")
    void highShouldHaveCorrectProperties() {
        DiscrepancyPriority high = DiscrepancyPriority.HIGH;

        assertThat(high.getChineseName()).isEqualTo("高");
        assertThat(high.getLevel()).isEqualTo(2);
        assertThat(high.getResolutionHours()).isEqualTo(4);
    }

    @Test
    @DisplayName("MEDIUM should have correct properties")
    void mediumShouldHaveCorrectProperties() {
        DiscrepancyPriority medium = DiscrepancyPriority.MEDIUM;

        assertThat(medium.getChineseName()).isEqualTo("中");
        assertThat(medium.getLevel()).isEqualTo(3);
        assertThat(medium.getResolutionHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("LOW should have correct properties")
    void lowShouldHaveCorrectProperties() {
        DiscrepancyPriority low = DiscrepancyPriority.LOW;

        assertThat(low.getChineseName()).isEqualTo("低");
        assertThat(low.getLevel()).isEqualTo(4);
        assertThat(low.getResolutionHours()).isEqualTo(72);
    }

    @Test
    @DisplayName("CRITICAL should be higher than HIGH")
    void criticalShouldBeHigherThanHigh() {
        assertThat(DiscrepancyPriority.CRITICAL.isHigherThan(DiscrepancyPriority.HIGH)).isTrue();
        assertThat(DiscrepancyPriority.HIGH.isHigherThan(DiscrepancyPriority.CRITICAL)).isFalse();
    }

    @Test
    @DisplayName("HIGH should be higher than MEDIUM")
    void highShouldBeHigherThanMedium() {
        assertThat(DiscrepancyPriority.HIGH.isHigherThan(DiscrepancyPriority.MEDIUM)).isTrue();
        assertThat(DiscrepancyPriority.MEDIUM.isHigherThan(DiscrepancyPriority.HIGH)).isFalse();
    }

    @Test
    @DisplayName("MEDIUM should be higher than LOW")
    void mediumShouldBeHigherThanLow() {
        assertThat(DiscrepancyPriority.MEDIUM.isHigherThan(DiscrepancyPriority.LOW)).isTrue();
        assertThat(DiscrepancyPriority.LOW.isHigherThan(DiscrepancyPriority.MEDIUM)).isFalse();
    }

    @Test
    @DisplayName("Same priority should not be higher than itself")
    void samePriorityShouldNotBeHigherThanItself() {
        assertThat(DiscrepancyPriority.CRITICAL.isHigherThan(DiscrepancyPriority.CRITICAL)).isFalse();
        assertThat(DiscrepancyPriority.HIGH.isHigherThan(DiscrepancyPriority.HIGH)).isFalse();
        assertThat(DiscrepancyPriority.MEDIUM.isHigherThan(DiscrepancyPriority.MEDIUM)).isFalse();
        assertThat(DiscrepancyPriority.LOW.isHigherThan(DiscrepancyPriority.LOW)).isFalse();
    }

    @Test
    @DisplayName("Should get priority from valid levels")
    void shouldGetPriorityFromValidLevels() {
        assertThat(DiscrepancyPriority.fromLevel(1)).isEqualTo(DiscrepancyPriority.CRITICAL);
        assertThat(DiscrepancyPriority.fromLevel(2)).isEqualTo(DiscrepancyPriority.HIGH);
        assertThat(DiscrepancyPriority.fromLevel(3)).isEqualTo(DiscrepancyPriority.MEDIUM);
        assertThat(DiscrepancyPriority.fromLevel(4)).isEqualTo(DiscrepancyPriority.LOW);
    }

    @Test
    @DisplayName("Should return MEDIUM for invalid levels")
    void shouldReturnMediumForInvalidLevels() {
        assertThat(DiscrepancyPriority.fromLevel(0)).isEqualTo(DiscrepancyPriority.MEDIUM);
        assertThat(DiscrepancyPriority.fromLevel(5)).isEqualTo(DiscrepancyPriority.MEDIUM);
        assertThat(DiscrepancyPriority.fromLevel(-1)).isEqualTo(DiscrepancyPriority.MEDIUM);
        assertThat(DiscrepancyPriority.fromLevel(100)).isEqualTo(DiscrepancyPriority.MEDIUM);
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyPriority.class)
    @DisplayName("All priorities should have non-empty Chinese name")
    void allPrioritiesShouldHaveNonEmptyChineseName(DiscrepancyPriority priority) {
        assertThat(priority.getChineseName()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyPriority.class)
    @DisplayName("All priorities should have positive level")
    void allPrioritiesShouldHavePositiveLevel(DiscrepancyPriority priority) {
        assertThat(priority.getLevel()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyPriority.class)
    @DisplayName("All priorities should have positive resolution hours")
    void allPrioritiesShouldHavePositiveResolutionHours(DiscrepancyPriority priority) {
        assertThat(priority.getResolutionHours()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(DiscrepancyPriority.class)
    @DisplayName("Should parse all priorities from string")
    void shouldParseAllPrioritiesFromString(DiscrepancyPriority priority) {
        assertThat(DiscrepancyPriority.valueOf(priority.name())).isEqualTo(priority);
    }
}
