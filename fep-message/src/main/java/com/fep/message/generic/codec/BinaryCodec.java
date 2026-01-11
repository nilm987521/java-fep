package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

/**
 * Binary encoding codec.
 * Handles raw binary data as byte arrays.
 */
public class BinaryCodec implements GenericCodec {

    private static final String NAME = "BINARY";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void encode(Object value, FieldSchema field, ByteBuf buffer) {
        byte[] bytes;

        if (value instanceof byte[] byteArray) {
            bytes = byteArray;
        } else if (value instanceof String str) {
            // Assume hex string input
            bytes = hexToBytes(str);
        } else {
            bytes = new byte[0];
        }

        // Pad or truncate for fixed length
        if (field != null && field.isFixedLength()) {
            int targetLength = field.getLength();
            if (bytes.length < targetLength) {
                byte[] padded = new byte[targetLength];
                System.arraycopy(bytes, 0, padded, targetLength - bytes.length, bytes.length);
                bytes = padded;
            } else if (bytes.length > targetLength) {
                byte[] truncated = new byte[targetLength];
                System.arraycopy(bytes, bytes.length - targetLength, truncated, 0, targetLength);
                bytes = truncated;
            }
        }

        buffer.writeBytes(bytes);
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        byte[] data = new byte[dataLength];
        buffer.readBytes(data);
        return data;
    }

    @Override
    public int calculateByteLength(int charLength) {
        return charLength; // 1:1 mapping for binary
    }

    private byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
