package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

/**
 * BCD (Binary Coded Decimal) encoding codec.
 * Each byte holds two decimal digits.
 */
public class BcdCodec implements GenericCodec {

    private static final String NAME = "BCD";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void encode(Object value, FieldSchema field, ByteBuf buffer) {
        String strValue = value != null ? value.toString() : "";

        // Ensure only digits
        strValue = strValue.replaceAll("[^0-9]", "");

        // Apply padding for fixed-length fields
        if (field != null && field.isFixedLength()) {
            int targetLength = field.getLength();
            if (strValue.length() < targetLength) {
                strValue = "0".repeat(targetLength - strValue.length()) + strValue;
            } else if (strValue.length() > targetLength) {
                strValue = strValue.substring(strValue.length() - targetLength);
            }
        }

        // BCD requires even number of digits, pad with leading zero if needed
        if (strValue.length() % 2 != 0) {
            strValue = "0" + strValue;
        }

        byte[] bcd = stringToBcd(strValue);
        buffer.writeBytes(bcd);
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        int byteLength = calculateByteLength(dataLength);
        byte[] data = new byte[byteLength];
        buffer.readBytes(data);

        String bcdStr = bcdToString(data);

        // Handle odd-length values (may have extra leading zero)
        if (bcdStr.length() > dataLength) {
            bcdStr = bcdStr.substring(bcdStr.length() - dataLength);
        }

        return bcdStr;
    }

    @Override
    public int calculateByteLength(int charLength) {
        return (charLength + 1) / 2; // 2 digits per byte
    }

    @Override
    public void encodeLengthPrefix(int length, int digits, ByteBuf buffer) {
        String lengthStr = String.format("%0" + digits + "d", length);
        // Pad to even length
        if (lengthStr.length() % 2 != 0) {
            lengthStr = "0" + lengthStr;
        }
        byte[] bcd = stringToBcd(lengthStr);
        buffer.writeBytes(bcd);
    }

    @Override
    public int decodeLengthPrefix(ByteBuf buffer, int digits) {
        int byteLen = calculateByteLength(digits);
        byte[] data = new byte[byteLen];
        buffer.readBytes(data);
        String lengthStr = bcdToString(data);
        // Take only the required digits
        if (lengthStr.length() > digits) {
            lengthStr = lengthStr.substring(lengthStr.length() - digits);
        }
        return Integer.parseInt(lengthStr);
    }

    /**
     * Converts a numeric string to BCD bytes.
     */
    private byte[] stringToBcd(String str) {
        int len = (str.length() + 1) / 2;
        byte[] bcd = new byte[len];

        int strIndex = 0;
        // If odd length, first nibble is the first digit
        if (str.length() % 2 != 0) {
            bcd[0] = (byte) (str.charAt(0) - '0');
            strIndex = 1;
        }

        for (int i = (str.length() % 2 != 0) ? 1 : 0; i < len; i++) {
            int high = str.charAt(strIndex++) - '0';
            int low = str.charAt(strIndex++) - '0';
            bcd[i] = (byte) ((high << 4) | low);
        }

        return bcd;
    }

    /**
     * Converts BCD bytes to a numeric string.
     */
    private String bcdToString(byte[] bcd) {
        StringBuilder sb = new StringBuilder(bcd.length * 2);
        for (byte b : bcd) {
            sb.append((char) ('0' + ((b >> 4) & 0x0F)));
            sb.append((char) ('0' + (b & 0x0F)));
        }
        return sb.toString();
    }
}
