package com.fep.message.iso8583.parser;

import com.fep.common.util.HexUtils;
import com.fep.message.exception.MessageException;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.bitmap.Bitmap;
import com.fep.message.iso8583.codec.DefaultFieldCodec;
import com.fep.message.iso8583.codec.FieldCodec;
import com.fep.message.iso8583.field.FieldDefinition;
import com.fep.message.iso8583.field.FiscFieldDefinitions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/**
 * Assembler for FISC ISO 8583 messages.
 *
 * <p>Assembles ISO 8583 messages into the FISC wire format:
 * <pre>
 * +--------+--------+--------+------+
 * | Length |  MTI   | Bitmap | Data |
 * | N bytes| 4 bytes| 8/16 B | var  |
 * +--------+--------+--------+------+
 * </pre>
 *
 * <p>Supports configurable length prefix encoding:
 * <ul>
 *   <li>BCD: 2 bytes BCD encoded (default)</li>
 *   <li>ASCII: 4 bytes ASCII encoded</li>
 *   <li>BINARY: 2 bytes binary (big-endian)</li>
 * </ul>
 */
@Slf4j
public class FiscMessageAssembler implements MessageAssembler {

    /** Length encoding types */
    public enum LengthEncoding {
        BCD,    // 2 bytes BCD (default)
        ASCII,  // 4 bytes ASCII
        BINARY  // 2 bytes binary big-endian
    }

    /** MTI size in bytes (BCD encoded) */
    private static final int MTI_SIZE = 2;

    /** Whether to include length prefix when assembling */
    private final boolean includeLengthPrefix;

    /** Length encoding type */
    private final LengthEncoding lengthEncoding;

    /** Field codec for encoding/decoding fields */
    private final FieldCodec fieldCodec;

    /**
     * Creates an assembler with default settings (with BCD length prefix).
     */
    public FiscMessageAssembler() {
        this(true, LengthEncoding.BCD);
    }

    /**
     * Creates an assembler with specified settings.
     *
     * @param includeLengthPrefix whether to include length prefix
     */
    public FiscMessageAssembler(boolean includeLengthPrefix) {
        this(includeLengthPrefix, LengthEncoding.BCD);
    }

    /**
     * Creates an assembler with specified length encoding.
     *
     * @param lengthEncoding the length prefix encoding type
     */
    public FiscMessageAssembler(LengthEncoding lengthEncoding) {
        this(true, lengthEncoding);
    }

    /**
     * Creates an assembler with full configuration.
     *
     * @param includeLengthPrefix whether to include length prefix
     * @param lengthEncoding the length prefix encoding type
     */
    public FiscMessageAssembler(boolean includeLengthPrefix, LengthEncoding lengthEncoding) {
        this.includeLengthPrefix = includeLengthPrefix;
        this.lengthEncoding = lengthEncoding;
        this.fieldCodec = new DefaultFieldCodec();
    }

    @Override
    public void assemble(Iso8583Message message, ByteBuf buffer) {
        try {
            // Build the message body first (to calculate length)
            ByteBuf bodyBuffer = Unpooled.buffer(512);

            // Write MTI
            writeMti(message.getMti(), bodyBuffer);

            // Build and write bitmap
            Bitmap bitmap = buildBitmap(message);
            bodyBuffer.writeBytes(bitmap.toBytes());

            // Write data fields
            writeFields(message, bitmap, bodyBuffer);

            // Now write the complete message with optional length prefix
            if (includeLengthPrefix) {
                int bodyLength = bodyBuffer.readableBytes();
                writeLengthPrefix(bodyLength, buffer);
            }

            // Copy body to output buffer
            buffer.writeBytes(bodyBuffer);
            bodyBuffer.release();

            if (log.isDebugEnabled()) {
                log.debug("Assembled message: MTI={}, size={} bytes",
                    message.getMti(), buffer.readableBytes());
            }

        } catch (MessageException e) {
            throw e;
        } catch (Exception e) {
            throw MessageException.assembleError("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public byte[] assemble(Iso8583Message message) {
        ByteBuf buffer = Unpooled.buffer(1024);
        try {
            assemble(message, buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally {
            buffer.release();
        }
    }

    /**
     * Writes the length prefix based on configured encoding.
     */
    private void writeLengthPrefix(int length, ByteBuf buffer) {
        if (length > 9999) {
            throw MessageException.assembleError("Message too long: " + length);
        }

        switch (lengthEncoding) {
            case ASCII -> writeAsciiLength(length, buffer);
            case BINARY -> writeBinaryLength(length, buffer);
            case BCD -> writeBcdLength(length, buffer);
        }
    }

    /**
     * Writes 4-byte ASCII encoded length (e.g., 100 -> "0100").
     */
    private void writeAsciiLength(int length, ByteBuf buffer) {
        String lengthStr = String.format("%04d", length);
        buffer.writeBytes(lengthStr.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Writes 2-byte binary encoded length (big-endian).
     */
    private void writeBinaryLength(int length, ByteBuf buffer) {
        buffer.writeShort(length);
    }

    /**
     * Writes 2-byte BCD encoded length (e.g., 100 -> 0x01 0x00).
     */
    private void writeBcdLength(int length, ByteBuf buffer) {
        String lengthStr = HexUtils.leftPad(String.valueOf(length), 4, '0');
        byte[] lengthBcd = HexUtils.stringToBcd(lengthStr);
        buffer.writeBytes(lengthBcd);
    }

    /**
     * Writes the MTI in BCD format.
     */
    private void writeMti(String mti, ByteBuf buffer) {
        if (mti == null || mti.length() != 4) {
            throw MessageException.assembleError("Invalid MTI: " + mti);
        }

        byte[] mtiBcd = HexUtils.stringToBcd(mti);
        buffer.writeBytes(mtiBcd);
    }

    /**
     * Builds the bitmap based on fields present in the message.
     */
    private Bitmap buildBitmap(Iso8583Message message) {
        Set<Integer> fieldNumbers = message.getFieldNumbers();
        return Bitmap.fromFields(fieldNumbers);
    }

    /**
     * Writes all data fields to the buffer.
     */
    private void writeFields(Iso8583Message message, Bitmap bitmap, ByteBuf buffer) {
        // Get sorted field numbers (excluding bitmap indicator)
        Set<Integer> fieldNumbers = new TreeSet<>(bitmap.getDataFields());

        for (int fieldNum : fieldNumbers) {
            // Skip field 1 (secondary bitmap indicator)
            if (fieldNum == 1) {
                continue;
            }

            Object value = message.getField(fieldNum);
            if (value == null) {
                continue;
            }

            FieldDefinition definition = FiscFieldDefinitions.get(fieldNum);
            if (definition == null) {
                throw MessageException.fieldError(fieldNum, "No definition found");
            }

            try {
                fieldCodec.encode(definition, value, buffer);
            } catch (Exception e) {
                throw MessageException.fieldError(fieldNum,
                    "Failed to encode: " + e.getMessage());
            }
        }
    }
}
