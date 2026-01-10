package com.fep.security.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CryptoAlgorithm enum.
 */
@DisplayName("CryptoAlgorithm Tests")
class CryptoAlgorithmTest {

    @Test
    @DisplayName("Should have all expected algorithms")
    void shouldHaveAllExpectedAlgorithms() {
        assertThat(CryptoAlgorithm.values()).containsExactlyInAnyOrder(
            CryptoAlgorithm.TDES,
            CryptoAlgorithm.AES_128,
            CryptoAlgorithm.AES_256,
            CryptoAlgorithm.DES
        );
    }

    @Test
    @DisplayName("TDES should have correct properties")
    void tdesShouldHaveCorrectProperties() {
        CryptoAlgorithm tdes = CryptoAlgorithm.TDES;

        assertThat(tdes.getAlgorithm()).isEqualTo("DESede");
        assertThat(tdes.getTransformation()).isEqualTo("DESede/CBC/NoPadding");
        assertThat(tdes.getKeyLength()).isEqualTo(24);
        assertThat(tdes.getBlockSize()).isEqualTo(8);
    }

    @Test
    @DisplayName("AES_128 should have correct properties")
    void aes128ShouldHaveCorrectProperties() {
        CryptoAlgorithm aes128 = CryptoAlgorithm.AES_128;

        assertThat(aes128.getAlgorithm()).isEqualTo("AES");
        assertThat(aes128.getTransformation()).isEqualTo("AES/CBC/NoPadding");
        assertThat(aes128.getKeyLength()).isEqualTo(16);
        assertThat(aes128.getBlockSize()).isEqualTo(16);
    }

    @Test
    @DisplayName("AES_256 should have correct properties")
    void aes256ShouldHaveCorrectProperties() {
        CryptoAlgorithm aes256 = CryptoAlgorithm.AES_256;

        assertThat(aes256.getAlgorithm()).isEqualTo("AES");
        assertThat(aes256.getTransformation()).isEqualTo("AES/CBC/NoPadding");
        assertThat(aes256.getKeyLength()).isEqualTo(32);
        assertThat(aes256.getBlockSize()).isEqualTo(16);
    }

    @Test
    @DisplayName("DES should have correct properties")
    void desShouldHaveCorrectProperties() {
        CryptoAlgorithm des = CryptoAlgorithm.DES;

        assertThat(des.getAlgorithm()).isEqualTo("DES");
        assertThat(des.getTransformation()).isEqualTo("DES/CBC/NoPadding");
        assertThat(des.getKeyLength()).isEqualTo(8);
        assertThat(des.getBlockSize()).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(CryptoAlgorithm.class)
    @DisplayName("All algorithms should have positive key length")
    void allAlgorithmsShouldHavePositiveKeyLength(CryptoAlgorithm algorithm) {
        assertThat(algorithm.getKeyLength()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(CryptoAlgorithm.class)
    @DisplayName("All algorithms should have positive block size")
    void allAlgorithmsShouldHavePositiveBlockSize(CryptoAlgorithm algorithm) {
        assertThat(algorithm.getBlockSize()).isPositive();
    }

    @ParameterizedTest
    @EnumSource(CryptoAlgorithm.class)
    @DisplayName("All algorithms should have non-empty algorithm name")
    void allAlgorithmsShouldHaveNonEmptyAlgorithmName(CryptoAlgorithm algorithm) {
        assertThat(algorithm.getAlgorithm()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(CryptoAlgorithm.class)
    @DisplayName("All algorithms should have non-empty transformation")
    void allAlgorithmsShouldHaveNonEmptyTransformation(CryptoAlgorithm algorithm) {
        assertThat(algorithm.getTransformation()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(CryptoAlgorithm.class)
    @DisplayName("Should parse all algorithms from string")
    void shouldParseAllAlgorithmsFromString(CryptoAlgorithm algorithm) {
        assertThat(CryptoAlgorithm.valueOf(algorithm.name())).isEqualTo(algorithm);
    }
}
