package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;

/**
 * Interface for encoding and decoding field values.
 * Each codec handles a specific encoding type (ASCII, BCD, EBCDIC, etc.).
 */
public interface GenericCodec {

    /**
     * Gets the codec name (e.g., "ASCII", "BCD", "PACKED_DECIMAL").
     *
     * @return the codec name
     */
    String getName();

    /**
     * Encodes a value to bytes and writes to buffer.
     *
     * @param value  the value to encode
     * @param field  the field schema
     * @param buffer the output buffer
     */
    void encode(Object value, FieldSchema field, ByteBuf buffer);

    /**
     * Decodes bytes from buffer to a value.
     *
     * @param buffer     the input buffer
     * @param field      the field schema
     * @param dataLength the data length in characters
     * @return the decoded value
     */
    Object decode(ByteBuf buffer, FieldSchema field, int dataLength);

    /**
     * Calculates the byte length for a given character length.
     *
     * @param charLength the character length
     * @return the byte length
     */
    int calculateByteLength(int charLength);

    /**
     * Encodes a length prefix value.
     *
     * @param length the length value
     * @param digits the number of digits in the prefix
     * @param buffer the output buffer
     */
    default void encodeLengthPrefix(int length, int digits, ByteBuf buffer) {
        String lengthStr = String.format("%0" + digits + "d", length);
        encode(lengthStr, null, buffer);
    }

    /**
     * Decodes a length prefix value.
     *
     * @param buffer the input buffer
     * @param digits the number of digits in the prefix
     * @return the decoded length
     */
    default int decodeLengthPrefix(ByteBuf buffer, int digits) {
        int byteLen = calculateByteLength(digits);
        byte[] data = new byte[byteLen];
        buffer.readBytes(data);
        return Integer.parseInt(new String(data).trim());
    }
}
