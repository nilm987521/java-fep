package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

/**
 * HEX encoding codec.
 * Converts hex string to/from binary bytes.
 */
public class HexCodec implements GenericCodec {

    private static final String NAME = "HEX";
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void encode(Object value, FieldSchema field, ByteBuf buffer) {
        String hexStr = value != null ? value.toString().toUpperCase() : "";

        // Remove any non-hex characters
        hexStr = hexStr.replaceAll("[^0-9A-Fa-f]", "");

        // Pad with leading zeros if needed for fixed length (in bytes)
        if (field != null && field.isFixedLength()) {
            int targetHexLen = field.getLength() * 2; // 2 hex chars per byte
            if (hexStr.length() < targetHexLen) {
                hexStr = "0".repeat(targetHexLen - hexStr.length()) + hexStr;
            } else if (hexStr.length() > targetHexLen) {
                hexStr = hexStr.substring(hexStr.length() - targetHexLen);
            }
        }

        // Ensure even number of hex characters
        if (hexStr.length() % 2 != 0) {
            hexStr = "0" + hexStr;
        }

        byte[] bytes = hexToBytes(hexStr);
        buffer.writeBytes(bytes);
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        // For HEX, dataLength is in bytes
        byte[] data = new byte[dataLength];
        buffer.readBytes(data);
        return bytesToHex(data);
    }

    @Override
    public int calculateByteLength(int charLength) {
        // charLength here represents byte length for HEX type
        return charLength;
    }

    /**
     * Converts a hex string to bytes.
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    /**
     * Converts bytes to a hex string.
     */
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}
