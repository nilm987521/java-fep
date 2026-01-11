package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

/**
 * Packed Decimal encoding codec.
 * Each byte holds two digits, with the last nibble containing the sign.
 * Sign nibble: 0xC = positive, 0xD = negative, 0xF = unsigned
 */
public class PackedDecimalCodec implements GenericCodec {

    private static final String NAME = "PACKED_DECIMAL";
    private static final byte SIGN_POSITIVE = 0x0C;
    private static final byte SIGN_NEGATIVE = 0x0D;
    private static final byte SIGN_UNSIGNED = 0x0F;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void encode(Object value, FieldSchema field, ByteBuf buffer) {
        String strValue = value != null ? value.toString() : "0";

        // Check for negative sign
        boolean negative = strValue.startsWith("-");
        if (negative) {
            strValue = strValue.substring(1);
        }

        // Remove non-digits
        strValue = strValue.replaceAll("[^0-9]", "");

        // Apply padding for fixed-length fields (based on digits, not bytes)
        if (field != null && field.isFixedLength()) {
            int targetLength = field.getLength();
            if (strValue.length() < targetLength) {
                strValue = "0".repeat(targetLength - strValue.length()) + strValue;
            } else if (strValue.length() > targetLength) {
                strValue = strValue.substring(strValue.length() - targetLength);
            }
        }

        byte[] packed = pack(strValue, negative);
        buffer.writeBytes(packed);
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        // dataLength is the number of digits, calculate byte length
        int byteLength = (dataLength + 2) / 2; // +1 for sign nibble, then divide by 2
        byte[] data = new byte[byteLength];
        buffer.readBytes(data);

        return unpack(data, dataLength);
    }

    @Override
    public int calculateByteLength(int charLength) {
        // Packed decimal: n digits + 1 sign nibble, packed into (n+1)/2 + 1 bytes
        // More precisely: (digits + 1 + 1) / 2 = (digits + 2) / 2
        return (charLength + 2) / 2;
    }

    /**
     * Packs a numeric string into packed decimal format.
     * Format: Each byte contains 2 digits (high nibble, low nibble),
     * except the last byte which has the last digit + sign nibble.
     * Example: +12345 -> 0x01 0x23 0x4C (3 bytes)
     *
     * @param digits   the digit string
     * @param negative whether the number is negative
     * @return packed decimal bytes
     */
    private byte[] pack(String digits, boolean negative) {
        // Ensure we have at least one digit
        if (digits.isEmpty()) {
            digits = "0";
        }

        // Total nibbles = digits + 1 (for sign)
        int totalNibbles = digits.length() + 1;
        int byteLength = (totalNibbles + 1) / 2;
        byte[] packed = new byte[byteLength];

        // Pad with leading zero if we have even number of total nibbles
        // (so first byte has a valid high nibble)
        String paddedDigits = (totalNibbles % 2 == 0) ? digits : "0" + digits;

        int byteIndex = 0;
        int charIndex = 0;

        // Pack digit pairs (except the last pair which includes sign)
        while (charIndex < paddedDigits.length() - 1) {
            int high = paddedDigits.charAt(charIndex++) - '0';
            int low = paddedDigits.charAt(charIndex++) - '0';
            packed[byteIndex++] = (byte) ((high << 4) | low);
        }

        // Last byte: last digit (if any) + sign
        if (charIndex < paddedDigits.length()) {
            int high = paddedDigits.charAt(charIndex) - '0';
            int sign = negative ? SIGN_NEGATIVE : SIGN_POSITIVE;
            packed[byteIndex] = (byte) ((high << 4) | sign);
        }

        return packed;
    }

    /**
     * Unpacks packed decimal bytes to a numeric string.
     *
     * @param packed    the packed decimal bytes
     * @param numDigits the expected number of digits
     * @return the numeric string (may include '-' prefix if negative)
     */
    private String unpack(byte[] packed, int numDigits) {
        StringBuilder sb = new StringBuilder();

        // Check sign (last nibble of last byte)
        byte lastByte = packed[packed.length - 1];
        int sign = lastByte & 0x0F;
        boolean negative = (sign == SIGN_NEGATIVE);

        // Extract digits
        for (int i = 0; i < packed.length; i++) {
            byte b = packed[i];
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;

            if (i < packed.length - 1) {
                // Not the last byte: both nibbles are digits
                sb.append((char) ('0' + high));
                sb.append((char) ('0' + low));
            } else {
                // Last byte: high nibble is digit, low nibble is sign
                sb.append((char) ('0' + high));
            }
        }

        // Trim to expected length (remove leading zeros padding)
        String result = sb.toString();
        if (result.length() > numDigits) {
            result = result.substring(result.length() - numDigits);
        }

        // Add sign if negative
        if (negative) {
            result = "-" + result;
        }

        return result;
    }
}
