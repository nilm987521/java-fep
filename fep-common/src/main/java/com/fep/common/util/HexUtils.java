package com.fep.common.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for hexadecimal encoding and decoding operations.
 * Used extensively for ISO 8583 message processing.
 */
@UtilityClass
public class HexUtils {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @param hex the hexadecimal string to convert
     * @return byte array
     * @throws IllegalArgumentException if the hex string is invalid
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + hex);
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Converts a byte array to BCD (Binary Coded Decimal) string.
     *
     * @param bcd the BCD encoded byte array
     * @return numeric string
     */
    public static String bcdToString(byte[] bcd) {
        if (bcd == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(bcd.length * 2);
        for (byte b : bcd) {
            sb.append((char) ('0' + ((b >> 4) & 0x0F)));
            sb.append((char) ('0' + (b & 0x0F)));
        }
        return sb.toString();
    }

    /**
     * Converts a numeric string to BCD (Binary Coded Decimal) byte array.
     *
     * @param value the numeric string to convert
     * @return BCD encoded byte array
     */
    public static byte[] stringToBcd(String value) {
        if (value == null) {
            return null;
        }
        // Pad with leading zero if odd length
        if (value.length() % 2 != 0) {
            value = "0" + value;
        }
        byte[] bcd = new byte[value.length() / 2];
        for (int i = 0; i < bcd.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 10);
            int low = Character.digit(value.charAt(i * 2 + 1), 10);
            bcd[i] = (byte) ((high << 4) | low);
        }
        return bcd;
    }

    /**
     * Converts an ASCII string to byte array.
     *
     * @param ascii the ASCII string
     * @return byte array
     */
    public static byte[] asciiToBytes(String ascii) {
        if (ascii == null) {
            return null;
        }
        return ascii.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Converts a byte array to ASCII string.
     *
     * @param bytes the byte array
     * @return ASCII string
     */
    public static String bytesToAscii(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Pads a string on the left with specified character to reach target length.
     *
     * @param value  the original string
     * @param length target length
     * @param padChar padding character
     * @return padded string
     */
    public static String leftPad(String value, int length, char padChar) {
        if (value == null) {
            value = "";
        }
        if (value.length() >= length) {
            return value;
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = value.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(value);
        return sb.toString();
    }

    /**
     * Pads a string on the right with specified character to reach target length.
     *
     * @param value  the original string
     * @param length target length
     * @param padChar padding character
     * @return padded string
     */
    public static String rightPad(String value, int length, char padChar) {
        if (value == null) {
            value = "";
        }
        if (value.length() >= length) {
            return value;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(value);
        for (int i = value.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }
}
