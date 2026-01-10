package com.fep.security.pin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PinBlockFormat enum.
 */
@DisplayName("PinBlockFormat Tests")
class PinBlockFormatTest {

    @Test
    @DisplayName("Should have all expected formats")
    void shouldHaveAllExpectedFormats() {
        assertThat(PinBlockFormat.values()).containsExactlyInAnyOrder(
            PinBlockFormat.FORMAT_0,
            PinBlockFormat.FORMAT_1,
            PinBlockFormat.FORMAT_2,
            PinBlockFormat.FORMAT_3,
            PinBlockFormat.FORMAT_4
        );
    }

    @Test
    @DisplayName("FORMAT_0 should have correct properties")
    void format0ShouldHaveCorrectProperties() {
        PinBlockFormat format0 = PinBlockFormat.FORMAT_0;

        assertThat(format0.getDescription()).isEqualTo("ISO 9564-1 Format 0");
        assertThat(format0.getFormatCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("FORMAT_1 should have correct properties")
    void format1ShouldHaveCorrectProperties() {
        PinBlockFormat format1 = PinBlockFormat.FORMAT_1;

        assertThat(format1.getDescription()).isEqualTo("ISO 9564-1 Format 1");
        assertThat(format1.getFormatCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("FORMAT_2 should have correct properties")
    void format2ShouldHaveCorrectProperties() {
        PinBlockFormat format2 = PinBlockFormat.FORMAT_2;

        assertThat(format2.getDescription()).isEqualTo("ISO 9564-1 Format 2");
        assertThat(format2.getFormatCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("FORMAT_3 should have correct properties")
    void format3ShouldHaveCorrectProperties() {
        PinBlockFormat format3 = PinBlockFormat.FORMAT_3;

        assertThat(format3.getDescription()).isEqualTo("ISO 9564-1 Format 3");
        assertThat(format3.getFormatCode()).isEqualTo(3);
    }

    @Test
    @DisplayName("FORMAT_4 should have correct properties")
    void format4ShouldHaveCorrectProperties() {
        PinBlockFormat format4 = PinBlockFormat.FORMAT_4;

        assertThat(format4.getDescription()).isEqualTo("ISO 9564-1 Format 4");
        assertThat(format4.getFormatCode()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should get format from valid codes")
    void shouldGetFormatFromValidCodes() {
        assertThat(PinBlockFormat.fromCode(0)).isEqualTo(PinBlockFormat.FORMAT_0);
        assertThat(PinBlockFormat.fromCode(1)).isEqualTo(PinBlockFormat.FORMAT_1);
        assertThat(PinBlockFormat.fromCode(2)).isEqualTo(PinBlockFormat.FORMAT_2);
        assertThat(PinBlockFormat.fromCode(3)).isEqualTo(PinBlockFormat.FORMAT_3);
        assertThat(PinBlockFormat.fromCode(4)).isEqualTo(PinBlockFormat.FORMAT_4);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5, 10, 100})
    @DisplayName("Should throw for invalid format codes")
    void shouldThrowForInvalidFormatCodes(int invalidCode) {
        assertThatThrownBy(() -> PinBlockFormat.fromCode(invalidCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown PIN block format code");
    }

    @ParameterizedTest
    @EnumSource(PinBlockFormat.class)
    @DisplayName("All formats should have non-empty description")
    void allFormatsShouldHaveNonEmptyDescription(PinBlockFormat format) {
        assertThat(format.getDescription()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(PinBlockFormat.class)
    @DisplayName("All formats should have non-negative format code")
    void allFormatsShouldHaveNonNegativeFormatCode(PinBlockFormat format) {
        assertThat(format.getFormatCode()).isGreaterThanOrEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(PinBlockFormat.class)
    @DisplayName("Should parse all formats from string")
    void shouldParseAllFormatsFromString(PinBlockFormat format) {
        assertThat(PinBlockFormat.valueOf(format.name())).isEqualTo(format);
    }
}
