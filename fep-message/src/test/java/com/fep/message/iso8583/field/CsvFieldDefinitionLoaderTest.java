package com.fep.message.iso8583.field;

import com.fep.message.exception.MessageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvFieldDefinitionLoader Tests")
class CsvFieldDefinitionLoaderTest {

    private CsvFieldDefinitionLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CsvFieldDefinitionLoader();
    }

    @Nested
    @DisplayName("Load from default resource")
    class LoadFromDefaultResource {

        @Test
        @DisplayName("should load default field definitions successfully")
        void shouldLoadDefaultDefinitions() {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource();

            assertNotNull(definitions);
            assertFalse(definitions.isEmpty());
            // FISC has at least 100+ field definitions
            assertTrue(definitions.size() >= 100,
                "Expected at least 100 field definitions, got " + definitions.size());
        }

        @Test
        @DisplayName("should have correct Field 2 (PAN) definition")
        void shouldHaveCorrectPanDefinition() {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource();

            FieldDefinition pan = definitions.get(2);
            assertNotNull(pan, "Field 2 (PAN) should be defined");
            assertEquals(2, pan.getFieldNumber());
            assertEquals("Primary Account Number", pan.getName());
            assertEquals(FieldType.NUMERIC, pan.getFieldType());
            assertEquals(LengthType.LLVAR, pan.getLengthType());
            assertEquals(19, pan.getLength());
            assertEquals(DataEncoding.BCD, pan.getDataEncoding());
            assertTrue(pan.isSensitive());
        }

        @Test
        @DisplayName("should have correct Field 3 (Processing Code) definition")
        void shouldHaveCorrectProcessingCodeDefinition() {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource();

            FieldDefinition processingCode = definitions.get(3);
            assertNotNull(processingCode, "Field 3 should be defined");
            assertEquals(3, processingCode.getFieldNumber());
            assertEquals("Processing Code", processingCode.getName());
            assertEquals(FieldType.NUMERIC, processingCode.getFieldType());
            assertEquals(LengthType.FIXED, processingCode.getLengthType());
            assertEquals(6, processingCode.getLength());
            assertEquals(DataEncoding.BCD, processingCode.getDataEncoding());
        }

        @Test
        @DisplayName("should have correct Field 41 (Terminal ID) definition")
        void shouldHaveCorrectTerminalIdDefinition() {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource();

            FieldDefinition terminalId = definitions.get(41);
            assertNotNull(terminalId, "Field 41 should be defined");
            assertEquals(8, terminalId.getLength());
            assertEquals(FieldType.ALPHA_NUMERIC_SPECIAL, terminalId.getFieldType());
            assertEquals(LengthType.FIXED, terminalId.getLengthType());
            assertEquals(DataEncoding.ASCII, terminalId.getDataEncoding());
        }

        @Test
        @DisplayName("should have correct Field 52 (PIN Data) definition")
        void shouldHaveCorrectPinDataDefinition() {
            Map<Integer, FieldDefinition> definitions = loader.loadFromResource();

            FieldDefinition pinData = definitions.get(52);
            assertNotNull(pinData, "Field 52 should be defined");
            assertEquals(FieldType.BINARY, pinData.getFieldType());
            assertEquals(16, pinData.getLength());
            assertTrue(pinData.isSensitive());
        }
    }

    @Nested
    @DisplayName("Load from custom resource")
    class LoadFromCustomResource {

        @Test
        @DisplayName("should throw exception for non-existent resource")
        void shouldThrowForNonExistentResource() {
            assertThrows(MessageException.class, () ->
                loader.loadFromResource("/non-existent-file.csv"));
        }
    }

    @Nested
    @DisplayName("Load from file")
    class LoadFromFile {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should load definitions from custom file")
        void shouldLoadFromFile() throws Exception {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Test PAN,Test description,NUMERIC,LLVAR,19,BCD,BCD,true,,
                3,Test Code,,NUMERIC,FIXED,6,BCD,,,,,
                4,Test Amount,,NUMERIC,FIXED,12,ASCII,,,0,true
                """;

            Path csvFile = tempDir.resolve("test-definitions.csv");
            Files.writeString(csvFile, csvContent);

            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(csvFile);

            assertEquals(3, definitions.size());

            FieldDefinition field2 = definitions.get(2);
            assertEquals("Test PAN", field2.getName());
            assertEquals("Test description", field2.getDescription());
            assertTrue(field2.isSensitive());

            FieldDefinition field4 = definitions.get(4);
            assertEquals(DataEncoding.ASCII, field4.getDataEncoding());
            assertEquals(Character.valueOf('0'), field4.getPaddingChar());
            assertTrue(field4.isLeftPadding());
        }

        @Test
        @DisplayName("should throw exception for non-existent file")
        void shouldThrowForNonExistentFile() {
            Path nonExistent = tempDir.resolve("non-existent.csv");
            assertThrows(MessageException.class, () -> loader.loadFromFile(nonExistent));
        }

        @Test
        @DisplayName("should skip comment lines")
        void shouldSkipCommentLines() throws Exception {
            String csvContent = """
                # This is a comment
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                # Another comment
                2,Test PAN,,NUMERIC,LLVAR,19,BCD,BCD,,,
                # Field 3 is commented out
                # 3,Processing Code,,NUMERIC,FIXED,6,BCD,,,,,
                4,Amount,,NUMERIC,FIXED,12,BCD,,,,,
                """;

            Path csvFile = tempDir.resolve("test-with-comments.csv");
            Files.writeString(csvFile, csvContent);

            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(csvFile);

            assertEquals(2, definitions.size());
            assertTrue(definitions.containsKey(2));
            assertTrue(definitions.containsKey(4));
            assertFalse(definitions.containsKey(3));
        }

        @Test
        @DisplayName("should skip empty lines")
        void shouldSkipEmptyLines() throws Exception {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding

                2,Test PAN,,NUMERIC,LLVAR,19,BCD,BCD,,,


                3,Processing Code,,NUMERIC,FIXED,6,BCD,,,,,

                """;

            Path csvFile = tempDir.resolve("test-with-empty-lines.csv");
            Files.writeString(csvFile, csvContent);

            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(csvFile);

            assertEquals(2, definitions.size());
        }

        @Test
        @DisplayName("should handle quoted fields with commas")
        void shouldHandleQuotedFields() throws Exception {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                43,"Card Acceptor Name","Name, Location, and Country",ALPHA_NUMERIC_SPECIAL,FIXED,40,ASCII,,,,,
                """;

            Path csvFile = tempDir.resolve("test-quoted.csv");
            Files.writeString(csvFile, csvContent);

            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(csvFile);

            FieldDefinition field43 = definitions.get(43);
            assertNotNull(field43);
            assertEquals("Card Acceptor Name", field43.getName());
            assertEquals("Name, Location, and Country", field43.getDescription());
        }

        @Test
        @DisplayName("should use default values for optional fields")
        void shouldUseDefaultsForOptionalFields() throws Exception {
            // Minimal CSV with only required fields
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length
                3,Processing Code,,NUMERIC,FIXED,6
                """;

            Path csvFile = tempDir.resolve("test-minimal.csv");
            Files.writeString(csvFile, csvContent);

            Map<Integer, FieldDefinition> definitions = loader.loadFromFile(csvFile);

            FieldDefinition field3 = definitions.get(3);
            assertNotNull(field3);
            assertEquals(DataEncoding.ASCII, field3.getDataEncoding()); // default
            assertEquals(DataEncoding.BCD, field3.getLengthEncoding()); // default
            assertFalse(field3.isSensitive()); // default
            assertNull(field3.getPaddingChar()); // default
            assertTrue(field3.isLeftPadding()); // default
        }
    }

    @Nested
    @DisplayName("Load from Reader")
    class LoadFromReader {

        @Test
        @DisplayName("should load from StringReader")
        void shouldLoadFromStringReader() {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                11,STAN,System Trace Audit Number,NUMERIC,FIXED,6,BCD,,,,,
                12,Local Time,hhmmss,NUMERIC,FIXED,6,BCD,,,,,
                """;

            Map<Integer, FieldDefinition> definitions =
                loader.load(new StringReader(csvContent), "test-string");

            assertEquals(2, definitions.size());
            assertEquals("STAN", definitions.get(11).getName());
            assertEquals("Local Time", definitions.get(12).getName());
        }

        @Test
        @DisplayName("should warn on duplicate field numbers")
        void shouldWarnOnDuplicateFieldNumbers() {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,First PAN,,NUMERIC,LLVAR,19,BCD,BCD,,,
                2,Second PAN,,NUMERIC,LLVAR,16,BCD,BCD,,,
                """;

            Map<Integer, FieldDefinition> definitions =
                loader.load(new StringReader(csvContent), "test-duplicates");

            // Latest definition wins
            assertEquals(1, definitions.size());
            assertEquals("Second PAN", definitions.get(2).getName());
            assertEquals(16, definitions.get(2).getLength());
        }

        @Test
        @DisplayName("should throw exception for invalid field type")
        void shouldThrowForInvalidFieldType() {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Test,,INVALID_TYPE,LLVAR,19,BCD,BCD,,,
                """;

            assertThrows(MessageException.class, () ->
                loader.load(new StringReader(csvContent), "test-invalid-type"));
        }

        @Test
        @DisplayName("should throw exception for invalid length type")
        void shouldThrowForInvalidLengthType() {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                2,Test,,NUMERIC,INVALID_LENGTH,19,BCD,BCD,,,
                """;

            assertThrows(MessageException.class, () ->
                loader.load(new StringReader(csvContent), "test-invalid-length-type"));
        }

        @Test
        @DisplayName("should throw exception for missing required columns")
        void shouldThrowForMissingColumns() {
            String csvContent = """
                fieldNumber,name,description
                2,Test,Description
                """;

            assertThrows(MessageException.class, () ->
                loader.load(new StringReader(csvContent), "test-missing-columns"));
        }

        @Test
        @DisplayName("should throw exception for non-numeric field number")
        void shouldThrowForNonNumericFieldNumber() {
            String csvContent = """
                fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
                abc,Test,,NUMERIC,FIXED,6,BCD,,,,,
                """;

            assertThrows(MessageException.class, () ->
                loader.load(new StringReader(csvContent), "test-invalid-number"));
        }
    }

    @Nested
    @DisplayName("All field types supported")
    class AllFieldTypesSupported {

        @Test
        @DisplayName("should support all FieldType values")
        void shouldSupportAllFieldTypes() {
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding\n");

            int fieldNum = 1;
            for (FieldType type : FieldType.values()) {
                csvContent.append(String.format("%d,Test %s,,%s,FIXED,10,ASCII,,,,,%n",
                    fieldNum++, type.name(), type.name()));
            }

            Map<Integer, FieldDefinition> definitions =
                loader.load(new StringReader(csvContent.toString()), "test-all-types");

            assertEquals(FieldType.values().length, definitions.size());
        }

        @Test
        @DisplayName("should support all LengthType values")
        void shouldSupportAllLengthTypes() {
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding\n");

            int fieldNum = 1;
            for (LengthType type : LengthType.values()) {
                csvContent.append(String.format("%d,Test %s,,NUMERIC,%s,10,ASCII,,,,,%n",
                    fieldNum++, type.name(), type.name()));
            }

            Map<Integer, FieldDefinition> definitions =
                loader.load(new StringReader(csvContent.toString()), "test-all-length-types");

            assertEquals(LengthType.values().length, definitions.size());
        }

        @Test
        @DisplayName("should support all DataEncoding values")
        void shouldSupportAllDataEncodings() {
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding\n");

            int fieldNum = 1;
            for (DataEncoding encoding : DataEncoding.values()) {
                csvContent.append(String.format("%d,Test %s,,NUMERIC,FIXED,10,%s,,,,,%n",
                    fieldNum++, encoding.name(), encoding.name()));
            }

            Map<Integer, FieldDefinition> definitions =
                loader.load(new StringReader(csvContent.toString()), "test-all-encodings");

            assertEquals(DataEncoding.values().length, definitions.size());
        }
    }
}
