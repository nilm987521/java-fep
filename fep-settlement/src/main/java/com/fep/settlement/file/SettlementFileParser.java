package com.fep.settlement.file;

import com.fep.settlement.domain.SettlementFile;
import com.fep.settlement.domain.SettlementFileType;
import com.fep.settlement.domain.SettlementRecord;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for parsing settlement files from FISC.
 */
public interface SettlementFileParser {

    /**
     * Parse a settlement file from path.
     *
     * @param filePath path to the settlement file
     * @return parsed settlement file
     * @throws SettlementFileParseException if parsing fails
     */
    SettlementFile parse(Path filePath) throws SettlementFileParseException;

    /**
     * Parse a settlement file from input stream.
     *
     * @param inputStream input stream of the file
     * @param fileName original file name
     * @return parsed settlement file
     * @throws SettlementFileParseException if parsing fails
     */
    SettlementFile parse(InputStream inputStream, String fileName) throws SettlementFileParseException;

    /**
     * Parse a single record line.
     *
     * @param line the line to parse
     * @param lineNumber the line number in the file
     * @return parsed settlement record
     * @throws SettlementFileParseException if parsing fails
     */
    SettlementRecord parseRecord(String line, int lineNumber) throws SettlementFileParseException;

    /**
     * Parse file header.
     *
     * @param headerLine the header line
     * @return parsed header
     * @throws SettlementFileParseException if parsing fails
     */
    SettlementFile.FileHeader parseHeader(String headerLine) throws SettlementFileParseException;

    /**
     * Parse file trailer.
     *
     * @param trailerLine the trailer line
     * @return parsed trailer
     * @throws SettlementFileParseException if parsing fails
     */
    SettlementFile.FileTrailer parseTrailer(String trailerLine) throws SettlementFileParseException;

    /**
     * Get the file type this parser handles.
     *
     * @return settlement file type
     */
    SettlementFileType getFileType();

    /**
     * Validate file format before parsing.
     *
     * @param filePath path to the file
     * @return validation result
     */
    FileValidationResult validateFormat(Path filePath);

    /**
     * Get supported character encoding.
     *
     * @return character encoding name
     */
    default String getEncoding() {
        return "Big5"; // Default for Taiwan FISC files
    }

    /**
     * Get expected record length (for fixed-width files).
     *
     * @return record length, or -1 for variable length
     */
    default int getExpectedRecordLength() {
        return -1;
    }
}
