package com.fep.message.iso8583.parser;

import com.fep.common.util.HexUtils;
import com.fep.message.exception.MessageException;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import com.fep.message.iso8583.bitmap.Bitmap;
import com.fep.message.iso8583.codec.DefaultFieldCodec;
import com.fep.message.iso8583.codec.FieldCodec;
import com.fep.message.iso8583.field.FieldDefinition;
import com.fep.message.iso8583.field.FiscFieldDefinitions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Parser for FISC ISO 8583 messages.
 *
 * <p>Message structure (FISC format):
 * <pre>
 * +--------+--------+--------+------+
 * | Length |  MTI   | Bitmap | Data |
 * | 2 bytes| 4 bytes| 8/16 B | var  |
 * +--------+--------+--------+------+
 * </pre>
 *
 * <p>The length prefix indicates the total message length (excluding length prefix itself).
 * MTI is in BCD format (2 bytes for 4 digits).
 * Bitmap can be 8 bytes (primary) or 16 bytes (primary + secondary).
 */
@Slf4j
public class FiscMessageParser implements MessageParser {

    /** Length prefix size in bytes */
    private static final int LENGTH_PREFIX_SIZE = 2;

    /** MTI size in bytes (BCD encoded) */
    private static final int MTI_SIZE = 2;

    /** Whether to include length prefix when parsing */
    private final boolean hasLengthPrefix;

    /** Field codec for encoding/decoding fields */
    private final FieldCodec fieldCodec;

    /**
     * Creates a parser with default settings (with length prefix).
     */
    public FiscMessageParser() {
        this(true);
    }

    /**
     * Creates a parser with specified settings.
     *
     * @param hasLengthPrefix whether the message has a length prefix
     */
    public FiscMessageParser(boolean hasLengthPrefix) {
        this.hasLengthPrefix = hasLengthPrefix;
        this.fieldCodec = new DefaultFieldCodec();
    }

    @Override
    public Iso8583Message parse(ByteBuf buffer) {
        log.debug("Parsing ISO 8583 message, buffer size: {} bytes", buffer.readableBytes());

        // Save original bytes for logging
        byte[] rawData = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), rawData);

        try {
            // Skip length prefix if present
            if (hasLengthPrefix) {
                if (buffer.readableBytes() < LENGTH_PREFIX_SIZE) {
                    throw MessageException.parseError("Not enough bytes for length prefix");
                }
                int messageLength = readLengthPrefix(buffer);
                log.trace("Message length: {}", messageLength);
            }

            // Parse MTI
            String mti = parseMti(buffer);
            Iso8583Message message = new Iso8583Message(mti);
            message.setRawData(rawData);
            log.debug("Parsed MTI: {}", mti);

            // Parse bitmap
            Bitmap bitmap = parseBitmap(buffer);
            message.setPrimaryBitmap(bitmap.toBytes());
            log.debug("Parsed bitmap: {}", bitmap.toHex());

            // Parse data fields
            parseFields(buffer, bitmap, message);

            log.info("Successfully parsed message: MTI={}, fields={}",
                mti, message.getFieldNumbers().size());
            log.trace("Parsed message details:\n{}", message.toDetailString());

            return message;

        } catch (MessageException e) {
            throw e;
        } catch (Exception e) {
            throw MessageException.parseError("Unexpected error: " + e.getMessage(), e);
        }
    }

    @Override
    public Iso8583Message parse(byte[] data) {
        return parse(Unpooled.wrappedBuffer(data));
    }

    /**
     * Reads the 2-byte length prefix (BCD format).
     */
    private int readLengthPrefix(ByteBuf buffer) {
        byte[] lengthBytes = new byte[LENGTH_PREFIX_SIZE];
        buffer.readBytes(lengthBytes);
        String lengthStr = HexUtils.bcdToString(lengthBytes);
        return Integer.parseInt(lengthStr);
    }

    /**
     * Parses the MTI (Message Type Indicator).
     */
    private String parseMti(ByteBuf buffer) {
        if (buffer.readableBytes() < MTI_SIZE) {
            throw MessageException.parseError("Not enough bytes for MTI");
        }

        byte[] mtiBytes = new byte[MTI_SIZE];
        buffer.readBytes(mtiBytes);
        String mti = HexUtils.bcdToString(mtiBytes);

        // Validate MTI
        MessageType messageType = MessageType.fromCode(mti);
        if (messageType == null) {
            log.warn("Unknown MTI: {}", mti);
        }

        return mti;
    }

    /**
     * Parses the bitmap (primary and optionally secondary).
     */
    private Bitmap parseBitmap(ByteBuf buffer) {
        if (buffer.readableBytes() < Bitmap.PRIMARY_BITMAP_SIZE) {
            throw MessageException.parseError("Not enough bytes for primary bitmap");
        }

        // Read primary bitmap first
        byte[] primaryBytes = new byte[Bitmap.PRIMARY_BITMAP_SIZE];
        buffer.readBytes(primaryBytes);

        // Check if secondary bitmap is present (bit 1 set)
        boolean hasSecondary = (primaryBytes[0] & 0x80) != 0;

        byte[] bitmapBytes;
        if (hasSecondary) {
            if (buffer.readableBytes() < Bitmap.PRIMARY_BITMAP_SIZE) {
                throw MessageException.parseError("Not enough bytes for secondary bitmap");
            }
            bitmapBytes = new byte[Bitmap.TOTAL_BITMAP_SIZE];
            System.arraycopy(primaryBytes, 0, bitmapBytes, 0, Bitmap.PRIMARY_BITMAP_SIZE);
            buffer.readBytes(bitmapBytes, Bitmap.PRIMARY_BITMAP_SIZE, Bitmap.PRIMARY_BITMAP_SIZE);
        } else {
            bitmapBytes = primaryBytes;
        }

        return new Bitmap(bitmapBytes);
    }

    /**
     * Parses all data fields indicated by the bitmap.
     */
    private void parseFields(ByteBuf buffer, Bitmap bitmap, Iso8583Message message) {
        Set<Integer> fieldNumbers = bitmap.getDataFields();

        for (int fieldNum : fieldNumbers) {
            // Skip field 1 (secondary bitmap indicator)
            if (fieldNum == 1) {
                continue;
            }

            FieldDefinition definition = FiscFieldDefinitions.getDefinition(fieldNum);
            if (definition == null) {
                log.warn("No definition for field {}, skipping", fieldNum);
                continue;
            }

            try {
                Object value = fieldCodec.decode(definition, buffer);
                message.setField(fieldNum, value);
            } catch (Exception e) {
                throw MessageException.fieldError(fieldNum,
                    "Failed to decode: " + e.getMessage());
            }
        }
    }
}
