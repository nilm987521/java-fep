package com.fep.security.hsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HsmCommand enum.
 */
@DisplayName("HsmCommand Tests")
class HsmCommandTest {

    @Test
    @DisplayName("Should have all expected commands")
    void shouldHaveAllExpectedCommands() {
        assertThat(HsmCommand.values()).containsExactlyInAnyOrder(
            // Key Management Commands
            HsmCommand.GENERATE_KEY,
            HsmCommand.IMPORT_KEY,
            HsmCommand.EXPORT_KEY,
            HsmCommand.DELETE_KEY,
            HsmCommand.TRANSLATE_KEY,
            // PIN Commands
            HsmCommand.GENERATE_PIN,
            HsmCommand.VERIFY_PIN,
            HsmCommand.TRANSLATE_PIN_BLOCK,
            HsmCommand.GENERATE_PIN_OFFSET,
            HsmCommand.DERIVE_PIN_KEY,
            // MAC Commands
            HsmCommand.GENERATE_MAC,
            HsmCommand.VERIFY_MAC,
            // Encryption Commands
            HsmCommand.ENCRYPT_DATA,
            HsmCommand.DECRYPT_DATA,
            // Diagnostic Commands
            HsmCommand.ECHO_TEST,
            HsmCommand.GET_DIAGNOSTICS,
            HsmCommand.GET_STATUS
        );
    }

    @Test
    @DisplayName("Key management commands should have correct codes")
    void keyManagementCommandsShouldHaveCorrectCodes() {
        assertThat(HsmCommand.GENERATE_KEY.getCommandCode()).isEqualTo("GK");
        assertThat(HsmCommand.IMPORT_KEY.getCommandCode()).isEqualTo("IK");
        assertThat(HsmCommand.EXPORT_KEY.getCommandCode()).isEqualTo("EK");
        assertThat(HsmCommand.DELETE_KEY.getCommandCode()).isEqualTo("DK");
        assertThat(HsmCommand.TRANSLATE_KEY.getCommandCode()).isEqualTo("TK");
    }

    @Test
    @DisplayName("PIN commands should have correct codes")
    void pinCommandsShouldHaveCorrectCodes() {
        assertThat(HsmCommand.GENERATE_PIN.getCommandCode()).isEqualTo("GP");
        assertThat(HsmCommand.VERIFY_PIN.getCommandCode()).isEqualTo("VP");
        assertThat(HsmCommand.TRANSLATE_PIN_BLOCK.getCommandCode()).isEqualTo("TP");
        assertThat(HsmCommand.GENERATE_PIN_OFFSET.getCommandCode()).isEqualTo("GO");
        assertThat(HsmCommand.DERIVE_PIN_KEY.getCommandCode()).isEqualTo("DP");
    }

    @Test
    @DisplayName("MAC commands should have correct codes")
    void macCommandsShouldHaveCorrectCodes() {
        assertThat(HsmCommand.GENERATE_MAC.getCommandCode()).isEqualTo("GM");
        assertThat(HsmCommand.VERIFY_MAC.getCommandCode()).isEqualTo("VM");
    }

    @Test
    @DisplayName("Encryption commands should have correct codes")
    void encryptionCommandsShouldHaveCorrectCodes() {
        assertThat(HsmCommand.ENCRYPT_DATA.getCommandCode()).isEqualTo("ED");
        assertThat(HsmCommand.DECRYPT_DATA.getCommandCode()).isEqualTo("DD");
    }

    @Test
    @DisplayName("Diagnostic commands should have correct codes")
    void diagnosticCommandsShouldHaveCorrectCodes() {
        assertThat(HsmCommand.ECHO_TEST.getCommandCode()).isEqualTo("EC");
        assertThat(HsmCommand.GET_DIAGNOSTICS.getCommandCode()).isEqualTo("DG");
        assertThat(HsmCommand.GET_STATUS.getCommandCode()).isEqualTo("ST");
    }

    @ParameterizedTest
    @EnumSource(HsmCommand.class)
    @DisplayName("All commands should have non-empty command code")
    void allCommandsShouldHaveNonEmptyCommandCode(HsmCommand command) {
        assertThat(command.getCommandCode()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(HsmCommand.class)
    @DisplayName("All commands should have non-empty description")
    void allCommandsShouldHaveNonEmptyDescription(HsmCommand command) {
        assertThat(command.getDescription()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(HsmCommand.class)
    @DisplayName("All command codes should be 2 characters")
    void allCommandCodesShouldBeTwoCharacters(HsmCommand command) {
        assertThat(command.getCommandCode()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(HsmCommand.class)
    @DisplayName("Should parse all commands from string")
    void shouldParseAllCommandsFromString(HsmCommand command) {
        assertThat(HsmCommand.valueOf(command.name())).isEqualTo(command);
    }
}
