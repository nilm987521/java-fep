package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

/**
 * EBCDIC encoding codec.
 * Used for mainframe system communication.
 */
public class EbcdicCodec implements GenericCodec {

    private static final String NAME = "EBCDIC";
    private static final Charset EBCDIC_CHARSET;

    static {
        Charset charset;
        try {
            charset = Charset.forName("IBM500");
        } catch (Exception e) {
            // Fallback to CP037 if IBM500 not available
            try {
                charset = Charset.forName("CP037");
            } catch (Exception e2) {
                // Last resort: use ASCII (will cause encoding issues)
                charset = java.nio.charset.StandardCharsets.US_ASCII;
            }
        }
        EBCDIC_CHARSET = charset;
    }

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

        buffer.writeBytes(strValue.getBytes(EBCDIC_CHARSET));
    }

    @Override
    public Object decode(ByteBuf buffer, FieldSchema field, int dataLength) {
        int byteLength = calculateByteLength(dataLength);
        byte[] data = new byte[byteLength];
        buffer.readBytes(data);
        String value = new String(data, EBCDIC_CHARSET);

        // Trim padding for fixed-length fields
        if (field != null && field.isFixedLength()) {
            value = trimPadding(value, field);
        }

        return value;
    }

    @Override
    public int calculateByteLength(int charLength) {
        return charLength; // 1 byte per character in EBCDIC
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
