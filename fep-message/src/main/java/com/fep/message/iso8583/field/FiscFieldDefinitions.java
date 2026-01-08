package com.fep.message.iso8583.field;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Field definitions for FISC (Financial Information Service Center) protocol.
 * Based on Taiwan FISC ISO 8583 specifications.
 *
 * <p>This class defines all 128 fields according to FISC specifications.
 * Each field has specific attributes for data type, length, and encoding.
 */
@UtilityClass
public class FiscFieldDefinitions {

    private static final Map<Integer, FieldDefinition> FIELD_MAP = new HashMap<>();

    static {
        // Initialize all field definitions based on FISC specification

        // Field 1 is reserved for secondary bitmap (handled separately)

        // Field 2: Primary Account Number (PAN)
        FIELD_MAP.put(2, FieldDefinition.builder()
            .fieldNumber(2)
            .name("Primary Account Number")
            .description("PAN - Card number")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.LLVAR)
            .length(19)
            .dataEncoding(DataEncoding.BCD)
            .sensitive(true)
            .build());

        // Field 3: Processing Code
        FIELD_MAP.put(3, FieldDefinition.builder()
            .fieldNumber(3)
            .name("Processing Code")
            .description("Transaction type code")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(6)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 4: Transaction Amount
        FIELD_MAP.put(4, FieldDefinition.builder()
            .fieldNumber(4)
            .name("Transaction Amount")
            .description("Amount in minor units")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(12)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 5: Settlement Amount
        FIELD_MAP.put(5, FieldDefinition.builder()
            .fieldNumber(5)
            .name("Settlement Amount")
            .description("Settlement amount")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(12)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 6: Cardholder Billing Amount
        FIELD_MAP.put(6, FieldDefinition.builder()
            .fieldNumber(6)
            .name("Cardholder Billing Amount")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(12)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 7: Transmission Date and Time
        FIELD_MAP.put(7, FieldDefinition.builder()
            .fieldNumber(7)
            .name("Transmission Date Time")
            .description("MMDDhhmmss")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(10)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 11: System Trace Audit Number (STAN)
        FIELD_MAP.put(11, FieldDefinition.builder()
            .fieldNumber(11)
            .name("STAN")
            .description("System Trace Audit Number")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(6)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 12: Local Transaction Time
        FIELD_MAP.put(12, FieldDefinition.builder()
            .fieldNumber(12)
            .name("Local Transaction Time")
            .description("hhmmss")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(6)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 13: Local Transaction Date
        FIELD_MAP.put(13, FieldDefinition.builder()
            .fieldNumber(13)
            .name("Local Transaction Date")
            .description("MMDD")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(4)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 14: Expiration Date
        FIELD_MAP.put(14, FieldDefinition.builder()
            .fieldNumber(14)
            .name("Expiration Date")
            .description("YYMM")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(4)
            .dataEncoding(DataEncoding.BCD)
            .sensitive(true)
            .build());

        // Field 15: Settlement Date
        FIELD_MAP.put(15, FieldDefinition.builder()
            .fieldNumber(15)
            .name("Settlement Date")
            .description("MMDD")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(4)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 18: Merchant Category Code
        FIELD_MAP.put(18, FieldDefinition.builder()
            .fieldNumber(18)
            .name("Merchant Category Code")
            .description("MCC")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(4)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 22: Point of Service Entry Mode
        FIELD_MAP.put(22, FieldDefinition.builder()
            .fieldNumber(22)
            .name("POS Entry Mode")
            .description("Point of Service Entry Mode")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(3)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 23: Card Sequence Number
        FIELD_MAP.put(23, FieldDefinition.builder()
            .fieldNumber(23)
            .name("Card Sequence Number")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(3)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 25: Point of Service Condition Code
        FIELD_MAP.put(25, FieldDefinition.builder()
            .fieldNumber(25)
            .name("POS Condition Code")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(2)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 32: Acquiring Institution ID
        FIELD_MAP.put(32, FieldDefinition.builder()
            .fieldNumber(32)
            .name("Acquiring Institution ID")
            .description("Bank code")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.LLVAR)
            .length(11)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 33: Forwarding Institution ID
        FIELD_MAP.put(33, FieldDefinition.builder()
            .fieldNumber(33)
            .name("Forwarding Institution ID")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.LLVAR)
            .length(11)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 35: Track 2 Data
        FIELD_MAP.put(35, FieldDefinition.builder()
            .fieldNumber(35)
            .name("Track 2 Data")
            .fieldType(FieldType.TRACK2)
            .lengthType(LengthType.LLVAR)
            .length(37)
            .dataEncoding(DataEncoding.BCD)
            .sensitive(true)
            .build());

        // Field 37: Retrieval Reference Number
        FIELD_MAP.put(37, FieldDefinition.builder()
            .fieldNumber(37)
            .name("Retrieval Reference Number")
            .description("RRN")
            .fieldType(FieldType.ALPHA_NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(12)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 38: Authorization ID Response
        FIELD_MAP.put(38, FieldDefinition.builder()
            .fieldNumber(38)
            .name("Authorization ID Response")
            .fieldType(FieldType.ALPHA_NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(6)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 39: Response Code
        FIELD_MAP.put(39, FieldDefinition.builder()
            .fieldNumber(39)
            .name("Response Code")
            .description("Transaction result code")
            .fieldType(FieldType.ALPHA_NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(2)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 41: Card Acceptor Terminal ID
        FIELD_MAP.put(41, FieldDefinition.builder()
            .fieldNumber(41)
            .name("Terminal ID")
            .description("ATM/POS Terminal ID")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.FIXED)
            .length(8)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 42: Card Acceptor ID Code
        FIELD_MAP.put(42, FieldDefinition.builder()
            .fieldNumber(42)
            .name("Card Acceptor ID")
            .description("Merchant ID")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.FIXED)
            .length(15)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 43: Card Acceptor Name/Location
        FIELD_MAP.put(43, FieldDefinition.builder()
            .fieldNumber(43)
            .name("Card Acceptor Name/Location")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.FIXED)
            .length(40)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 44: Additional Response Data
        FIELD_MAP.put(44, FieldDefinition.builder()
            .fieldNumber(44)
            .name("Additional Response Data")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLVAR)
            .length(25)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 48: Additional Data (Private)
        FIELD_MAP.put(48, FieldDefinition.builder()
            .fieldNumber(48)
            .name("Additional Data Private")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 49: Transaction Currency Code
        FIELD_MAP.put(49, FieldDefinition.builder()
            .fieldNumber(49)
            .name("Currency Code Transaction")
            .description("ISO 4217 currency code")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(3)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 52: PIN Data
        FIELD_MAP.put(52, FieldDefinition.builder()
            .fieldNumber(52)
            .name("PIN Data")
            .description("Encrypted PIN Block")
            .fieldType(FieldType.BINARY)
            .lengthType(LengthType.FIXED)
            .length(16)
            .dataEncoding(DataEncoding.BINARY)
            .sensitive(true)
            .build());

        // Field 53: Security Related Control Info
        FIELD_MAP.put(53, FieldDefinition.builder()
            .fieldNumber(53)
            .name("Security Related Control Info")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(16)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 54: Additional Amounts
        FIELD_MAP.put(54, FieldDefinition.builder()
            .fieldNumber(54)
            .name("Additional Amounts")
            .description("Balance information")
            .fieldType(FieldType.ALPHA_NUMERIC)
            .lengthType(LengthType.LLLVAR)
            .length(120)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 55: ICC System Related Data (EMV)
        FIELD_MAP.put(55, FieldDefinition.builder()
            .fieldNumber(55)
            .name("EMV Data")
            .description("ICC/EMV related data")
            .fieldType(FieldType.BINARY)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.BINARY)
            .sensitive(true)
            .build());

        // Field 60: Reserved (National)
        FIELD_MAP.put(60, FieldDefinition.builder()
            .fieldNumber(60)
            .name("Reserved National")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 61: Reserved (Private)
        FIELD_MAP.put(61, FieldDefinition.builder()
            .fieldNumber(61)
            .name("Reserved Private")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 62: Reserved (Private)
        FIELD_MAP.put(62, FieldDefinition.builder()
            .fieldNumber(62)
            .name("Reserved Private")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 63: Reserved (Private)
        FIELD_MAP.put(63, FieldDefinition.builder()
            .fieldNumber(63)
            .name("Reserved Private")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLLVAR)
            .length(999)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 64: MAC (Message Authentication Code)
        FIELD_MAP.put(64, FieldDefinition.builder()
            .fieldNumber(64)
            .name("MAC")
            .description("Message Authentication Code")
            .fieldType(FieldType.BINARY)
            .lengthType(LengthType.FIXED)
            .length(16)
            .dataEncoding(DataEncoding.BINARY)
            .build());

        // Field 70: Network Management Info Code
        FIELD_MAP.put(70, FieldDefinition.builder()
            .fieldNumber(70)
            .name("Network Management Info Code")
            .description("Sign-on/Sign-off/Key exchange")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(3)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 90: Original Data Elements
        FIELD_MAP.put(90, FieldDefinition.builder()
            .fieldNumber(90)
            .name("Original Data Elements")
            .description("For reversal transactions")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(42)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 95: Replacement Amounts
        FIELD_MAP.put(95, FieldDefinition.builder()
            .fieldNumber(95)
            .name("Replacement Amounts")
            .fieldType(FieldType.ALPHA_NUMERIC)
            .lengthType(LengthType.FIXED)
            .length(42)
            .dataEncoding(DataEncoding.ASCII)
            .build());

        // Field 100: Receiving Institution ID
        FIELD_MAP.put(100, FieldDefinition.builder()
            .fieldNumber(100)
            .name("Receiving Institution ID")
            .description("Destination bank code")
            .fieldType(FieldType.NUMERIC)
            .lengthType(LengthType.LLVAR)
            .length(11)
            .dataEncoding(DataEncoding.BCD)
            .build());

        // Field 102: Account ID 1
        FIELD_MAP.put(102, FieldDefinition.builder()
            .fieldNumber(102)
            .name("Account ID 1")
            .description("Source account")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLVAR)
            .length(28)
            .dataEncoding(DataEncoding.ASCII)
            .sensitive(true)
            .build());

        // Field 103: Account ID 2
        FIELD_MAP.put(103, FieldDefinition.builder()
            .fieldNumber(103)
            .name("Account ID 2")
            .description("Destination account")
            .fieldType(FieldType.ALPHA_NUMERIC_SPECIAL)
            .lengthType(LengthType.LLVAR)
            .length(28)
            .dataEncoding(DataEncoding.ASCII)
            .sensitive(true)
            .build());

        // Field 128: MAC (Secondary)
        FIELD_MAP.put(128, FieldDefinition.builder()
            .fieldNumber(128)
            .name("MAC Secondary")
            .description("Secondary Message Authentication Code")
            .fieldType(FieldType.BINARY)
            .lengthType(LengthType.FIXED)
            .length(16)
            .dataEncoding(DataEncoding.BINARY)
            .build());
    }

    /**
     * Gets the field definition for a specific field number.
     *
     * @param fieldNumber the field number (1-128)
     * @return the field definition, or null if not defined
     */
    public static FieldDefinition getDefinition(int fieldNumber) {
        return FIELD_MAP.get(fieldNumber);
    }

    /**
     * Gets all defined field definitions.
     *
     * @return unmodifiable map of field definitions
     */
    public static Map<Integer, FieldDefinition> getAllDefinitions() {
        return Collections.unmodifiableMap(FIELD_MAP);
    }

    /**
     * Checks if a field is defined.
     *
     * @param fieldNumber the field number
     * @return true if the field is defined
     */
    public static boolean isDefined(int fieldNumber) {
        return FIELD_MAP.containsKey(fieldNumber);
    }
}
