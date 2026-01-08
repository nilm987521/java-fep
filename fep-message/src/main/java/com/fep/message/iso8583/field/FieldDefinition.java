package com.fep.message.iso8583.field;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Definition of an ISO 8583 field.
 *
 * <p>Each field in ISO 8583 has specific attributes:
 * <ul>
 *   <li>Field number (1-128)</li>
 *   <li>Data type (numeric, alpha, binary, etc.)</li>
 *   <li>Length type (fixed or variable with prefix)</li>
 *   <li>Maximum/fixed length</li>
 *   <li>Data encoding (ASCII, BCD, EBCDIC, Binary)</li>
 * </ul>
 */
@Getter
@Builder
@ToString
public class FieldDefinition {

    /** Field number (1-128) */
    private final int fieldNumber;

    /** Field name for documentation */
    private final String name;

    /** Field description */
    private final String description;

    /** Data type */
    private final FieldType fieldType;

    /** Length type (fixed or variable) */
    private final LengthType lengthType;

    /** Maximum length (for variable) or exact length (for fixed) */
    private final int length;

    /** Data encoding */
    @Builder.Default
    private final DataEncoding dataEncoding = DataEncoding.ASCII;

    /** Length prefix encoding (for variable length fields) */
    @Builder.Default
    private final DataEncoding lengthEncoding = DataEncoding.BCD;

    /** Whether this field is sensitive (should be masked in logs) */
    @Builder.Default
    private final boolean sensitive = false;

    /** Padding character for fixed-length fields (default: '0' for numeric, ' ' for others) */
    private final Character paddingChar;

    /** Whether padding is on the left (default: true for numeric, false for alpha) */
    @Builder.Default
    private final boolean leftPadding = true;

    /**
     * Gets the effective padding character.
     *
     * @return padding character based on field type
     */
    public char getEffectivePaddingChar() {
        if (paddingChar != null) {
            return paddingChar;
        }
        return fieldType == FieldType.NUMERIC ? '0' : ' ';
    }

    /**
     * Gets effective left padding setting.
     *
     * @return true if left padding should be used
     */
    public boolean isEffectiveLeftPadding() {
        return fieldType == FieldType.NUMERIC || leftPadding;
    }

    /**
     * Checks if this is a fixed-length field.
     *
     * @return true if fixed length
     */
    public boolean isFixedLength() {
        return lengthType == LengthType.FIXED;
    }

    /**
     * Checks if this is a variable-length field.
     *
     * @return true if variable length
     */
    public boolean isVariableLength() {
        return lengthType != LengthType.FIXED;
    }

    /**
     * Gets the byte length for the length prefix (variable fields only).
     *
     * @return prefix byte length
     */
    public int getLengthPrefixByteSize() {
        if (isFixedLength()) {
            return 0;
        }
        return lengthEncoding.calculateByteLength(lengthType.getPrefixLength());
    }

    /**
     * Calculates the byte length needed for the data portion.
     *
     * @param dataLength the data character length
     * @return byte length
     */
    public int calculateDataByteLength(int dataLength) {
        return dataEncoding.calculateByteLength(dataLength);
    }

    /**
     * Validates a value against this field definition.
     *
     * @param value the value to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Field " + fieldNumber + " value cannot be null");
        }

        int valueLength = value.length();

        if (isFixedLength()) {
            if (valueLength > length) {
                throw new IllegalArgumentException(
                    "Field " + fieldNumber + " value length " + valueLength +
                    " exceeds fixed length " + length);
            }
        } else {
            if (valueLength > length) {
                throw new IllegalArgumentException(
                    "Field " + fieldNumber + " value length " + valueLength +
                    " exceeds max length " + length);
            }
        }

        // Type-specific validation
        if (fieldType == FieldType.NUMERIC) {
            for (char c : value.toCharArray()) {
                if (!Character.isDigit(c)) {
                    throw new IllegalArgumentException(
                        "Field " + fieldNumber + " contains non-numeric character: " + c);
                }
            }
        }
    }
}
