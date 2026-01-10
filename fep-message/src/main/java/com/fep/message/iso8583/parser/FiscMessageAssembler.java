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

import java.util.Set;
import java.util.TreeSet;

/**
 * Assembler for FISC ISO 8583 messages.
 *
 * <p>Assembles ISO 8583 messages into the FISC wire format:
 * <pre>
 * +--------+--------+--------+------+
 * | Length |  MTI   | Bitmap | Data |
 * | 2 bytes| 4 bytes| 8/16 B | var  |
 * +--------+--------+--------+------+
 * </pre>
 */
@Slf4j
public class FiscMessageAssembler implements MessageAssembler {

    /** Length prefix size in bytes */
    private static final int LENGTH_PREFIX_SIZE = 2;

    /** MTI size in bytes (BCD encoded) */
    private static final int MTI_SIZE = 2;

    /** Whether to include length prefix when assembling */
    private final boolean includeLengthPrefix;

    /** Field codec for encoding/decoding fields */
    private final FieldCodec fieldCodec;

    /**
     * Creates an assembler with default settings (with length prefix).
     */
    public FiscMessageAssembler() {
        this(true);
    }

    /**
     * Creates an assembler with specified settings.
     *
     * @param includeLengthPrefix whether to include length prefix
     */
    public FiscMessageAssembler(boolean includeLengthPrefix) {
        this.includeLengthPrefix = includeLengthPrefix;
        this.fieldCodec = new DefaultFieldCodec();
    }

    @Override
    public void assemble(Iso8583Message message, ByteBuf buffer) {
        log.debug("Assembling ISO 8583 message: MTI={}", message.getMti());

        try {
            // Build the message body first (to calculate length)
            ByteBuf bodyBuffer = Unpooled.buffer(1024);

            // Write MTI
            writeMti(message.getMti(), bodyBuffer);

            // Build and write bitmap
            Bitmap bitmap = buildBitmap(message);
            bodyBuffer.writeBytes(bitmap.toBytes());
            log.trace("Assembled bitmap: {}", bitmap.toHex());

            // Write data fields
            writeFields(message, bitmap, bodyBuffer);

            // Now write the complete message with optional length prefix
            if (includeLengthPrefix) {
                int bodyLength = bodyBuffer.readableBytes();
                writeLengthPrefix(bodyLength, buffer);
                log.trace("Message length: {}", bodyLength);
            }

            // Copy body to output buffer
            buffer.writeBytes(bodyBuffer);
            bodyBuffer.release();

            log.info("Successfully assembled message: MTI={}, size={} bytes",
                message.getMti(), buffer.readableBytes());

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
     * Writes the 2-byte length prefix in BCD format.
     */
    private void writeLengthPrefix(int length, ByteBuf buffer) {
        if (length > 9999) {
            throw MessageException.assembleError("Message too long: " + length);
        }
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
                log.warn("Field {} in bitmap but no value, skipping", fieldNum);
                continue;
            }

            FieldDefinition definition = FiscFieldDefinitions.get(fieldNum);
            if (definition == null) {
                throw MessageException.fieldError(fieldNum, "No definition found");
            }

            try {
                fieldCodec.encode(definition, value, buffer);
                log.trace("Encoded field {}: {}",
                    fieldNum, definition.isSensitive() ? "****" : value);
            } catch (Exception e) {
                throw MessageException.fieldError(fieldNum,
                    "Failed to encode: " + e.getMessage());
            }
        }
    }
}
