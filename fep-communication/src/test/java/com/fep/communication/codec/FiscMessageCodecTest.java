package com.fep.communication.codec;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FISC message encoder and decoder.
 */
@DisplayName("FISC Message Codec Tests")
class FiscMessageCodecTest {

    @Test
    @DisplayName("Should encode message to bytes")
    void shouldEncodeMessageToBytes() {
        EmbeddedChannel channel = new EmbeddedChannel(new FiscMessageEncoder());

        Iso8583Message message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        message.setField(7, "0106213900");
        message.setField(11, "000001");
        message.setField(70, "301");

        assertTrue(channel.writeOutbound(message));
        ByteBuf encoded = channel.readOutbound();

        assertNotNull(encoded);
        assertTrue(encoded.readableBytes() > 0);

        encoded.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("Should encode and decode round trip")
    void shouldEncodeAndDecodeRoundTrip() {
        // Create encoder and decoder channels
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new FiscMessageEncoder());
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new FiscMessageDecoder());

        // Create original message using factory (ensures proper format)
        Iso8583MessageFactory factory = new Iso8583MessageFactory();
        Iso8583Message original = factory.createEchoTestMessage();

        // Encode the message
        assertTrue(encoderChannel.writeOutbound(original));
        ByteBuf encoded = encoderChannel.readOutbound();
        assertNotNull(encoded);

        // Decode the message
        assertTrue(decoderChannel.writeInbound(encoded));
        Iso8583Message decoded = decoderChannel.readInbound();

        // Verify
        assertNotNull(decoded);
        assertEquals(original.getMti(), decoded.getMti());
        assertEquals(original.getFieldAsString(70), decoded.getFieldAsString(70));

        encoderChannel.finishAndReleaseAll();
        decoderChannel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("Should wait for length prefix before decoding")
    void shouldWaitForLengthPrefixBeforeDecoding() {
        EmbeddedChannel channel = new EmbeddedChannel(new FiscMessageDecoder());

        // Send only 1 byte (need 2 for length prefix)
        ByteBuf partial = Unpooled.buffer();
        partial.writeByte(0x00);
        assertFalse(channel.writeInbound(partial));

        // No message yet
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("Should wait for complete message after reading length")
    void shouldWaitForCompleteMessageAfterReadingLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new FiscMessageDecoder());

        // Send length prefix indicating 100 bytes of data
        ByteBuf lengthOnly = Unpooled.buffer();
        lengthOnly.writeByte(0x01);  // BCD 01
        lengthOnly.writeByte(0x00);  // BCD 00 = 100 bytes
        assertFalse(channel.writeInbound(lengthOnly));

        // No message yet (waiting for 100 bytes of data)
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("Should handle multiple encoded messages")
    void shouldHandleMultipleEncodedMessages() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new FiscMessageEncoder());
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new FiscMessageDecoder());

        Iso8583MessageFactory factory = new Iso8583MessageFactory();

        // Encode two messages
        Iso8583Message msg1 = factory.createEchoTestMessage();
        Iso8583Message msg2 = factory.createSignOnMessage();

        assertTrue(encoderChannel.writeOutbound(msg1));
        assertTrue(encoderChannel.writeOutbound(msg2));

        ByteBuf encoded1 = encoderChannel.readOutbound();
        ByteBuf encoded2 = encoderChannel.readOutbound();

        // Combine and decode
        ByteBuf combined = Unpooled.wrappedBuffer(encoded1, encoded2);
        assertTrue(decoderChannel.writeInbound(combined));

        Iso8583Message decoded1 = decoderChannel.readInbound();
        Iso8583Message decoded2 = decoderChannel.readInbound();

        assertNotNull(decoded1);
        assertNotNull(decoded2);
        assertEquals("0800", decoded1.getMti());
        assertEquals("0800", decoded2.getMti());
        assertEquals("301", decoded1.getFieldAsString(70));
        assertEquals("001", decoded2.getFieldAsString(70));

        encoderChannel.finishAndReleaseAll();
        decoderChannel.finishAndReleaseAll();
    }
}
