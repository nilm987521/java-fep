package com.fep.security.hsm;

/**
 * Common HSM commands.
 */
public enum HsmCommand {

    // Key Management Commands
    GENERATE_KEY("GK", "Generate Key"),
    IMPORT_KEY("IK", "Import Key"),
    EXPORT_KEY("EK", "Export Key"),
    DELETE_KEY("DK", "Delete Key"),
    TRANSLATE_KEY("TK", "Translate Key"),

    // PIN Commands
    GENERATE_PIN("GP", "Generate PIN"),
    VERIFY_PIN("VP", "Verify PIN"),
    TRANSLATE_PIN_BLOCK("TP", "Translate PIN Block"),
    GENERATE_PIN_OFFSET("GO", "Generate PIN Offset"),
    DERIVE_PIN_KEY("DP", "Derive PIN Key"),

    // MAC Commands
    GENERATE_MAC("GM", "Generate MAC"),
    VERIFY_MAC("VM", "Verify MAC"),

    // Encryption Commands
    ENCRYPT_DATA("ED", "Encrypt Data"),
    DECRYPT_DATA("DD", "Decrypt Data"),

    // Diagnostic Commands
    ECHO_TEST("EC", "Echo Test"),
    GET_DIAGNOSTICS("DG", "Get Diagnostics"),
    GET_STATUS("ST", "Get Status");

    private final String commandCode;
    private final String description;

    HsmCommand(String commandCode, String description) {
        this.commandCode = commandCode;
        this.description = description;
    }

    public String getCommandCode() {
        return commandCode;
    }

    public String getDescription() {
        return description;
    }
}
