package com.fep.communication.codec;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.parser.FiscMessageParser;
import com.fep.message.iso8583.parser.MessageParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Netty decoder for ISO 8583 messages.
 * Converts incoming bytes to Iso8583Message objects.
 *
 * <p>Message format:
 * <pre>
 * +--------+----------+
 * | Length |   Data   |
 * | 2 bytes|  N bytes |
 * +--------+----------+
 * </pre>
 *
 * Length is BCD encoded, indicating the length of the Data portion.
 */
@Slf4j
public class FiscMessageDecoder extends ByteToMessageDecoder {

    /** Length prefix size in bytes (BCD encoded) */
    private static final int LENGTH_PREFIX_SIZE = 2;

    /** Maximum message size (to prevent memory attacks) */
    private static final int MAX_MESSAGE_SIZE = 65535;

    private final MessageParser parser;

    public FiscMessageDecoder() {
        this.parser = new FiscMessageParser(false); // Parser without length prefix (we handle it here)
    }

    public FiscMessageDecoder(MessageParser parser) {
        this.parser = parser;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Wait for length prefix
        if (in.readableBytes() < LENGTH_PREFIX_SIZE) {
            return;
        }

        // Mark current position
        in.markReaderIndex();

        // Read length prefix (BCD encoded)
        int messageLength = readBcdLength(in);

        // Validate message length
        if (messageLength <= 0 || messageLength > MAX_MESSAGE_SIZE) {
            log.error("Invalid message length: {}", messageLength);
            in.resetReaderIndex();
            throw new IllegalArgumentException("Invalid message length: " + messageLength);
        }

        // Check if full message is available
        if (in.readableBytes() < messageLength) {
            // Not enough data, wait for more
            in.resetReaderIndex();
            return;
        }

        // Read the message data
        byte[] messageData = new byte[messageLength];
        in.readBytes(messageData);

        log.debug("Received {} bytes, decoding message", messageLength);

        try {
            Iso8583Message message = parser.parse(messageData);
            out.add(message);
            log.debug("Decoded message: MTI={}", message.getMti());
        } catch (Exception e) {
            log.error("Failed to decode message: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Reads the 2-byte BCD encoded length.
     */
    private int readBcdLength(ByteBuf in) {
        byte b1 = in.readByte();
        byte b2 = in.readByte();

        int high1 = (b1 >> 4) & 0x0F;
        int low1 = b1 & 0x0F;
        int high2 = (b2 >> 4) & 0x0F;
        int low2 = b2 & 0x0F;

        return high1 * 1000 + low1 * 100 + high2 * 10 + low2;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Decoder exception: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
