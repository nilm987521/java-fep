package com.fep.message.iso8583.field;

import com.fep.message.exception.MessageException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for ISO 8583 field definitions from CSV files.
 *
 * <p>CSV Format:
 * <pre>
 * fieldNumber,name,description,fieldType,lengthType,length,dataEncoding,lengthEncoding,sensitive,paddingChar,leftPadding
 * 2,Primary Account Number,PAN,NUMERIC,LLVAR,19,BCD,BCD,true,,
 * 3,Processing Code,,NUMERIC,FIXED,6,BCD,,,,,
 * </pre>
 *
 * <p>Lines starting with # are treated as comments.
 * Empty lines are ignored.
 */
@Slf4j
public class CsvFieldDefinitionLoader {

    private static final String DEFAULT_RESOURCE_PATH = "/fisc-field-definitions.csv";
    private static final int EXPECTED_COLUMNS = 11;

    /**
     * Loads field definitions from the default resource file.
     *
     * @return map of field number to field definition
     */
    public Map<Integer, FieldDefinition> loadFromResource() {
        return loadFromResource(DEFAULT_RESOURCE_PATH);
    }

    /**
     * Loads field definitions from a resource file.
     *
     * @param resourcePath the classpath resource path
     * @return map of field number to field definition
     */
    public Map<Integer, FieldDefinition> loadFromResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw MessageException.parseError("Resource not found: " + resourcePath);
            }
            return load(new InputStreamReader(is, StandardCharsets.UTF_8), resourcePath);
        } catch (IOException e) {
            throw MessageException.parseError("Failed to load field definitions from resource: " + resourcePath, e);
        }
    }

    /**
     * Loads field definitions from a file path.
     *
     * @param filePath the file path
     * @return map of field number to field definition
     */
    public Map<Integer, FieldDefinition> loadFromFile(Path filePath) {
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            return load(reader, filePath.toString());
        } catch (IOException e) {
            throw MessageException.parseError("Failed to load field definitions from file: " + filePath, e);
        }
    }

    /**
     * Loads field definitions from a file path string.
     *
     * @param filePath the file path
     * @return map of field number to field definition
     */
    public Map<Integer, FieldDefinition> loadFromFile(String filePath) {
        return loadFromFile(Path.of(filePath));
    }

    /**
     * Loads field definitions from a reader.
     *
     * @param reader the reader
     * @param sourceName source name for error messages
     * @return map of field number to field definition
     */
    public Map<Integer, FieldDefinition> load(Reader reader, String sourceName) {
        Map<Integer, FieldDefinition> definitions = new HashMap<>();
        int lineNumber = 0;
        boolean headerSkipped = false;

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNumber++;

                // Skip empty lines and comments
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Skip header row
                if (!headerSkipped && line.toLowerCase().startsWith("fieldnumber")) {
                    headerSkipped = true;
                    continue;
                }

                try {
                    FieldDefinition definition = parseLine(line, lineNumber);
                    if (definition != null) {
                        if (definitions.containsKey(definition.getFieldNumber())) {
                            log.warn("Duplicate field definition for field {} at line {}, using latest",
                                definition.getFieldNumber(), lineNumber);
                        }
                        definitions.put(definition.getFieldNumber(), definition);
                    }
                } catch (Exception e) {
                    log.error("Error parsing line {} in {}: {}", lineNumber, sourceName, e.getMessage());
                    throw MessageException.parseError(
                        "Error parsing field definition at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw MessageException.parseError("Error reading field definitions", e);
        }

        log.info("Loaded {} field definitions from {}", definitions.size(), sourceName);
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * Parses a single CSV line into a FieldDefinition.
     *
     * @param line the CSV line
     * @param lineNumber the line number for error reporting
     * @return the field definition, or null if line is invalid
     */
    private FieldDefinition parseLine(String line, int lineNumber) {
        String[] parts = parseCSVLine(line);

        if (parts.length < 6) {
            throw new IllegalArgumentException(
                "Invalid CSV format: expected at least 6 columns, got " + parts.length);
        }

        int fieldNumber = parseInteger(parts[0], "fieldNumber", lineNumber);
        String name = parts[1].trim();
        String description = parts.length > 2 ? parts[2].trim() : "";
        FieldType fieldType = parseFieldType(parts[3], lineNumber);
        LengthType lengthType = parseLengthType(parts[4], lineNumber);
        int length = parseInteger(parts[5], "length", lineNumber);

        // Optional fields with defaults
        DataEncoding dataEncoding = parts.length > 6 && !parts[6].trim().isEmpty()
            ? parseDataEncoding(parts[6], lineNumber)
            : DataEncoding.ASCII;

        DataEncoding lengthEncoding = parts.length > 7 && !parts[7].trim().isEmpty()
            ? parseDataEncoding(parts[7], lineNumber)
            : DataEncoding.BCD;

        boolean sensitive = parts.length > 8 && !parts[8].trim().isEmpty()
            && Boolean.parseBoolean(parts[8].trim());

        Character paddingChar = parts.length > 9 && !parts[9].trim().isEmpty()
            ? parts[9].trim().charAt(0)
            : null;

        boolean leftPadding = parts.length <= 10 || parts[10].trim().isEmpty()
            || Boolean.parseBoolean(parts[10].trim());

        return FieldDefinition.builder()
            .fieldNumber(fieldNumber)
            .name(name)
            .description(description.isEmpty() ? null : description)
            .fieldType(fieldType)
            .lengthType(lengthType)
            .length(length)
            .dataEncoding(dataEncoding)
            .lengthEncoding(lengthEncoding)
            .sensitive(sensitive)
            .paddingChar(paddingChar)
            .leftPadding(leftPadding)
            .build();
    }

    /**
     * Parses a CSV line handling quoted fields.
     */
    private String[] parseCSVLine(String line) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        return parts.toArray(new String[0]);
    }

    private int parseInteger(String value, String fieldName, int lineNumber) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid " + fieldName + " value '" + value + "' at line " + lineNumber);
        }
    }

    private FieldType parseFieldType(String value, int lineNumber) {
        try {
            return FieldType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid fieldType '" + value + "' at line " + lineNumber +
                ". Valid values: " + java.util.Arrays.toString(FieldType.values()));
        }
    }

    private LengthType parseLengthType(String value, int lineNumber) {
        try {
            return LengthType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid lengthType '" + value + "' at line " + lineNumber +
                ". Valid values: " + java.util.Arrays.toString(LengthType.values()));
        }
    }

    private DataEncoding parseDataEncoding(String value, int lineNumber) {
        try {
            return DataEncoding.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid dataEncoding '" + value + "' at line " + lineNumber +
                ". Valid values: " + java.util.Arrays.toString(DataEncoding.values()));
        }
    }
}
