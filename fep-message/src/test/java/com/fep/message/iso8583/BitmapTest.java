package com.fep.message.iso8583;

import com.fep.message.iso8583.bitmap.Bitmap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bitmap class.
 */
@DisplayName("Bitmap Tests")
class BitmapTest {

    @Test
    @DisplayName("Should create empty bitmap")
    void shouldCreateEmptyBitmap() {
        Bitmap bitmap = new Bitmap();

        assertTrue(bitmap.getFields().isEmpty());
        assertFalse(bitmap.hasSecondaryBitmap());
        assertEquals(8, bitmap.getSize());
    }

    @Test
    @DisplayName("Should set and check individual bits")
    void shouldSetAndCheckBits() {
        Bitmap bitmap = new Bitmap();

        bitmap.set(3);
        bitmap.set(11);
        bitmap.set(39);

        assertTrue(bitmap.isSet(3));
        assertTrue(bitmap.isSet(11));
        assertTrue(bitmap.isSet(39));
        assertFalse(bitmap.isSet(2));
        assertFalse(bitmap.isSet(64));
    }

    @Test
    @DisplayName("Should automatically set bit 1 for secondary bitmap")
    void shouldSetBit1ForSecondaryBitmap() {
        Bitmap bitmap = new Bitmap();

        bitmap.set(70);

        assertTrue(bitmap.isSet(1));
        assertTrue(bitmap.hasSecondaryBitmap());
        assertEquals(16, bitmap.getSize());
    }

    @Test
    @DisplayName("Should clear bit 1 when no secondary fields")
    void shouldClearBit1WhenNoSecondaryFields() {
        Bitmap bitmap = new Bitmap();

        bitmap.set(70);
        assertTrue(bitmap.hasSecondaryBitmap());

        bitmap.clear(70);
        assertFalse(bitmap.hasSecondaryBitmap());
        assertFalse(bitmap.isSet(1));
    }

    @Test
    @DisplayName("Should parse bitmap from hex")
    void shouldParseBitmapFromHex() {
        // Hex: F0000000 00000000 = bits 1,2,3,4 set
        Bitmap bitmap = Bitmap.fromHex("F000000000000000");

        assertTrue(bitmap.isSet(1));
        assertTrue(bitmap.isSet(2));
        assertTrue(bitmap.isSet(3));
        assertTrue(bitmap.isSet(4));
        assertFalse(bitmap.isSet(5));
    }

    @Test
    @DisplayName("Should convert bitmap to hex")
    void shouldConvertBitmapToHex() {
        Bitmap bitmap = new Bitmap();
        bitmap.set(3);
        bitmap.set(4);

        String hex = bitmap.toHex();

        // Bit 3 and 4 set = 00110000 00000000 00000000 00000000 ... = 30 00 00 00 00 00 00 00
        assertEquals("3000000000000000", hex);
    }

    @Test
    @DisplayName("Should parse bitmap from bytes")
    void shouldParseBitmapFromBytes() {
        // Binary: 11100000 00000000 00000000 00000000 00000000 00000000 00000000 00000010
        byte[] bytes = new byte[] {
            (byte) 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02
        };

        Bitmap bitmap = new Bitmap(bytes);

        assertTrue(bitmap.isSet(1));
        assertTrue(bitmap.isSet(2));
        assertTrue(bitmap.isSet(3));
        assertTrue(bitmap.isSet(63));
        assertFalse(bitmap.isSet(4));
    }

    @Test
    @DisplayName("Should create bitmap from field set")
    void shouldCreateBitmapFromFieldSet() {
        Set<Integer> fields = Set.of(2, 3, 4, 11, 12, 37, 39);

        Bitmap bitmap = Bitmap.fromFields(fields);

        for (int field : fields) {
            assertTrue(bitmap.isSet(field), "Field " + field + " should be set");
        }
    }

    @Test
    @DisplayName("Should get all set fields")
    void shouldGetAllSetFields() {
        Bitmap bitmap = new Bitmap();
        bitmap.set(2);
        bitmap.set(3);
        bitmap.set(11);
        bitmap.set(39);

        Set<Integer> fields = bitmap.getFields();

        assertEquals(4, fields.size());
        assertTrue(fields.contains(2));
        assertTrue(fields.contains(3));
        assertTrue(fields.contains(11));
        assertTrue(fields.contains(39));
    }

    @Test
    @DisplayName("Should get data fields excluding bit 1")
    void shouldGetDataFieldsExcludingBit1() {
        Bitmap bitmap = new Bitmap();
        bitmap.set(2);
        bitmap.set(3);
        bitmap.set(70); // This will auto-set bit 1

        Set<Integer> dataFields = bitmap.getDataFields();

        assertTrue(bitmap.isSet(1));
        assertFalse(dataFields.contains(1));
        assertTrue(dataFields.contains(2));
        assertTrue(dataFields.contains(3));
        assertTrue(dataFields.contains(70));
    }

    @Test
    @DisplayName("Should produce correct binary string")
    void shouldProduceBinaryString() {
        Bitmap bitmap = new Bitmap();
        bitmap.set(2);
        bitmap.set(4);

        String binary = bitmap.toBinaryString();

        // Should be: 01010000 00000000 ... (64 bits total with spaces)
        assertTrue(binary.startsWith("0101"));
    }

    @Test
    @DisplayName("Should handle full secondary bitmap")
    void shouldHandleFullSecondaryBitmap() {
        // Hex with secondary bitmap indicator set
        String hex = "C000000000000000" + "4000000000000000";

        Bitmap bitmap = Bitmap.fromHex(hex);

        assertTrue(bitmap.isSet(1));    // Secondary bitmap indicator
        assertTrue(bitmap.isSet(2));    // Primary field
        assertTrue(bitmap.isSet(66));   // Secondary field
        assertEquals(16, bitmap.getSize());
    }

    @Test
    @DisplayName("Should reject invalid field numbers")
    void shouldRejectInvalidFieldNumbers() {
        Bitmap bitmap = new Bitmap();

        assertThrows(Exception.class, () -> bitmap.set(0));
        assertThrows(Exception.class, () -> bitmap.set(129));
        assertThrows(Exception.class, () -> bitmap.isSet(-1));
    }

    @Test
    @DisplayName("Should reject invalid hex length")
    void shouldRejectInvalidHexLength() {
        assertThrows(Exception.class, () -> Bitmap.fromHex("F000")); // Too short
        assertThrows(Exception.class, () -> Bitmap.fromHex("F00000000000000000")); // Invalid length
    }
}
