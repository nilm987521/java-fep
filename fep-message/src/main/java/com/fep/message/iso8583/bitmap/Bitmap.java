package com.fep.message.iso8583.bitmap;

import com.fep.common.util.HexUtils;
import com.fep.message.exception.MessageException;
import lombok.extern.slf4j.Slf4j;

import java.util.BitSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * ISO 8583 Bitmap handler.
 *
 * <p>The bitmap in ISO 8583 indicates which data elements are present in the message.
 * The primary bitmap (8 bytes) covers fields 1-64.
 * If bit 1 is set, a secondary bitmap follows, covering fields 65-128.
 *
 * <p>Bitmap can be represented in two formats:
 * <ul>
 *   <li>Binary: 8 bytes (16 bytes with secondary)</li>
 *   <li>Hex: 16 characters (32 characters with secondary)</li>
 * </ul>
 */
@Slf4j
public class Bitmap {

    /** Primary bitmap size in bytes */
    public static final int PRIMARY_BITMAP_SIZE = 8;

    /** Total bitmap size in bytes (primary + secondary) */
    public static final int TOTAL_BITMAP_SIZE = 16;

    /** Number of fields covered by primary bitmap */
    public static final int PRIMARY_FIELD_COUNT = 64;

    /** Maximum field number */
    public static final int MAX_FIELD_NUMBER = 128;

    private final BitSet bitSet;

    /**
     * Creates an empty bitmap.
     */
    public Bitmap() {
        this.bitSet = new BitSet(MAX_FIELD_NUMBER);
    }

    /**
     * Creates a bitmap from a byte array.
     *
     * @param bytes the bitmap bytes (8 or 16 bytes)
     */
    public Bitmap(byte[] bytes) {
        this();
        if (bytes == null || (bytes.length != PRIMARY_BITMAP_SIZE && bytes.length != TOTAL_BITMAP_SIZE)) {
            throw MessageException.bitmapError(
                "Bitmap must be " + PRIMARY_BITMAP_SIZE + " or " + TOTAL_BITMAP_SIZE + " bytes");
        }
        parseBitmap(bytes);
    }

    /**
     * Creates a bitmap from a hexadecimal string.
     *
     * @param hex the bitmap in hex format (16 or 32 characters)
     */
    public static Bitmap fromHex(String hex) {
        if (hex == null || (hex.length() != PRIMARY_BITMAP_SIZE * 2 &&
                           hex.length() != TOTAL_BITMAP_SIZE * 2)) {
            throw MessageException.bitmapError(
                "Hex bitmap must be " + (PRIMARY_BITMAP_SIZE * 2) + " or " +
                (TOTAL_BITMAP_SIZE * 2) + " characters");
        }
        return new Bitmap(HexUtils.hexToBytes(hex));
    }

    /**
     * Creates a bitmap from a set of field numbers.
     *
     * @param fieldNumbers the set of field numbers to include
     * @return the bitmap
     */
    public static Bitmap fromFields(Set<Integer> fieldNumbers) {
        Bitmap bitmap = new Bitmap();
        for (Integer fieldNum : fieldNumbers) {
            bitmap.set(fieldNum);
        }
        return bitmap;
    }

    /**
     * Parses bitmap bytes into the internal BitSet.
     */
    private void parseBitmap(byte[] bytes) {
        int byteCount = bytes.length;
        for (int i = 0; i < byteCount; i++) {
            byte b = bytes[i];
            for (int bit = 0; bit < 8; bit++) {
                if ((b & (0x80 >> bit)) != 0) {
                    // Bit position: byte_index * 8 + bit_position + 1 (1-indexed)
                    int fieldNumber = i * 8 + bit + 1;
                    bitSet.set(fieldNumber);
                }
            }
        }
        log.trace("Parsed bitmap with fields: {}", getFields());
    }

    /**
     * Sets a field as present in the bitmap.
     *
     * @param fieldNumber the field number (1-128)
     */
    public void set(int fieldNumber) {
        validateFieldNumber(fieldNumber);
        bitSet.set(fieldNumber);

        // If setting a field > 64, also set bit 1 (secondary bitmap indicator)
        if (fieldNumber > PRIMARY_FIELD_COUNT) {
            bitSet.set(1);
        }
    }

    /**
     * Clears a field from the bitmap.
     *
     * @param fieldNumber the field number (1-128)
     */
    public void clear(int fieldNumber) {
        validateFieldNumber(fieldNumber);
        bitSet.clear(fieldNumber);

        // Check if we still need secondary bitmap
        if (fieldNumber > PRIMARY_FIELD_COUNT) {
            boolean hasSecondaryFields = bitSet.nextSetBit(PRIMARY_FIELD_COUNT + 1) > 0;
            if (!hasSecondaryFields) {
                bitSet.clear(1);
            }
        }
    }

    /**
     * Checks if a field is present in the bitmap.
     *
     * @param fieldNumber the field number (1-128)
     * @return true if the field is present
     */
    public boolean isSet(int fieldNumber) {
        validateFieldNumber(fieldNumber);
        return bitSet.get(fieldNumber);
    }

    /**
     * Checks if secondary bitmap is present.
     *
     * @return true if bit 1 is set (secondary bitmap present)
     */
    public boolean hasSecondaryBitmap() {
        return bitSet.get(1);
    }

    /**
     * Gets all set field numbers.
     *
     * @return sorted set of field numbers
     */
    public Set<Integer> getFields() {
        Set<Integer> fields = new TreeSet<>();
        for (int i = bitSet.nextSetBit(1); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            fields.add(i);
        }
        return fields;
    }

    /**
     * Gets the data fields (excluding bitmap indicator).
     * This excludes field 1 which indicates secondary bitmap presence.
     *
     * @return sorted set of data field numbers
     */
    public Set<Integer> getDataFields() {
        Set<Integer> fields = new TreeSet<>();
        for (int i = bitSet.nextSetBit(2); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            fields.add(i);
        }
        return fields;
    }

    /**
     * Converts the bitmap to a byte array.
     *
     * @return byte array (8 or 16 bytes depending on secondary bitmap)
     */
    public byte[] toBytes() {
        int length = hasSecondaryBitmap() ? TOTAL_BITMAP_SIZE : PRIMARY_BITMAP_SIZE;
        byte[] bytes = new byte[length];

        for (int i = 0; i < length; i++) {
            byte b = 0;
            for (int bit = 0; bit < 8; bit++) {
                int fieldNumber = i * 8 + bit + 1;
                if (bitSet.get(fieldNumber)) {
                    b |= (byte) (0x80 >> bit);
                }
            }
            bytes[i] = b;
        }
        return bytes;
    }

    /**
     * Converts the bitmap to a hexadecimal string.
     *
     * @return hex string representation
     */
    public String toHex() {
        return HexUtils.bytesToHex(toBytes());
    }

    /**
     * Gets the size of this bitmap in bytes.
     *
     * @return 8 for primary only, 16 with secondary
     */
    public int getSize() {
        return hasSecondaryBitmap() ? TOTAL_BITMAP_SIZE : PRIMARY_BITMAP_SIZE;
    }

    /**
     * Returns a binary string representation of the bitmap.
     * Useful for debugging.
     *
     * @return binary string with spaces every 8 bits
     */
    public String toBinaryString() {
        StringBuilder sb = new StringBuilder();
        int length = hasSecondaryBitmap() ? MAX_FIELD_NUMBER : PRIMARY_FIELD_COUNT;

        for (int i = 1; i <= length; i++) {
            sb.append(bitSet.get(i) ? '1' : '0');
            if (i % 8 == 0 && i < length) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private void validateFieldNumber(int fieldNumber) {
        if (fieldNumber < 1 || fieldNumber > MAX_FIELD_NUMBER) {
            throw MessageException.bitmapError(
                "Field number must be between 1 and " + MAX_FIELD_NUMBER + ": " + fieldNumber);
        }
    }

    @Override
    public String toString() {
        return String.format("Bitmap{hex=%s, fields=%s}", toHex(), getFields());
    }
}
