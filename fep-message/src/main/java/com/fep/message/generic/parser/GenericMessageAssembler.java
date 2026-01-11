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

import java.util.Map;

/**
 * Assembles GenericMessage objects into byte arrays according to the schema.
 */
@Slf4j
public class GenericMessageAssembler {

    /**
     * Assembles a message to bytes.
     *
     * @param message the message to assemble
     * @return the assembled bytes
     */
    public byte[] assemble(GenericMessage message) {
        MessageSchema schema = message.getSchema();
        ByteBuf buffer = Unpooled.buffer();

        try {
            // Reserve space for header length field if needed
            int lengthFieldPosition = -1;
            int lengthFieldBytes = 0;
            String lengthEncoding = "BCD";

            if (schema.getHeader() != null && schema.getHeader().isIncludeLength()) {
                lengthFieldPosition = buffer.writerIndex();
                lengthFieldBytes = schema.getHeader().getLengthBytes();
                lengthEncoding = schema.getHeader().getLengthEncoding();
                // Write placeholder
                buffer.writeZero(lengthFieldBytes);
            }

            // Write header fields
            if (schema.getHeader() != null && schema.getHeader().getFields() != null) {
                for (FieldSchema fieldSchema : schema.getHeader().getFields()) {
                    Object value = message.getField(fieldSchema.getId());
                    if (value == null) {
                        value = fieldSchema.getDefaultValue();
                    }
                    if (value != null || fieldSchema.isRequired()) {
                        encodeField(fieldSchema, value, buffer, schema);
                    }
                }
            }

            // Write body fields
            for (FieldSchema fieldSchema : schema.getFields()) {
                Object value = message.getField(fieldSchema.getId());
                if (value == null) {
                    value = fieldSchema.getDefaultValue();
                }
                if (value != null) {
                    encodeField(fieldSchema, value, buffer, schema);
                } else if (fieldSchema.isRequired()) {
                    throw MessageException.fieldError(fieldSchema.getId(),
                            "Required field is missing");
                }
            }

            // Write trailer fields
            if (schema.getTrailer() != null && schema.getTrailer().getFields() != null) {
                for (FieldSchema fieldSchema : schema.getTrailer().getFields()) {
                    Object value = message.getField(fieldSchema.getId());
                    if (value == null) {
                        value = fieldSchema.getDefaultValue();
                    }
                    if (value != null || fieldSchema.isRequired()) {
                        encodeField(fieldSchema, value, buffer, schema);
                    }
                }
            }

            // Update length field
            if (lengthFieldPosition >= 0) {
                int totalLength = buffer.readableBytes();
                if (!schema.getHeader().isLengthIncludesHeader()) {
                    totalLength -= lengthFieldBytes;
                }
                updateLengthField(buffer, lengthFieldPosition, totalLength, lengthFieldBytes, lengthEncoding);
            }

            // Extract bytes
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);

            log.debug("Assembled message [{}]: {} bytes", schema.getName(), result.length);
            return result;

        } finally {
            buffer.release();
        }
    }

    /**
     * Encodes a single field to the buffer.
     */
    private void encodeField(FieldSchema fieldSchema, Object value, ByteBuf buffer, MessageSchema schema) {
        if (fieldSchema.isComposite()) {
            // Handle composite field
            encodeCompositeField(fieldSchema, value, buffer, schema);
            return;
        }

        String encoding = fieldSchema.getEncoding();
        if (encoding == null) {
            encoding = schema.getDefaultEncoding();
        }

        GenericCodec codec = CodecRegistry.get(encoding);
        String strValue = value != null ? value.toString() : "";

        // Write length prefix for variable-length fields
        if (fieldSchema.isVariableLength()) {
            int dataLength = strValue.length();
            int prefixDigits = fieldSchema.getLengthPrefixDigits();
            String lengthEncoding = fieldSchema.getLengthEncoding();

            GenericCodec lengthCodec = CodecRegistry.get(lengthEncoding);
            lengthCodec.encodeLengthPrefix(dataLength, prefixDigits, buffer);
        }

        // Write the data
        codec.encode(value, fieldSchema, buffer);

        log.trace("Encoded field [{}]: value={}, encoding={}",
                fieldSchema.getId(),
                fieldSchema.isSensitive() ? "****" : strValue,
                encoding);
    }

    /**
     * Encodes a composite field with nested children.
     */
    @SuppressWarnings("unchecked")
    private void encodeCompositeField(FieldSchema fieldSchema, Object value, ByteBuf buffer, MessageSchema schema) {
        Map<String, Object> nestedValues;

        if (value instanceof Map) {
            nestedValues = (Map<String, Object>) value;
        } else if (value instanceof GenericMessage msg) {
            nestedValues = msg.getAllFields();
        } else {
            log.warn("Composite field [{}] has non-map value, skipping", fieldSchema.getId());
            return;
        }

        for (FieldSchema childSchema : fieldSchema.getFields()) {
            Object childValue = nestedValues.get(childSchema.getId());
            if (childValue == null) {
                childValue = childSchema.getDefaultValue();
            }
            if (childValue != null || childSchema.isRequired()) {
                encodeField(childSchema, childValue, buffer, schema);
            }
        }
    }

    /**
     * Updates the length field at the specified position.
     */
    private void updateLengthField(ByteBuf buffer, int position, int length, int lengthBytes, String encoding) {
        // Save current position
        int currentWriterIndex = buffer.writerIndex();

        // Move to length field position
        buffer.writerIndex(position);

        // Create temporary buffer for length encoding
        ByteBuf tempBuf = Unpooled.buffer(lengthBytes);
        try {
            GenericCodec codec = CodecRegistry.get(encoding);

            if ("BCD".equalsIgnoreCase(encoding)) {
                // BCD encoding for length
                String lengthStr = String.format("%0" + (lengthBytes * 2) + "d", length);
                codec.encode(lengthStr, null, tempBuf);
            } else if ("BINARY".equalsIgnoreCase(encoding)) {
                // Binary encoding (big-endian)
                if (lengthBytes == 2) {
                    tempBuf.writeShort(length);
                } else if (lengthBytes == 4) {
                    tempBuf.writeInt(length);
                } else {
                    tempBuf.writeByte(length);
                }
            } else {
                // ASCII encoding
                String lengthStr = String.format("%0" + lengthBytes + "d", length);
                codec.encode(lengthStr, null, tempBuf);
            }

            // Copy to main buffer
            byte[] lengthData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(lengthData);
            buffer.writeBytes(lengthData);

        } finally {
            tempBuf.release();
        }

        // Restore writer position
        buffer.writerIndex(currentWriterIndex);
    }
}
