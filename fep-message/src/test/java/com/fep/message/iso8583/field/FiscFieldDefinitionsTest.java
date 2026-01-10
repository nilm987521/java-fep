package com.fep.message.iso8583.field;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FiscFieldDefinitions Tests")
class FiscFieldDefinitionsTest {

    @BeforeEach
    void setUp() {
        // Clear cache before each test to ensure clean state
        FiscFieldDefinitions.clear();
        // Clear system property
        System.clearProperty(FiscFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        // Clean up after tests
        FiscFieldDefinitions.clear();
        System.clearProperty(FiscFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @Nested
    @DisplayName("Static access - get definition")
    class StaticGetDefinition {

        @Test
        @DisplayName("should return definition for valid field number")
        void shouldReturnDefinitionForValidField() {
            FieldDefinition definition = FiscFieldDefinitions.get(2);

            assertNotNull(definition);
            assertEquals(2, definition.getFieldNumber());
            assertEquals("Primary Account Number", definition.getName());
        }

        @Test
        @DisplayName("should return null for undefined field number")
        void shouldReturnNullForUndefinedField() {
            FieldDefinition definition = FiscFieldDefinitions.get(1);

            // Field 1 is the bitmap, not typically defined as a field
            assertNull(definition);
        }

        @Test
        @DisplayName("should return null for out of range field number")
        void shouldReturnNullForOutOfRangeField() {
            assertNull(FiscFieldDefinitions.get(0));
            assertNull(FiscFieldDefinitions.get(200));
            assertNull(FiscFieldDefinitions.get(-1));
        }
    }

    @Nested
    @DisplayName("Instance access")
    class InstanceAccess {

        @Test
        @DisplayName("should get instance")
        void shouldGetInstance() {
            FiscFieldDefinitions instance = FiscFieldDefinitions.getInstance();
            assertNotNull(instance);
        }

        @Test
        @DisplayName("should return same instance (singleton)")
        void shouldReturnSameInstance() {
            FiscFieldDefinitions instance1 = FiscFieldDefinitions.getInstance();
            FiscFieldDefinitions instance2 = FiscFieldDefinitions.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("should implement FieldDefinitionProvider")
        void shouldImplementInterface() {
            FieldDefinitionProvider provider = FiscFieldDefinitions.getInstance();
            assertNotNull(provider);
            assertEquals("FISC", provider.getProviderName());
        }

        @Test
        @DisplayName("should get definition via instance")
        void shouldGetDefinitionViaInstance() {
            FieldDefinitionProvider provider = FiscFieldDefinitions.getInstance();
            FieldDefinition definition = provider.getDefinition(2);

            assertNotNull(definition);
            assertEquals(2, definition.getFieldNumber());
        }
    }

    @Nested
    @DisplayName("Get all definitions")
    class GetAllDefinitions {

        @Test
        @DisplayName("should return all defined fields")
        void shouldReturnAllDefinedFields() {
            Map<Integer, FieldDefinition> allDefinitions = FiscFieldDefinitions.getAll();

            assertNotNull(allDefinitions);
            assertFalse(allDefinitions.isEmpty());
            assertTrue(allDefinitions.size() >= 100);
        }

        @Test
        @DisplayName("should return unmodifiable map")
        void shouldReturnUnmodifiableMap() {
            Map<Integer, FieldDefinition> allDefinitions = FiscFieldDefinitions.getAll();

            assertThrows(UnsupportedOperationException.class, () ->
                allDefinitions.put(999, null));
        }
    }

    @Nested
    @DisplayName("Is defined")
    class IsDefined {

        @Test
        @DisplayName("should return true for defined field")
        void shouldReturnTrueForDefinedField() {
            assertTrue(FiscFieldDefinitions.isFieldDefined(2));
            assertTrue(FiscFieldDefinitions.isFieldDefined(3));
            assertTrue(FiscFieldDefinitions.isFieldDefined(41));
        }

        @Test
        @DisplayName("should return false for undefined field")
        void shouldReturnFalseForUndefinedField() {
            assertFalse(FiscFieldDefinitions.isFieldDefined(1));
            assertFalse(FiscFieldDefinitions.isFieldDefined(200));
        }
    }

    @Nested
    @DisplayName("Get defined field count")
    class GetDefinedFieldCount {

        @Test
        @DisplayName("should return count of defined fields")
        void shouldReturnCountOfDefinedFields() {
            int count = FiscFieldDefinitions.getFieldCount();

            assertTrue(count >= 100, "Expected at least 100 defined fields, got " + count);
        }
    }

    @Nested
    @DisplayName("Load from custom file")
    class LoadFromCustomFile {

        @Test
        @DisplayName("should load from custom file path")
        void shouldLoadFromCustomFile(@TempDir Path tempDir) throws IOException {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Custom PAN,,NUMERIC,LLVAR,16,BCD,BCD,true,,
                3,Custom Code,,NUMERIC,FIXED,6,BCD,,,,,
                """;

            Path customFile = tempDir.resolve("custom-definitions.csv");
            Files.writeString(customFile, csvContent);

            FiscFieldDefinitions.loadFile(customFile);

            FieldDefinition pan = FiscFieldDefinitions.get(2);
            assertNotNull(pan);
            assertEquals("Custom PAN", pan.getName());
            assertEquals(16, pan.getLength());

            assertEquals(2, FiscFieldDefinitions.getFieldCount());
        }

        @Test
        @DisplayName("should load from system property path")
        void shouldLoadFromSystemProperty(@TempDir Path tempDir) throws IOException {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Property PAN,,NUMERIC,LLVAR,18,BCD,BCD,,,
                """;

            Path customFile = tempDir.resolve("property-definitions.csv");
            Files.writeString(customFile, csvContent);

            System.setProperty(FiscFieldDefinitions.CSV_PATH_PROPERTY, customFile.toString());
            FiscFieldDefinitions.clear();

            // This should trigger loading from system property path
            FieldDefinition pan = FiscFieldDefinitions.get(2);

            assertNotNull(pan);
            assertEquals("Property PAN", pan.getName());
            assertEquals(18, pan.getLength());
        }
    }

    @Nested
    @DisplayName("Reload")
    class Reload {

        @Test
        @DisplayName("should reload from default resource")
        void shouldReloadFromDefaultResource(@TempDir Path tempDir) throws IOException {
            // First load custom definitions
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Temporary PAN,,NUMERIC,LLVAR,10,BCD,BCD,,,
                """;

            Path customFile = tempDir.resolve("temp-definitions.csv");
            Files.writeString(customFile, csvContent);
            FiscFieldDefinitions.loadFile(customFile);

            assertEquals("Temporary PAN", FiscFieldDefinitions.get(2).getName());
            assertEquals(1, FiscFieldDefinitions.getFieldCount());

            // Reload from default
            FiscFieldDefinitions.reloadDefinitions();

            assertEquals("Primary Account Number", FiscFieldDefinitions.get(2).getName());
            assertTrue(FiscFieldDefinitions.getFieldCount() >= 100);
        }
    }

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafety {

        @Test
        @DisplayName("should handle concurrent access safely")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        FiscFieldDefinitions.get(2);
                        FiscFieldDefinitions.isFieldDefined(3);
                        FiscFieldDefinitions.getFieldCount();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Verify definitions are still valid
            assertNotNull(FiscFieldDefinitions.get(2));
            assertTrue(FiscFieldDefinitions.getFieldCount() >= 100);
        }
    }

    @Nested
    @DisplayName("FISC field definitions validation")
    class FiscDefinitionsValidation {

        @Test
        @DisplayName("should have all common FISC fields")
        void shouldHaveAllCommonFiscFields() {
            // Key fields that must be present
            int[] requiredFields = {2, 3, 4, 11, 12, 13, 14, 32, 35, 37, 38, 39, 41, 42, 49, 52, 55, 64, 128};

            for (int fieldNum : requiredFields) {
                assertTrue(FiscFieldDefinitions.isFieldDefined(fieldNum),
                    "Field " + fieldNum + " should be defined");
            }
        }

        @Test
        @DisplayName("Field 35 (Track 2) should be TRACK2 type with BCD encoding")
        void shouldHaveCorrectTrack2Definition() {
            FieldDefinition track2 = FiscFieldDefinitions.get(35);

            assertNotNull(track2);
            assertEquals(FieldType.TRACK2, track2.getFieldType());
            assertEquals(LengthType.LLVAR, track2.getLengthType());
            assertEquals(DataEncoding.BCD, track2.getDataEncoding());
            assertTrue(track2.isSensitive());
        }

        @Test
        @DisplayName("Field 55 (EMV Data) should be BINARY with LLLVAR")
        void shouldHaveCorrectEmvDataDefinition() {
            FieldDefinition emvData = FiscFieldDefinitions.get(55);

            assertNotNull(emvData);
            assertEquals(FieldType.BINARY, emvData.getFieldType());
            assertEquals(LengthType.LLLVAR, emvData.getLengthType());
            assertEquals(DataEncoding.BINARY, emvData.getDataEncoding());
            assertTrue(emvData.isSensitive());
        }

        @Test
        @DisplayName("Field 64 (MAC) should be BINARY FIXED 16")
        void shouldHaveCorrectMacDefinition() {
            FieldDefinition mac = FiscFieldDefinitions.get(64);

            assertNotNull(mac);
            assertEquals(FieldType.BINARY, mac.getFieldType());
            assertEquals(LengthType.FIXED, mac.getLengthType());
            assertEquals(16, mac.getLength());
            assertEquals(DataEncoding.BINARY, mac.getDataEncoding());
        }

        @Test
        @DisplayName("Field 128 (MAC Secondary) should be BINARY FIXED 16")
        void shouldHaveCorrectSecondaryMacDefinition() {
            FieldDefinition mac128 = FiscFieldDefinitions.get(128);

            assertNotNull(mac128);
            assertEquals(FieldType.BINARY, mac128.getFieldType());
            assertEquals(LengthType.FIXED, mac128.getLengthType());
            assertEquals(16, mac128.getLength());
        }
    }
}
