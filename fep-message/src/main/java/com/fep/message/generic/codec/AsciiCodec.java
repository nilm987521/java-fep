package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * ASCII encoding codec.
 */
public class AsciiCodec implements GenericCodec {

    private static final String NAME = "ASCII";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void encode(Object value, FieldSchema field, ByteBuf buffer) {
        String strValue = value != null ? value.toString() : "";

        // Apply padding if needed
        if (field != null && field.isFixedLength()) {
            strValue = applyPadding(strValue, field);
        }

        buffer.writeBytes(strValue.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        int byteLength = calculateByteLength(dataLength);
        byte[] data = new byte[byteLength];
        buffer.readBytes(data);
        String value = new String(data, StandardCharsets.US_ASCII);

        // Trim padding only for fixed-length fields with EXPLICIT padding configuration
        // Don't trim based on inferred padding (e.g., NUMERIC type auto-padding)
        // to preserve values like MTI "0200" which should not become "200"
        if (field != null && field.isFixedLength() && field.hasExplicitPadding()) {
            value = trimPadding(value, field);
        }

        return value;
    }

    @Override
    public int calculateByteLength(int charLength) {
        return charLength; // 1 byte per character
    }

    private String applyPadding(String value, FieldSchema field) {
        int targetLength = field.getLength();
        if (value.length() >= targetLength) {
            return value.substring(0, targetLength);
        }

        char padChar = field.getEffectivePaddingChar();
        int padCount = targetLength - value.length();

        if (field.isLeftPadding()) {
            return String.valueOf(padChar).repeat(padCount) + value;
        } else {
            return value + String.valueOf(padChar).repeat(padCount);
        }
    }

    private String trimPadding(String value, FieldSchema field) {
        char padChar = field.getEffectivePaddingChar();

        if (field.isLeftPadding()) {
            int start = 0;
            while (start < value.length() - 1 && value.charAt(start) == padChar) {
                start++;
            }
            return value.substring(start);
        } else {
            int end = value.length();
            while (end > 1 && value.charAt(end - 1) == padChar) {
                end--;
            }
            return value.substring(0, end);
        }
    }
}
