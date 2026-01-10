package com.fep.message.iso8583.field;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BankCoreFieldDefinitions Tests")
class BankCoreFieldDefinitionsTest {

    @BeforeEach
    void setUp() {
        BankCoreFieldDefinitions.clear();
        System.clearProperty(BankCoreFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        BankCoreFieldDefinitions.clear();
        System.clearProperty(BankCoreFieldDefinitions.CSV_PATH_PROPERTY);
    }

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("should get instance")
        void shouldGetInstance() {
            BankCoreFieldDefinitions instance = BankCoreFieldDefinitions.getInstance();
            assertNotNull(instance);
            assertEquals("BankCore", instance.getProviderName());
        }

        @Test
        @DisplayName("should return same instance (singleton)")
        void shouldReturnSameInstance() {
            BankCoreFieldDefinitions instance1 = BankCoreFieldDefinitions.getInstance();
            BankCoreFieldDefinitions instance2 = BankCoreFieldDefinitions.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("should implement FieldDefinitionProvider")
        void shouldImplementInterface() {
            FieldDefinitionProvider provider = BankCoreFieldDefinitions.getInstance();
            assertNotNull(provider);
            assertTrue(provider instanceof BankCoreFieldDefinitions);
        }

        @Test
        @DisplayName("should load definitions from default resource")
        void shouldLoadFromDefaultResource() {
            int count = BankCoreFieldDefinitions.getFieldCount();
            assertTrue(count > 0, "Should have loaded some field definitions");
        }
    }

    @Nested
    @DisplayName("Field definitions validation")
    class FieldDefinitionsValidation {

        @Test
        @DisplayName("Field 2 (PAN) should use ASCII encoding")
        void shouldHaveAsciiPan() {
            FieldDefinition pan = BankCoreFieldDefinitions.get(2);

            assertNotNull(pan);
            assertEquals(DataEncoding.ASCII, pan.getDataEncoding());
            assertEquals(DataEncoding.ASCII, pan.getLengthEncoding());
            assertTrue(pan.isSensitive());
        }

        @Test
        @DisplayName("Field 3 (Processing Code) should use ASCII encoding")
        void shouldHaveAsciiProcessingCode() {
            FieldDefinition pc = BankCoreFieldDefinitions.get(3);

            assertNotNull(pc);
            assertEquals(DataEncoding.ASCII, pc.getDataEncoding());
            assertEquals(LengthType.FIXED, pc.getLengthType());
            assertEquals(6, pc.getLength());
        }

        @Test
        @DisplayName("Field 64 (MAC) should be 8 bytes")
        void shouldHaveShorterMac() {
            FieldDefinition mac = BankCoreFieldDefinitions.get(64);

            assertNotNull(mac);
            assertEquals(FieldType.BINARY, mac.getFieldType());
            assertEquals(8, mac.getLength());
        }

        @Test
        @DisplayName("should have internal bank fields (120-127)")
        void shouldHaveInternalFields() {
            assertTrue(BankCoreFieldDefinitions.isFieldDefined(120));
            assertTrue(BankCoreFieldDefinitions.isFieldDefined(121));
            assertTrue(BankCoreFieldDefinitions.isFieldDefined(127));
        }

        @Test
        @DisplayName("Field 7 (Transmission DateTime) should be 14 chars (YYYYMMDDhhmmss)")
        void shouldHaveExtendedDateTime() {
            FieldDefinition dt = BankCoreFieldDefinitions.get(7);

            assertNotNull(dt);
            assertEquals(14, dt.getLength());
            assertEquals(DataEncoding.ASCII, dt.getDataEncoding());
        }
    }

    @Nested
    @DisplayName("Differences from FISC")
    class DifferencesFromFisc {

        @Test
        @DisplayName("should have different encoding than FISC")
        void shouldHaveDifferentEncoding() {
            FieldDefinition bankCorePan = BankCoreFieldDefinitions.get(2);
            FieldDefinition fiscPan = FiscFieldDefinitions.get(2);

            // BankCore uses ASCII, FISC uses BCD
            assertEquals(DataEncoding.ASCII, bankCorePan.getDataEncoding());
            assertEquals(DataEncoding.BCD, fiscPan.getDataEncoding());
        }

        @Test
        @DisplayName("should have different MAC length than FISC")
        void shouldHaveDifferentMacLength() {
            FieldDefinition bankCoreMac = BankCoreFieldDefinitions.get(64);
            FieldDefinition fiscMac = FiscFieldDefinitions.get(64);

            // BankCore uses 8 bytes, FISC uses 16 bytes
            assertEquals(8, bankCoreMac.getLength());
            assertEquals(16, fiscMac.getLength());
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
                2,Custom Core PAN,,NUMERIC,LLVAR,20,ASCII,ASCII,true,,
                """;

            Path customFile = tempDir.resolve("custom-bankcore.csv");
            Files.writeString(customFile, csvContent);

            BankCoreFieldDefinitions.loadFile(customFile);

            FieldDefinition pan = BankCoreFieldDefinitions.get(2);
            assertNotNull(pan);
            assertEquals("Custom Core PAN", pan.getName());
            assertEquals(20, pan.getLength());
        }
    }
}
