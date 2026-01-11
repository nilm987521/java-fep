package com.fep.message.generic.parser;

import com.fep.message.exception.MessageException;
import com.fep.message.generic.codec.CodecRegistry;
import com.fep.message.generic.codec.GenericCodec;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.schema.FieldSchema;
import com.fep.message.generic.schema.MessageSchema;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses byte arrays into GenericMessage objects according to the schema.
 */
@Slf4j
public class GenericMessageParser {

    /**
     * Parses bytes into a message.
     *
     * @param data   the raw bytes
     * @param schema the message schema
     * @return the parsed message
     */
    public GenericMessage parse(byte[] data, MessageSchema schema) {
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        try {
            return parse(buffer, schema);
        } finally {
            buffer.release();
        }
    }

    /**
     * Parses a ByteBuf into a message.
     *
     * @param buffer the input buffer
     * @param schema the message schema
     * @return the parsed message
     */
    public GenericMessage parse(ByteBuf buffer, MessageSchema schema) {
        GenericMessage message = new GenericMessage(schema);

        // Skip/parse header length field if present
        if (schema.getHeader() != null && schema.getHeader().isIncludeLength()) {
            int lengthBytes = schema.getHeader().getLengthBytes();
            String lengthEncoding = schema.getHeader().getLengthEncoding();

            int messageLength = parseLengthField(buffer, lengthBytes, lengthEncoding);
            log.debug("Parsed message length: {}", messageLength);
        }

        // Parse header fields
        if (schema.getHeader() != null && schema.getHeader().getFields() != null) {
            for (FieldSchema fieldSchema : schema.getHeader().getFields()) {
                if (buffer.readableBytes() <= 0) break;
                parseField(fieldSchema, buffer, message, schema);
            }
        }

        // Parse body fields
        for (FieldSchema fieldSchema : schema.getFields()) {
            if (buffer.readableBytes() <= 0) break;
            parseField(fieldSchema, buffer, message, schema);
        }

        // Parse trailer fields
        if (schema.getTrailer() != null && schema.getTrailer().getFields() != null) {
            for (FieldSchema fieldSchema : schema.getTrailer().getFields()) {
                if (buffer.readableBytes() <= 0) break;
                parseField(fieldSchema, buffer, message, schema);
            }
        }

        // Store raw data
        message.setRawData(buffer.array());

        log.debug("Parsed message [{}]: {} fields", schema.getName(), message.getSetFieldIds().size());
        return message;
    }

    /**
     * Parses a single field from the buffer.
     */
    private void parseField(FieldSchema fieldSchema, ByteBuf buffer, GenericMessage message, MessageSchema schema) {
        if (fieldSchema.isComposite()) {
            parseCompositeField(fieldSchema, buffer, message, schema);
            return;
        }

        String encoding = fieldSchema.getEncoding();
        if (encoding == null) {
            encoding = schema.getDefaultEncoding();
        }

        GenericCodec codec = CodecRegistry.get(encoding);

        // Determine data length
        int dataLength;
        if (fieldSchema.isVariableLength()) {
            int prefixDigits = fieldSchema.getLengthPrefixDigits();
            String lengthEncoding = fieldSchema.getLengthEncoding();
            GenericCodec lengthCodec = CodecRegistry.get(lengthEncoding);
            dataLength = lengthCodec.decodeLengthPrefix(buffer, prefixDigits);

            if (dataLength > fieldSchema.getLength()) {
                throw MessageException.fieldError(fieldSchema.getId(),
                        "Data length " + dataLength + " exceeds max length " + fieldSchema.getLength());
            }
        } else {
            dataLength = fieldSchema.getLength();
        }

        // Check buffer has enough data
        int byteLength = codec.calculateByteLength(dataLength);
        if (buffer.readableBytes() < byteLength) {
            if (fieldSchema.isRequired()) {
                throw MessageException.fieldError(fieldSchema.getId(),
                        "Not enough bytes for field: need " + byteLength + ", have " + buffer.readableBytes());
            }
            return;
        }

        // Decode the field
        Object value = codec.decode(buffer, fieldSchema, dataLength);
        message.setField(fieldSchema.getId(), value);

        log.trace("Parsed field [{}]: length={}, value={}",
                fieldSchema.getId(), dataLength,
                fieldSchema.isSensitive() ? "****" : value);
    }

    /**
     * Parses a composite field with nested children.
     */
    private void parseCompositeField(FieldSchema fieldSchema, ByteBuf buffer, GenericMessage message, MessageSchema schema) {
        Map<String, Object> nestedValues = new LinkedHashMap<>();

        for (FieldSchema childSchema : fieldSchema.getFields()) {
            if (buffer.readableBytes() <= 0) break;

            String encoding = childSchema.getEncoding();
            if (encoding == null) {
                encoding = schema.getDefaultEncoding();
            }

            GenericCodec codec = CodecRegistry.get(encoding);

            // Determine data length
            int dataLength;
            if (childSchema.isVariableLength()) {
                int prefixDigits = childSchema.getLengthPrefixDigits();
                String lengthEncoding = childSchema.getLengthEncoding();
                GenericCodec lengthCodec = CodecRegistry.get(lengthEncoding);
                dataLength = lengthCodec.decodeLengthPrefix(buffer, prefixDigits);
            } else {
                dataLength = childSchema.getLength();
            }

            // Check buffer
            int byteLength = codec.calculateByteLength(dataLength);
            if (buffer.readableBytes() < byteLength) {
                if (childSchema.isRequired()) {
                    throw MessageException.fieldError(fieldSchema.getId() + "." + childSchema.getId(),
                            "Not enough bytes for nested field");
                }
                continue;
            }

            // Decode
            Object value = codec.decode(buffer, childSchema, dataLength);
            nestedValues.put(childSchema.getId(), value);
            message.setNestedField(fieldSchema.getId(), childSchema.getId(), value);
        }

        message.setField(fieldSchema.getId(), nestedValues);
    }

    /**
     * Parses the length field from the header.
     */
    private int parseLengthField(ByteBuf buffer, int lengthBytes, String encoding) {
        if ("BCD".equalsIgnoreCase(encoding)) {
            byte[] lengthData = new byte[lengthBytes];
            buffer.readBytes(lengthData);
            StringBuilder sb = new StringBuilder();
            for (byte b : lengthData) {
                sb.append((char) ('0' + ((b >> 4) & 0x0F)));
                sb.append((char) ('0' + (b & 0x0F)));
            }
            return Integer.parseInt(sb.toString());
        } else if ("BINARY".equalsIgnoreCase(encoding)) {
            if (lengthBytes == 2) {
                return buffer.readUnsignedShort();
            } else if (lengthBytes == 4) {
                return buffer.readInt();
            } else {
                return buffer.readUnsignedByte();
            }
        } else {
            // ASCII
            byte[] lengthData = new byte[lengthBytes];
            buffer.readBytes(lengthData);
            return Integer.parseInt(new String(lengthData).trim());
        }
    }
}
