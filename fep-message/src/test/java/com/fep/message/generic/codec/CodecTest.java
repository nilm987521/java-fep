package com.fep.message.generic.codec;

import com.fep.message.generic.schema.FieldSchema;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodecTest {

    @Test
    void asciiCodecShouldEncodeAndDecode() {
        AsciiCodec codec = new AsciiCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(10)
                .encoding("ASCII")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("HELLO", field, buffer);

        // Should be padded to 10 chars
        assertEquals(10, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 10);
        assertEquals("HELLO", decoded.trim());
    }

    @Test
    void bcdCodecShouldEncodeAndDecode() {
        BcdCodec codec = new BcdCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(6)
                .encoding("BCD")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("123456", field, buffer);

        // 6 digits = 3 bytes in BCD
        assertEquals(3, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 6);
        assertEquals("123456", decoded);
    }

    @Test
    void bcdCodecShouldHandleOddDigits() {
        BcdCodec codec = new BcdCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(5)
                .encoding("BCD")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("12345", field, buffer);

        // 5 digits padded to 6 = 3 bytes
        assertEquals(3, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 5);
        assertEquals("12345", decoded);
    }

    @Test
    void hexCodecShouldEncodeAndDecode() {
        HexCodec codec = new HexCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(4)  // 4 bytes
                .encoding("HEX")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("DEADBEEF", field, buffer);

        assertEquals(4, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 4);
        assertEquals("DEADBEEF", decoded);
    }

    @Test
    void binaryCodecShouldEncodeAndDecode() {
        BinaryCodec codec = new BinaryCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(8)
                .encoding("BINARY")
                .build();

        byte[] input = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

        ByteBuf buffer = Unpooled.buffer();
        codec.encode(input, field, buffer);

        assertEquals(8, buffer.readableBytes());

        byte[] decoded = (byte[]) codec.decode(buffer, field, 8);
        assertArrayEquals(input, decoded);
    }

    @Test
    void packedDecimalShouldEncodePositiveNumber() {
        PackedDecimalCodec codec = new PackedDecimalCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(5)
                .encoding("PACKED_DECIMAL")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("12345", field, buffer);

        // 5 digits + sign nibble = 6 nibbles = 3 bytes
        assertEquals(3, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 5);
        assertEquals("12345", decoded);
    }

    @Test
    void packedDecimalShouldEncodeNegativeNumber() {
        PackedDecimalCodec codec = new PackedDecimalCodec();
        FieldSchema field = FieldSchema.builder()
                .id("test")
                .length(5)
                .encoding("PACKED_DECIMAL")
                .build();

        ByteBuf buffer = Unpooled.buffer();
        codec.encode("-12345", field, buffer);

        assertEquals(3, buffer.readableBytes());

        String decoded = (String) codec.decode(buffer, field, 5);
        assertEquals("-12345", decoded);
    }

    @Test
    void codecRegistryShouldReturnCorrectCodec() {
        assertTrue(CodecRegistry.get("ASCII") instanceof AsciiCodec);
        assertTrue(CodecRegistry.get("BCD") instanceof BcdCodec);
        assertTrue(CodecRegistry.get("HEX") instanceof HexCodec);
        assertTrue(CodecRegistry.get("BINARY") instanceof BinaryCodec);
        assertTrue(CodecRegistry.get("EBCDIC") instanceof EbcdicCodec);
        assertTrue(CodecRegistry.get("PACKED_DECIMAL") instanceof PackedDecimalCodec);
    }

    @Test
    void codecRegistryShouldBeCaseInsensitive() {
        assertNotNull(CodecRegistry.get("ascii"));
        assertNotNull(CodecRegistry.get("ASCII"));
        assertNotNull(CodecRegistry.get("Ascii"));
    }

    @Test
    void codecRegistryShouldThrowForUnknownCodec() {
        assertThrows(IllegalArgumentException.class, () -> CodecRegistry.get("UNKNOWN"));
    }

    @Test
    void bcdLengthPrefixShouldWorkCorrectly() {
        BcdCodec codec = new BcdCodec();
        ByteBuf buffer = Unpooled.buffer();

        codec.encodeLengthPrefix(37, 2, buffer);

        // 2 digits BCD = 1 byte
        assertEquals(1, buffer.readableBytes());

        int length = codec.decodeLengthPrefix(buffer, 2);
        assertEquals(37, length);
    }
}
