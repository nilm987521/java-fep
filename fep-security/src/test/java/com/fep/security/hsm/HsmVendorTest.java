package com.fep.security.hsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HsmVendor enum.
 */
@DisplayName("HsmVendor Tests")
class HsmVendorTest {

    @Test
    @DisplayName("Should have all expected vendors")
    void shouldHaveAllExpectedVendors() {
        assertThat(HsmVendor.values()).containsExactlyInAnyOrder(
            HsmVendor.THALES,
            HsmVendor.UTIMACO,
            HsmVendor.SAFENET,
            HsmVendor.SOFTWARE
        );
    }

    @Test
    @DisplayName("THALES should have correct properties")
    void thalesShouldHaveCorrectProperties() {
        HsmVendor thales = HsmVendor.THALES;

        assertThat(thales.getVendorName()).isEqualTo("Thales");
        assertThat(thales.getProductLine()).isEqualTo("payShield");
    }

    @Test
    @DisplayName("UTIMACO should have correct properties")
    void utimacoShouldHaveCorrectProperties() {
        HsmVendor utimaco = HsmVendor.UTIMACO;

        assertThat(utimaco.getVendorName()).isEqualTo("Utimaco");
        assertThat(utimaco.getProductLine()).isEqualTo("CryptoServer");
    }

    @Test
    @DisplayName("SAFENET should have correct properties")
    void safenetShouldHaveCorrectProperties() {
        HsmVendor safenet = HsmVendor.SAFENET;

        assertThat(safenet.getVendorName()).isEqualTo("SafeNet");
        assertThat(safenet.getProductLine()).isEqualTo("Luna");
    }

    @Test
    @DisplayName("SOFTWARE should have correct properties")
    void softwareShouldHaveCorrectProperties() {
        HsmVendor software = HsmVendor.SOFTWARE;

        assertThat(software.getVendorName()).isEqualTo("Software");
        assertThat(software.getProductLine()).isEqualTo("SoftHSM");
    }

    @ParameterizedTest
    @EnumSource(HsmVendor.class)
    @DisplayName("All vendors should have non-empty vendor name")
    void allVendorsShouldHaveNonEmptyVendorName(HsmVendor vendor) {
        assertThat(vendor.getVendorName()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(HsmVendor.class)
    @DisplayName("All vendors should have non-empty product line")
    void allVendorsShouldHaveNonEmptyProductLine(HsmVendor vendor) {
        assertThat(vendor.getProductLine()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(HsmVendor.class)
    @DisplayName("Should parse all vendors from string")
    void shouldParseAllVendorsFromString(HsmVendor vendor) {
        assertThat(HsmVendor.valueOf(vendor.name())).isEqualTo(vendor);
    }
}
