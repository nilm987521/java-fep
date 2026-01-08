package com.fep.message.iso8583.codec;

import com.fep.message.iso8583.field.FieldDefinition;
import io.netty.buffer.ByteBuf;

/**
 * Interface for encoding and decoding ISO 8583 fields.
 */
public interface FieldCodec {

    /**
     * Encodes a field value to bytes and writes to the buffer.
     *
     * @param definition the field definition
     * @param value the field value to encode
     * @param buffer the output buffer
     */
    void encode(FieldDefinition definition, Object value, ByteBuf buffer);

    /**
     * Decodes a field value from the buffer.
     *
     * @param definition the field definition
     * @param buffer the input buffer
     * @return the decoded value
     */
    Object decode(FieldDefinition definition, ByteBuf buffer);

    /**
     * Gets the byte length of the encoded field (including length prefix if applicable).
     *
     * @param definition the field definition
     * @param value the field value
     * @return the total byte length
     */
    int getEncodedLength(FieldDefinition definition, Object value);
}
