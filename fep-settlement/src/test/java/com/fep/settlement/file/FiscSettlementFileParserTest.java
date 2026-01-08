package com.fep.settlement.file;

import com.fep.settlement.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FiscSettlementFileParser Tests")
class FiscSettlementFileParserTest {

    private FiscSettlementFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FiscSettlementFileParser();
    }

    @Nested
    @DisplayName("Parse Header")
    class ParseHeaderTests {

        @Test
        @DisplayName("Should parse valid header")
        void shouldParseValidHeader() {
            String headerLine = "HSF123456001202601071234567890123401";

            SettlementFile.FileHeader header = parser.parseHeader(headerLine);

            assertNotNull(header);
            assertEquals("SF123456", header.getFileId().trim());
            assertEquals("001", header.getVersion());
            assertEquals(LocalDate.of(2026, 1, 7), header.getCreationDate());
        }

        @Test
        @DisplayName("Should throw exception for invalid header type")
        void shouldThrowForInvalidHeaderType() {
            String invalidHeader = "DSF123456001202601071234567890123401";

            assertThrows(SettlementFileParseException.class, () ->
                parser.parseHeader(invalidHeader)
            );
        }
    }

    @Nested
    @DisplayName("Parse Record")
    class ParseRecordTests {

        @Test
        @DisplayName("Should parse valid detail record")
        void shouldParseValidDetailRecord() {
            // Build a detail record with proper field positions
            StringBuilder record = new StringBuilder();
            record.append("D");                       // 1: Record type
            record.append("20260107");                // 2-9: Date
            record.append("TXN123456789");            // 10-21: Reference (12)
            record.append("123456");                  // 22-27: STAN (6)
            record.append("RRN123456789");            // 28-39: RRN (12)
            record.append("0100");                    // 40-43: Txn type (4)
            record.append("1234567");                 // 44-50: Acquiring bank (7)
            record.append("7654321");                 // 51-57: Issuing bank (7)
            record.append("4111111111111111");        // 58-73: Card number (16)
            record.append("000000100000");            // 74-85: Amount (12) = 1000.00
            record.append("TWD");                     // 86-88: Currency (3)
            record.append("000000001000");            // 89-100: Fee (12) = 10.00
            record.append("ATM12345");                // 101-108: Terminal ID (8)
            record.append("MERCHANT1234567");         // 109-123: Merchant ID (15)
            record.append("AUTH01");                  // 124-129: Auth code (6)
            record.append("00");                      // 130-131: Response code (2)
            record.append("N");                       // 132: Reversal flag (1)
            record.append("            ");            // 133-144: Original ref (12)
            record.append("ATM   ");                  // 145-150: Channel (6)

            SettlementRecord result = parser.parseRecord(record.toString(), 1);

            assertNotNull(result);
            assertEquals("TXN123456789", result.getTransactionRefNo());
            assertEquals("123456", result.getStan());
            assertEquals("RRN123456789", result.getRrn());
            assertEquals("0100", result.getTransactionType());
            assertEquals(new BigDecimal("1000.00"), result.getAmount());
            assertEquals(new BigDecimal("10.00"), result.getFeeAmount());
            assertEquals("TWD", result.getCurrencyCode());
            assertFalse(result.isReversal());
            assertEquals(SettlementStatus.PENDING, result.getStatus());
        }

        @Test
        @DisplayName("Should parse reversal record")
        void shouldParseReversalRecord() {
            StringBuilder record = new StringBuilder();
            record.append("D");
            record.append("20260107");
            record.append("TXN123456789");
            record.append("123456");
            record.append("RRN123456789");
            record.append("0400");                    // Reversal type
            record.append("1234567");
            record.append("7654321");
            record.append("4111111111111111");
            record.append("000000100000");
            record.append("TWD");
            record.append("000000000000");
            record.append("ATM12345");
            record.append("MERCHANT1234567");
            record.append("AUTH01");
            record.append("00");
            record.append("Y");                       // Reversal flag = Y
            record.append("ORIG12345678");
            record.append("ATM   ");

            SettlementRecord result = parser.parseRecord(record.toString(), 1);

            assertTrue(result.isReversal());
            assertEquals("ORIG12345678", result.getOriginalTransactionRef());
        }

        @Test
        @DisplayName("Should calculate net amount")
        void shouldCalculateNetAmount() {
            StringBuilder record = new StringBuilder();
            record.append("D");
            record.append("20260107");
            record.append("TXN123456789");
            record.append("123456");
            record.append("RRN123456789");
            record.append("0100");
            record.append("1234567");
            record.append("7654321");
            record.append("4111111111111111");
            record.append("000000100000");            // 1000.00
            record.append("TWD");
            record.append("000000001000");            // 10.00 fee
            record.append("ATM12345");
            record.append("MERCHANT1234567");
            record.append("AUTH01");
            record.append("00");
            record.append("N");
            record.append("            ");
            record.append("ATM   ");

            SettlementRecord result = parser.parseRecord(record.toString(), 1);

            assertEquals(new BigDecimal("990.00"), result.getNetAmount());
        }
    }

    @Nested
    @DisplayName("Parse Trailer")
    class ParseTrailerTests {

        @Test
        @DisplayName("Should parse valid trailer")
        void shouldParseValidTrailer() {
            StringBuilder trailer = new StringBuilder();
            trailer.append("T");                      // Record type
            trailer.append("00000100");               // Record count (8) = 100
            trailer.append("0000000010000000");       // Total amount (16) = 100000.00
            trailer.append("0000000005000000");       // Debit amount (16) = 50000.00
            trailer.append("0000000005000000");       // Credit amount (16) = 50000.00
            trailer.append("00000050");               // Debit count (8) = 50
            trailer.append("00000050");               // Credit count (8) = 50
            trailer.append("1234567890123456789012345678901234567890");  // Checksum

            SettlementFile.FileTrailer result = parser.parseTrailer(trailer.toString());

            assertNotNull(result);
            assertEquals(100, result.getRecordCount());
            assertEquals(new BigDecimal("100000.00"), result.getTotalAmount());
            assertEquals(50, result.getDebitCount());
            assertEquals(50, result.getCreditCount());
        }
    }

    @Nested
    @DisplayName("Parse Complete File")
    class ParseFileTests {

        @Test
        @DisplayName("Should parse complete settlement file")
        void shouldParseCompleteFile() {
            StringBuilder file = new StringBuilder();

            // Header
            file.append("HSF123456001202601071234567890123401\n");

            // Detail record
            file.append("D20260107TXN123456789123456RRN12345678901001234567765432141111111111111110000001000001TWD000000001000ATM12345MERCHANT1234567AUTH010" +
                    "0N            ATM   \n");

            // Trailer
            file.append("T00000001000000000100000000000000050000000000000050000000010000000000000000000000000000");

            InputStream is = new ByteArrayInputStream(
                    file.toString().getBytes(Charset.forName("Big5"))
            );

            SettlementFile result = parser.parse(is, "test.txt");

            assertNotNull(result);
            assertNotNull(result.getFileId());
            assertNotNull(result.getHeader());
            assertNotNull(result.getTrailer());
            assertEquals(1, result.getTotalRecordCount());
            assertEquals("test.txt", result.getFileName());
            assertEquals(SettlementFileType.DAILY_SETTLEMENT, result.getFileType());
        }

        @Test
        @DisplayName("Should throw exception for empty file")
        void shouldThrowForEmptyFile() {
            InputStream is = new ByteArrayInputStream(new byte[0]);

            assertThrows(SettlementFileParseException.class, () ->
                parser.parse(is, "empty.txt")
            );
        }

        @Test
        @DisplayName("Should throw exception for missing header")
        void shouldThrowForMissingHeader() {
            String fileContent = "D20260107TXN123456789123456RRN1234567890100\n" +
                                 "T00000001000000000100000";

            InputStream is = new ByteArrayInputStream(fileContent.getBytes());

            assertThrows(SettlementFileParseException.class, () ->
                parser.parse(is, "no_header.txt")
            );
        }
    }

    @Nested
    @DisplayName("File Type")
    class FileTypeTests {

        @Test
        @DisplayName("Should return correct file type")
        void shouldReturnCorrectFileType() {
            assertEquals(SettlementFileType.DAILY_SETTLEMENT, parser.getFileType());

            FiscSettlementFileParser atmParser = new FiscSettlementFileParser(SettlementFileType.ATM_TRANSACTION);
            assertEquals(SettlementFileType.ATM_TRANSACTION, atmParser.getFileType());
        }

        @Test
        @DisplayName("Should return Big5 encoding")
        void shouldReturnBig5Encoding() {
            assertEquals("Big5", parser.getEncoding());
        }
    }
}
