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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses byte arrays into GenericMessage objects according to the schema.
 */
@Slf4j
public class GenericMessageParser {

    /**
     * Context for tracking parse progress and bitmap state.
     */
    private static class ParseContext {
        int totalBytes;
        int bytesRead;
        int startIndex;  // Buffer start position for calculating bytesRead
        String currentSection;  // "header", "body", "trailer"
        String currentField;
        List<String> parsedFields = new ArrayList<>();
        Map<String, Object> parsedValues = new LinkedHashMap<>();
        byte[] rawData;
        ByteBuf buffer;  // Reference to buffer for bytesRead calculation
        private static final java.util.HexFormat HEX = java.util.HexFormat.of();

        // Bitmap tracking: maps bitmap field ID to its parsed byte array
        Map<String, byte[]> bitmapValues = new LinkedHashMap<>();
        // Maps bitmap field ID to its controls list
        Map<String, List<String>> bitmapControls = new LinkedHashMap<>();

        void updateBytesRead() {
            if (buffer != null) {
                bytesRead = buffer.readerIndex() - startIndex;
            }
        }

        /**
         * Checks if a field should be parsed based on bitmap control.
         * Returns true if the field is not controlled by any bitmap, or if the corresponding bit is set.
         */
        boolean shouldParseField(String fieldId) {
            for (Map.Entry<String, List<String>> entry : bitmapControls.entrySet()) {
                String bitmapId = entry.getKey();
                List<String> controls = entry.getValue();
                int bitIndex = controls.indexOf(fieldId);

                if (bitIndex >= 0) {
                    // This field is controlled by this bitmap
                    byte[] bitmap = bitmapValues.get(bitmapId);
                    if (bitmap == null) {
                        // Bitmap not yet parsed, skip this field
                        log.debug("Field [{}] controlled by bitmap [{}] which is not yet parsed, skipping", fieldId, bitmapId);
                        return false;
                    }
                    boolean bitSet = isBitSet(bitmap, bitIndex);
                    if (!bitSet) {
                        log.trace("Field [{}] skipped: bit {} not set in bitmap [{}]", fieldId, bitIndex, bitmapId);
                    }
                    return bitSet;
                }
            }
            // Field not controlled by any bitmap, always parse
            return true;
        }

        /**
         * Checks if a specific bit is set in the bitmap.
         * Bit 0 is MSB of first byte, bit 7 is LSB of first byte, etc.
         */
        private boolean isBitSet(byte[] bitmap, int bitIndex) {
            int byteIndex = bitIndex / 8;
            int bitPosition = 7 - (bitIndex % 8);  // MSB first
            if (byteIndex >= bitmap.length) {
                return false;
            }
            return (bitmap[byteIndex] & (1 << bitPosition)) != 0;
        }

        String getProgressSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== Parse Progress ===\n");
            sb.append(String.format("Bytes: %d/%d (%.1f%%)%n",
                    bytesRead, totalBytes, totalBytes > 0 ? (100.0 * bytesRead / totalBytes) : 0));
            sb.append("Section: ").append(currentSection != null ? currentSection : "unknown").append("\n");
            sb.append("Current field: ").append(currentField != null ? currentField : "none").append("\n");

            sb.append("\nParsed fields (").append(parsedFields.size()).append("):\n");
            if (parsedFields.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (String fieldId : parsedFields) {
                    Object value = parsedValues.get(fieldId);
                    sb.append("  ").append(fieldId).append(" = ");
                    if (value != null) {
                        sb.append(value);
                        // Also show hex for string values
                        if (value instanceof String strVal) {
                            sb.append(" [hex: ").append(HEX.formatHex(strVal.getBytes())).append("]");
                        }
                    } else {
                        sb.append("(null)");
                    }
                    sb.append("\n");
                }
            }

            // Show remaining unparsed bytes
            if (rawData != null && bytesRead < totalBytes) {
                int remaining = Math.min(totalBytes - bytesRead, 50);
                byte[] unparsed = new byte[remaining];
                System.arraycopy(rawData, bytesRead, unparsed, 0, remaining);
                sb.append("\nRemaining bytes (first ").append(remaining).append("): ");
                sb.append(HEX.formatHex(unparsed));
                if (totalBytes - bytesRead > 50) {
                    sb.append("...");
                }
            }

            return sb.toString();
        }
    }

    /**
     * Parses bytes into a message.
     *
     * @param data   the raw bytes
     * @param schema the message schema
     * @return the parsed message
     */
    public GenericMessage parse(byte[] data, MessageSchema schema) {
        return parse(data, schema, false);
    }

    /**
     * Parses bytes into a message with option to skip length field.
     *
     * @param data            the raw bytes
     * @param schema          the message schema
     * @param skipLengthField if true, skip parsing the length field (useful when length field
     *                        was already stripped by a frame decoder)
     * @return the parsed message
     */
    public GenericMessage parse(byte[] data, MessageSchema schema, boolean skipLengthField) {
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        try {
            return parse(buffer, schema, skipLengthField);
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
        return parse(buffer, schema, false);
    }

    /**
     * Parses a ByteBuf into a message with option to skip length field.
     *
     * @param buffer          the input buffer
     * @param schema          the message schema
     * @param skipLengthField if true, skip parsing the length field (useful when length field
     *                        was already stripped by a frame decoder)
     * @return the parsed message
     */
    public GenericMessage parse(ByteBuf buffer, MessageSchema schema, boolean skipLengthField) {
        GenericMessage message = new GenericMessage(schema);
        ParseContext ctx = new ParseContext();
        ctx.totalBytes = buffer.readableBytes();
        ctx.startIndex = buffer.readerIndex();
        ctx.buffer = buffer;

        // Store raw data for error reporting
        if (buffer.hasArray()) {
            ctx.rawData = buffer.array();
        } else {
            byte[] data = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), data);
            ctx.rawData = data;
        }

        try {
            // Register all bitmap controls from schema
            registerBitmapControls(schema, ctx);

            // Skip/parse header length field if present and not already stripped
            if (!skipLengthField && schema.getHeader() != null && schema.getHeader().isIncludeLength()) {
                ctx.currentSection = "length-header";
                ctx.currentField = "length";
                int lengthBytes = schema.getHeader().getLengthBytes();
                String lengthEncoding = schema.getHeader().getLengthEncoding();

                int messageLength = parseLengthField(buffer, lengthBytes, lengthEncoding);
                ctx.updateBytesRead();
                ctx.parsedFields.add("length");
                ctx.parsedValues.put("length", messageLength);
                log.debug("Parsed message length: {}", messageLength);
            }

            // Parse header fields
            if (schema.getHeader() != null && schema.getHeader().getFields() != null) {
                ctx.currentSection = "header";
                for (FieldSchema fieldSchema : schema.getHeader().getFields()) {
                    if (buffer.readableBytes() <= 0) break;
                    ctx.currentField = fieldSchema.getId();
                    parseFieldWithBitmapCheck(fieldSchema, buffer, message, schema, ctx);
                }
            }

            // Parse body fields
            ctx.currentSection = "body";
            for (FieldSchema fieldSchema : schema.getFields()) {
                if (buffer.readableBytes() <= 0) break;
                ctx.currentField = fieldSchema.getId();
                parseFieldWithBitmapCheck(fieldSchema, buffer, message, schema, ctx);
            }

            // Parse trailer fields
            if (schema.getTrailer() != null && schema.getTrailer().getFields() != null) {
                ctx.currentSection = "trailer";
                for (FieldSchema fieldSchema : schema.getTrailer().getFields()) {
                    if (buffer.readableBytes() <= 0) break;
                    ctx.currentField = fieldSchema.getId();
                    parseFieldWithBitmapCheck(fieldSchema, buffer, message, schema, ctx);
                }
            }

            // Store raw data
            message.setRawData(buffer.array());

            log.debug("Parsed message [{}]: {} fields", schema.getName(), message.getSetFieldIds().size());
            return message;

        } catch (MessageException e) {
            // Enhance error message with parse progress
            throw MessageException.parseError(e.getMessage() + ctx.getProgressSummary());
        } catch (Exception e) {
            // Wrap unexpected exceptions with parse progress
            throw MessageException.parseError(
                    "Unexpected error: " + e.getMessage() + ctx.getProgressSummary());
        }
    }

    /**
     * Registers all bitmap controls from the schema into the parse context.
     */
    private void registerBitmapControls(MessageSchema schema, ParseContext ctx) {
        // Check header fields
        if (schema.getHeader() != null && schema.getHeader().getFields() != null) {
            for (FieldSchema field : schema.getHeader().getFields()) {
                if (field.isBitmap() && field.getControls() != null) {
                    ctx.bitmapControls.put(field.getId(), field.getControls());
                    log.debug("Registered bitmap [{}] with {} controls", field.getId(), field.getControls().size());
                }
            }
        }
        // Check body fields
        for (FieldSchema field : schema.getFields()) {
            if (field.isBitmap() && field.getControls() != null) {
                ctx.bitmapControls.put(field.getId(), field.getControls());
                log.debug("Registered bitmap [{}] with {} controls", field.getId(), field.getControls().size());
            }
        }
        // Check trailer fields
        if (schema.getTrailer() != null && schema.getTrailer().getFields() != null) {
            for (FieldSchema field : schema.getTrailer().getFields()) {
                if (field.isBitmap() && field.getControls() != null) {
                    ctx.bitmapControls.put(field.getId(), field.getControls());
                    log.debug("Registered bitmap [{}] with {} controls", field.getId(), field.getControls().size());
                }
            }
        }
    }

    /**
     * Parses a field with bitmap control check.
     */
    private void parseFieldWithBitmapCheck(FieldSchema fieldSchema, ByteBuf buffer,
                                           GenericMessage message, MessageSchema schema, ParseContext ctx) {
        // Bitmap fields are always parsed
        if (fieldSchema.isBitmap()) {
            parseBitmapField(fieldSchema, buffer, message, ctx);
            ctx.updateBytesRead();
            ctx.parsedFields.add(fieldSchema.getId());
            ctx.parsedValues.put(fieldSchema.getId(), "[" + fieldSchema.getLength() + " bytes]");
            return;
        }

        // Check if this field should be parsed based on bitmap
        if (!ctx.shouldParseField(fieldSchema.getId())) {
            log.trace("Skipping field [{}]: not present in bitmap", fieldSchema.getId());
            return;
        }

        // Parse the field
        parseField(fieldSchema, buffer, message, schema);
        ctx.updateBytesRead();
        ctx.parsedFields.add(fieldSchema.getId());
        ctx.parsedValues.put(fieldSchema.getId(), message.getField(fieldSchema.getId()));
    }

    /**
     * Parses a bitmap field and stores its value in the context.
     */
    private void parseBitmapField(FieldSchema fieldSchema, ByteBuf buffer, GenericMessage message, ParseContext ctx) {
        int length = fieldSchema.getLength();
        if (buffer.readableBytes() < length) {
            throw MessageException.fieldError(fieldSchema.getId(),
                    "Not enough bytes for bitmap: need " + length + ", have " + buffer.readableBytes());
        }

        byte[] bitmapBytes = new byte[length];
        buffer.readBytes(bitmapBytes);

        // Store in context for field control
        ctx.bitmapValues.put(fieldSchema.getId(), bitmapBytes);

        // Store in message (as hex string for display)
        message.setField(fieldSchema.getId(), bitmapBytes);

        log.debug("Parsed bitmap [{}]: {} bytes, hex={}", fieldSchema.getId(), length,
                java.util.HexFormat.of().formatHex(bitmapBytes));
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
