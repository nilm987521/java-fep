package com.fep.jmeter.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A flexible length-field based frame decoder that supports multiple length encodings.
 *
 * <p>Unlike Netty's {@code LengthFieldBasedFrameDecoder} which only supports binary length fields,
 * this decoder supports:
 * <ul>
 *   <li><b>ASCII</b>: Length as ASCII digits (e.g., "0105" = 105)</li>
 *   <li><b>BCD</b>: Length in Binary Coded Decimal (e.g., 0x01 0x05 = 105)</li>
 *   <li><b>BINARY</b>: Length as big-endian binary (standard Netty behavior)</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * pipeline.addLast(new GenericLengthFieldDecoder(
 *     4,           // lengthFieldBytes
 *     "ASCII",     // lengthEncoding
 *     false,       // lengthIncludesHeader
 *     65535        // maxFrameLength
 * ));
 * }</pre>
 */
@Slf4j
public class GenericLengthFieldDecoder extends ByteToMessageDecoder {

    private final int lengthFieldBytes;
    private final String lengthEncoding;
    private final boolean lengthIncludesHeader;
    private final int maxFrameLength;

    /**
     * Creates a new decoder.
     *
     * @param lengthFieldBytes     number of bytes for the length field
     * @param lengthEncoding       encoding type: "ASCII", "BCD", or "BINARY"
     * @param lengthIncludesHeader whether the length value includes the length field itself
     * @param maxFrameLength       maximum allowed frame length
     */
    public GenericLengthFieldDecoder(int lengthFieldBytes, String lengthEncoding,
                                      boolean lengthIncludesHeader, int maxFrameLength) {
        this.lengthFieldBytes = lengthFieldBytes;
        this.lengthEncoding = lengthEncoding != null ? lengthEncoding.toUpperCase() : "BINARY";
        this.lengthIncludesHeader = lengthIncludesHeader;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Need at least length field bytes to read
        if (in.readableBytes() < lengthFieldBytes) {
            log.debug("Waiting for more data: have {} bytes, need {} for length field",
                    in.readableBytes(), lengthFieldBytes);
            return;
        }

        log.debug("Decoding frame: {} bytes available, lengthEncoding={}", in.readableBytes(), lengthEncoding);
        in.markReaderIndex();

        // Read and decode length field
        int frameLength = readLength(in);
        log.debug("Read length field: frameLength={}", frameLength);

        // Adjust for header if needed
        if (lengthIncludesHeader) {
            frameLength -= lengthFieldBytes;
        }

        // Validate length
        if (frameLength < 0) {
            log.error("Negative frame length: {}", frameLength);
            in.skipBytes(in.readableBytes());
            return;
        }

        if (frameLength > maxFrameLength) {
            log.error("Frame length {} exceeds max {}, discarding. Length field bytes: {}",
                    frameLength, maxFrameLength, formatBytes(in, lengthFieldBytes));
            in.skipBytes(in.readableBytes());
            return;
        }

        // Check if we have the complete frame
        if (in.readableBytes() < frameLength) {
            log.debug("Incomplete frame: have {} bytes after length field, need {} bytes",
                    in.readableBytes(), frameLength);
            in.resetReaderIndex();
            return;
        }

        // Extract frame (without length field, which was already consumed)
        ByteBuf frame = in.readRetainedSlice(frameLength);
        out.add(frame);

        log.debug("Decoded complete frame: {} bytes (length field stripped)", frameLength);
    }

    /**
     * Reads the length value from the buffer according to the encoding.
     */
    private int readLength(ByteBuf in) {
        return switch (lengthEncoding) {
            case "ASCII" -> readAsciiLength(in);
            case "BCD" -> readBcdLength(in);
            default -> readBinaryLength(in);  // BINARY
        };
    }

    /**
     * Reads ASCII-encoded length (e.g., "0105" = 105).
     */
    private int readAsciiLength(ByteBuf in) {
        byte[] lengthBytes = new byte[lengthFieldBytes];
        in.readBytes(lengthBytes);
        String lengthStr = new String(lengthBytes, StandardCharsets.US_ASCII);
        try {
            return Integer.parseInt(lengthStr);
        } catch (NumberFormatException e) {
            log.error("Invalid ASCII length field: '{}'", lengthStr);
            return -1;
        }
    }

    /**
     * Reads BCD-encoded length (e.g., 0x01 0x05 = 105).
     */
    private int readBcdLength(ByteBuf in) {
        int length = 0;
        for (int i = 0; i < lengthFieldBytes; i++) {
            int b = in.readByte() & 0xFF;
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;

            // Validate BCD digits
            if (high > 9 || low > 9) {
                log.error("Invalid BCD digit at position {}: 0x{}", i, String.format("%02X", b));
                return -1;
            }

            length = length * 100 + high * 10 + low;
        }
        return length;
    }

    /**
     * Reads binary-encoded length (big-endian).
     */
    private int readBinaryLength(ByteBuf in) {
        return switch (lengthFieldBytes) {
            case 1 -> in.readByte() & 0xFF;
            case 2 -> in.readUnsignedShort();
            case 3 -> in.readMedium();
            case 4 -> in.readInt();
            default -> {
                // For arbitrary lengths, read byte by byte
                int length = 0;
                for (int i = 0; i < lengthFieldBytes; i++) {
                    length = (length << 8) | (in.readByte() & 0xFF);
                }
                yield length;
            }
        };
    }

    /**
     * Formats bytes for error logging.
     */
    private String formatBytes(ByteBuf in, int count) {
        in.markReaderIndex();
        StringBuilder sb = new StringBuilder();
        int available = Math.min(count, in.readableBytes());
        for (int i = 0; i < available; i++) {
            sb.append(String.format("%02X ", in.readByte()));
        }
        in.resetReaderIndex();
        return sb.toString().trim();
    }
}
