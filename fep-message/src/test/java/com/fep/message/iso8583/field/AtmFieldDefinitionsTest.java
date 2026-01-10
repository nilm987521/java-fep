package com.fep.message.iso8583.field;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AtmFieldDefinitions Tests")
class AtmFieldDefinitionsTest {

    @BeforeEach
    void setUp() {
        AtmFieldDefinitions.clear();
        System.clearProperty(AtmFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        AtmFieldDefinitions.clear();
        System.clearProperty(AtmFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("should get instance")
        void shouldGetInstance() {
            AtmFieldDefinitions instance = AtmFieldDefinitions.getInstance();
            assertNotNull(instance);
            assertEquals("ATM", instance.getProviderName());
        }

        @Test
        @DisplayName("should return same instance (singleton)")
        void shouldReturnSameInstance() {
            AtmFieldDefinitions instance1 = AtmFieldDefinitions.getInstance();
            AtmFieldDefinitions instance2 = AtmFieldDefinitions.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("should implement FieldDefinitionProvider")
        void shouldImplementInterface() {
            FieldDefinitionProvider provider = AtmFieldDefinitions.getInstance();
            assertNotNull(provider);
            assertTrue(provider instanceof AtmFieldDefinitions);
        }

        @Test
        @DisplayName("should load definitions from default resource")
        void shouldLoadFromDefaultResource() {
            int count = AtmFieldDefinitions.getFieldCount();
            assertTrue(count > 0, "Should have loaded some field definitions");
        }
    }

    @Nested
    @DisplayName("Field definitions validation")
    class FieldDefinitionsValidation {

        @Test
        @DisplayName("Field 2 (PAN) should use BCD encoding")
        void shouldHaveBcdPan() {
            FieldDefinition pan = AtmFieldDefinitions.get(2);

            assertNotNull(pan);
            assertEquals(DataEncoding.BCD, pan.getDataEncoding());
            assertTrue(pan.isSensitive());
        }

        @Test
        @DisplayName("Field 52 (PIN) should be 8 bytes")
        void shouldHaveShorterPinBlock() {
            FieldDefinition pin = AtmFieldDefinitions.get(52);

            assertNotNull(pin);
            assertEquals(FieldType.BINARY, pin.getFieldType());
            assertEquals(8, pin.getLength());
            assertTrue(pin.isSensitive());
        }

        @Test
        @DisplayName("Field 64 (MAC) should be 8 bytes")
        void shouldHaveShorterMac() {
            FieldDefinition mac = AtmFieldDefinitions.get(64);

            assertNotNull(mac);
            assertEquals(FieldType.BINARY, mac.getFieldType());
            assertEquals(8, mac.getLength());
        }

        @Test
        @DisplayName("should have ATM-specific fields (60-63)")
        void shouldHaveAtmSpecificFields() {
            // ATM device status
            assertTrue(AtmFieldDefinitions.isFieldDefined(60));
            // ATM transaction data
            assertTrue(AtmFieldDefinitions.isFieldDefined(61));
            // Dispensed amount
            assertTrue(AtmFieldDefinitions.isFieldDefined(62));
            // Notes dispensed
            assertTrue(AtmFieldDefinitions.isFieldDefined(63));
        }

        @Test
        @DisplayName("should have ATM journal fields (120-125)")
        void shouldHaveAtmJournalFields() {
            // Journal sequence
            assertTrue(AtmFieldDefinitions.isFieldDefined(120));
            // Hardware ID
            assertTrue(AtmFieldDefinitions.isFieldDefined(121));
            // Cassette status
            assertTrue(AtmFieldDefinitions.isFieldDefined(122));
            // Deposit data
            assertTrue(AtmFieldDefinitions.isFieldDefined(123));
            // Receipt data
            assertTrue(AtmFieldDefinitions.isFieldDefined(124));
            // Supervisor data
            assertTrue(AtmFieldDefinitions.isFieldDefined(125));
        }

        @Test
        @DisplayName("Field 60 (ATM Device Status) should exist")
        void shouldHaveDeviceStatusField() {
            FieldDefinition deviceStatus = AtmFieldDefinitions.get(60);

            assertNotNull(deviceStatus);
            assertEquals("ATM Device Status", deviceStatus.getName());
        }
    }

    @Nested
    @DisplayName("Comparison with FISC")
    class ComparisonWithFisc {

        @Test
        @DisplayName("should have same PAN encoding as FISC")
        void shouldHaveSamePanEncodingAsFisc() {
            FieldDefinition atmPan = AtmFieldDefinitions.get(2);
            FieldDefinition fiscPan = FiscFieldDefinitions.get(2);

            // Both use BCD encoding
            assertEquals(atmPan.getDataEncoding(), fiscPan.getDataEncoding());
        }

        @Test
        @DisplayName("should have shorter PIN block than FISC")
        void shouldHaveShorterPinBlock() {
            FieldDefinition atmPin = AtmFieldDefinitions.get(52);
            FieldDefinition fiscPin = FiscFieldDefinitions.get(52);

            // ATM uses 8 bytes, FISC uses 16 bytes
            assertEquals(8, atmPin.getLength());
            assertEquals(16, fiscPin.getLength());
        }
    }

    @Nested
    @DisplayName("Custom file loading")
    class CustomFileLoading {

        @Test
        @DisplayName("should load from custom file")
        void shouldLoadFromCustomFile(@TempDir Path tempDir) throws IOException {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Custom ATM PAN,,NUMERIC,LLVAR,16,BCD,BCD,true,,
                60,Custom Device Status,,ALPHA_NUMERIC_SPECIAL,LLLVAR,50,ASCII,BCD,,,
                """;

            Path customFile = tempDir.resolve("custom-atm.csv");
            Files.writeString(customFile, csvContent);

            AtmFieldDefinitions.loadFile(customFile);

            FieldDefinition pan = AtmFieldDefinitions.get(2);
            assertNotNull(pan);
            assertEquals("Custom ATM PAN", pan.getName());
            assertEquals(16, pan.getLength());

            FieldDefinition deviceStatus = AtmFieldDefinitions.get(60);
            assertNotNull(deviceStatus);
            assertEquals("Custom Device Status", deviceStatus.getName());
        }
    }
}
