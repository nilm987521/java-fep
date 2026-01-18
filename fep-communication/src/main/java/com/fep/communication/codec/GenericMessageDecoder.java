package com.fep.communication.codec;

import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageParser;
import com.fep.message.generic.schema.MessageSchema;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Netty decoder for GenericMessage using schema-based parsing.
 *
 * <p>This decoder uses GenericMessageParser to parse messages according to
 * the provided MessageSchema, supporting flexible message formats.
 *
 * <p>The length field format is determined by the schema's header configuration:
 * <ul>
 *   <li>lengthBytes: number of bytes for length field</li>
 *   <li>lengthEncoding: ASCII, BCD, or BINARY</li>
 * </ul>
 */
@Slf4j
public class GenericMessageDecoder extends ByteToMessageDecoder {

    /** Maximum message size (to prevent memory attacks) */
    private static final int MAX_MESSAGE_SIZE = 65535;

    private final MessageSchema schema;
    private final GenericMessageParser parser;
    private final int lengthFieldBytes;
    private final String lengthEncoding;

    /**
     * Creates a decoder with the specified schema.
     *
     * @param schema the message schema defining the format
     */
    public GenericMessageDecoder(MessageSchema schema) {
        this.schema = schema;
        this.parser = new GenericMessageParser();

        // Get length field configuration from schema
        if (schema.getHeader() != null && schema.getHeader().isIncludeLength()) {
            this.lengthFieldBytes = schema.getHeader().getLengthBytes();
            this.lengthEncoding = schema.getHeader().getLengthEncoding();
        } else {
            // Default: 4 bytes ASCII
            this.lengthFieldBytes = 4;
            this.lengthEncoding = "ASCII";
        }

        log.info("GenericMessageDecoder initialized: schema={}, lengthBytes={}, lengthEncoding={}",
                schema.getName(), lengthFieldBytes, lengthEncoding);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Log when data arrives
        if (log.isTraceEnabled() && in.readableBytes() > 0) {
            log.trace("Data available: {} bytes", in.readableBytes());
        }

        // Wait for length prefix
        if (in.readableBytes() < lengthFieldBytes) {
            if (in.readableBytes() > 0) {
                log.debug("Waiting for length field: have={} bytes, need={} bytes",
                        in.readableBytes(), lengthFieldBytes);
            }
            return;
        }

        // Mark current position
        in.markReaderIndex();

        // Peek at raw length bytes for logging
        byte[] rawLengthBytes = new byte[lengthFieldBytes];
        in.getBytes(in.readerIndex(), rawLengthBytes);

        // Read length prefix based on schema encoding
        int messageLength = readLength(in);

        // Log the length field parsing result
        log.debug("Length field parsed: rawBytes={}, encoding={}, parsedLength={}",
                bytesToHex(rawLengthBytes), lengthEncoding, messageLength);

        // Validate message length
        if (messageLength <= 0 || messageLength > MAX_MESSAGE_SIZE) {
            log.error("Invalid message length: {} (encoding={}, rawBytes={}, readable={})",
                    messageLength, lengthEncoding, bytesToHex(rawLengthBytes), in.readableBytes());
            in.resetReaderIndex();
            // Log first few bytes for diagnosis
            if (in.readableBytes() > 0) {
                byte[] preview = new byte[Math.min(20, in.readableBytes())];
                in.getBytes(in.readerIndex(), preview);
                log.error("First {} bytes of data: {}", preview.length, bytesToHex(preview));
            }
            throw new IllegalArgumentException("Invalid message length: " + messageLength);
        }

        // Check if full message is available
        if (in.readableBytes() < messageLength) {
            // Not enough data, wait for more
            in.resetReaderIndex();
            log.debug("Waiting for message body: have={} bytes, need={} bytes",
                    in.readableBytes(), messageLength);
            return;
        }

        // Read the message data
        byte[] messageData = new byte[messageLength];
        in.readBytes(messageData);

        log.info("Received message: {} bytes (length field: {} {})",
                messageLength, lengthFieldBytes, lengthEncoding);
        log.debug("Raw message data: {}", bytesToHex(messageData));

        try {
            // Parse using GenericMessageParser with schema
            // skipLengthField = true because we already stripped the length field
            GenericMessage message = parser.parse(messageData, schema, true);
            message.setRawData(messageData);
            out.add(message);

            String mti = message.getFieldAsString("mti");
            String stan = message.getFieldAsString("stan");
            log.info("Decoded GenericMessage: schema={}, MTI={}, STAN={}",
                    schema.getName(), mti, stan);
        } catch (Exception e) {
            log.error("Failed to decode message with schema [{}]: {} | Raw data: {}",
                    schema.getName(), e.getMessage(), bytesToHex(messageData), e);
            throw e;
        }
    }

    /**
     * Reads the length field based on schema encoding.
     */
    private int readLength(ByteBuf in) {
        return switch (lengthEncoding.toUpperCase()) {
            case "ASCII" -> readAsciiLength(in);
            case "BINARY" -> readBinaryLength(in);
            case "BCD" -> readBcdLength(in);
            default -> {
                log.warn("Unknown length encoding: {}, defaulting to ASCII", lengthEncoding);
                yield readAsciiLength(in);
            }
        };
    }

    /**
     * Reads ASCII encoded length (e.g., "0100" = 100).
     */
    private int readAsciiLength(ByteBuf in) {
        byte[] lengthBytes = new byte[lengthFieldBytes];
        in.readBytes(lengthBytes);
        String lengthStr = new String(lengthBytes, StandardCharsets.US_ASCII);
        try {
            return Integer.parseInt(lengthStr.trim());
        } catch (NumberFormatException e) {
            log.error("Invalid ASCII length: '{}' (hex: {})", lengthStr, bytesToHex(lengthBytes));
            return -1;
        }
    }

    /**
     * Reads binary encoded length (big-endian).
     */
    private int readBinaryLength(ByteBuf in) {
        if (lengthFieldBytes == 2) {
            return in.readUnsignedShort();
        } else if (lengthFieldBytes == 4) {
            return in.readInt();
        } else {
            return in.readUnsignedByte();
        }
    }

    /**
     * Reads BCD encoded length (e.g., 0x01 0x00 = 100).
     */
    private int readBcdLength(ByteBuf in) {
        int result = 0;
        for (int i = 0; i < lengthFieldBytes; i++) {
            byte b = in.readByte();
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;
            result = result * 100 + high * 10 + low;
        }
        return result;
    }

    /**
     * Converts bytes to hex string for logging.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("GenericMessageDecoder exception: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * Gets the schema used by this decoder.
     */
    public MessageSchema getSchema() {
        return schema;
    }
}
