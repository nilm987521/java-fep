package com.fep.message.iso8583.codec;

import com.fep.common.util.HexUtils;
import com.fep.message.exception.MessageException;
import com.fep.message.iso8583.field.DataEncoding;
import com.fep.message.iso8583.field.FieldDefinition;
import com.fep.message.iso8583.field.LengthType;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Default implementation of FieldCodec.
 * Handles encoding and decoding of ISO 8583 fields based on field definitions.
 */
@Slf4j
public class DefaultFieldCodec implements FieldCodec {

    private static final Charset ASCII = StandardCharsets.US_ASCII;

    @Override
    public void encode(FieldDefinition definition, Object value, ByteBuf buffer) {
        String strValue = value != null ? value.toString() : "";

        // Validate the value
        definition.validate(strValue);

        // Pad value if needed for fixed-length fields
        String paddedValue = padValue(definition, strValue);

        // Write length prefix for variable-length fields
        if (definition.isVariableLength()) {
            writeLengthPrefix(definition, strValue.length(), buffer);
        }

        // Write the data
        writeData(definition, paddedValue, buffer);

        log.trace("Encoded field {}: value='{}', bytes={}",
            definition.getFieldNumber(),
            definition.isSensitive() ? "****" : paddedValue,
            buffer.readableBytes());
    }

    @Override
    public Object decode(FieldDefinition definition, ByteBuf buffer) {
        // Read length prefix for variable-length fields, or use fixed length
        int dataLength;
        if (definition.isVariableLength()) {
            dataLength = readLengthPrefix(definition, buffer);
        } else {
            dataLength = definition.getLength();
        }

        // Validate data length
        if (dataLength > definition.getLength()) {
            throw MessageException.fieldError(definition.getFieldNumber(),
                "Data length " + dataLength + " exceeds max length " + definition.getLength());
        }

        // Read the data
        String value = readData(definition, dataLength, buffer);

        // Only trim padding for fixed-length non-BCD fields (e.g., ASCII alpha fields)
        // BCD numeric fields should preserve leading zeros
        if (definition.isFixedLength() &&
            definition.getDataEncoding() != DataEncoding.BCD) {
            value = trimPadding(definition, value);
        }

        log.trace("Decoded field {}: length={}, value='{}'",
            definition.getFieldNumber(), dataLength,
            definition.isSensitive() ? "****" : value);

        return value;
    }

    @Override
    public int getEncodedLength(FieldDefinition definition, Object value) {
        String strValue = value != null ? value.toString() : "";

        int prefixLength = definition.getLengthPrefixByteSize();
        int dataLength;

        if (definition.isFixedLength()) {
            dataLength = definition.calculateDataByteLength(definition.getLength());
        } else {
            dataLength = definition.calculateDataByteLength(strValue.length());
        }

        return prefixLength + dataLength;
    }

    /**
     * Pads the value to the required length for fixed-length fields.
     */
    private String padValue(FieldDefinition definition, String value) {
        if (!definition.isFixedLength()) {
            return value;
        }

        int targetLength = definition.getLength();
        if (value.length() >= targetLength) {
            return value.substring(0, targetLength);
        }

        char padChar = definition.getEffectivePaddingChar();
        if (definition.isEffectiveLeftPadding()) {
            return HexUtils.leftPad(value, targetLength, padChar);
        } else {
            return HexUtils.rightPad(value, targetLength, padChar);
        }
    }

    /**
     * Trims padding from decoded value.
     */
    private String trimPadding(FieldDefinition definition, String value) {
        char padChar = definition.getEffectivePaddingChar();
        if (definition.isEffectiveLeftPadding()) {
            // Trim leading padding
            int start = 0;
            while (start < value.length() - 1 && value.charAt(start) == padChar) {
                start++;
            }
            return value.substring(start);
        } else {
            // Trim trailing padding
            int end = value.length();
            while (end > 1 && value.charAt(end - 1) == padChar) {
                end--;
            }
            return value.substring(0, end);
        }
    }

    /**
     * Writes the length prefix for variable-length fields.
     */
    private void writeLengthPrefix(FieldDefinition definition, int dataLength, ByteBuf buffer) {
        LengthType lengthType = definition.getLengthType();
        DataEncoding lengthEncoding = definition.getLengthEncoding();
        int prefixDigits = lengthType.getPrefixLength();

        String lengthStr = HexUtils.leftPad(String.valueOf(dataLength), prefixDigits, '0');

        if (lengthEncoding == DataEncoding.BCD) {
            byte[] bcd = HexUtils.stringToBcd(lengthStr);
            buffer.writeBytes(bcd);
        } else {
            buffer.writeBytes(lengthStr.getBytes(ASCII));
        }
    }

    /**
     * Reads the length prefix for variable-length fields.
     */
    private int readLengthPrefix(FieldDefinition definition, ByteBuf buffer) {
        LengthType lengthType = definition.getLengthType();
        DataEncoding lengthEncoding = definition.getLengthEncoding();
        int prefixDigits = lengthType.getPrefixLength();
        int prefixBytes = lengthEncoding.calculateByteLength(prefixDigits);

        if (buffer.readableBytes() < prefixBytes) {
            throw MessageException.fieldError(definition.getFieldNumber(),
                "Not enough bytes for length prefix");
        }

        byte[] prefixData = new byte[prefixBytes];
        buffer.readBytes(prefixData);

        String lengthStr;
        if (lengthEncoding == DataEncoding.BCD) {
            lengthStr = HexUtils.bcdToString(prefixData);
            // Handle odd digit count in BCD
            if (lengthStr.length() > prefixDigits) {
                lengthStr = lengthStr.substring(lengthStr.length() - prefixDigits);
            }
        } else {
            lengthStr = new String(prefixData, ASCII);
        }

        try {
            return Integer.parseInt(lengthStr);
        } catch (NumberFormatException e) {
            throw MessageException.fieldError(definition.getFieldNumber(),
                "Invalid length prefix: " + lengthStr);
        }
    }

    /**
     * Writes the field data.
     */
    private void writeData(FieldDefinition definition, String value, ByteBuf buffer) {
        DataEncoding encoding = definition.getDataEncoding();

        switch (encoding) {
            case BCD -> {
                byte[] bcd = HexUtils.stringToBcd(value);
                buffer.writeBytes(bcd);
            }
            case ASCII -> buffer.writeBytes(value.getBytes(ASCII));
            case BINARY -> {
                byte[] binary = HexUtils.hexToBytes(value);
                buffer.writeBytes(binary);
            }
            case EBCDIC -> {
                // EBCDIC support - simplified for now
                buffer.writeBytes(value.getBytes(ASCII));
            }
        }
    }

    /**
     * Reads the field data.
     */
    private String readData(FieldDefinition definition, int dataLength, ByteBuf buffer) {
        DataEncoding encoding = definition.getDataEncoding();
        int byteLength = encoding.calculateByteLength(dataLength);

        if (buffer.readableBytes() < byteLength) {
            throw MessageException.fieldError(definition.getFieldNumber(),
                "Not enough bytes for data: need " + byteLength + ", have " + buffer.readableBytes());
        }

        byte[] data = new byte[byteLength];
        buffer.readBytes(data);

        return switch (encoding) {
            case BCD -> {
                String bcdStr = HexUtils.bcdToString(data);
                // Handle odd-length BCD (may have extra leading zero)
                yield bcdStr.length() > dataLength ?
                    bcdStr.substring(bcdStr.length() - dataLength) : bcdStr;
            }
            case ASCII -> new String(data, ASCII);
            case BINARY -> HexUtils.bytesToHex(data);
            case EBCDIC -> new String(data, ASCII); // Simplified
        };
    }
}
