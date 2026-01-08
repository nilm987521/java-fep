package com.fep.integration.converter;

import com.fep.integration.model.MainframeResponse;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MainframeToIso8583ConverterTest {

    private MainframeToIso8583Converter converter;

    @BeforeEach
    void setUp() {
        converter = new MainframeToIso8583Converter();
    }

    @Test
    void testConvert_SuccessResponse() {
        // Given
        MainframeResponse mainframeResponse = MainframeResponse.builder()
                .transactionId("TXN123456")
                .responseCode("00")
                .responseMessage("Approved")
                .authorizationCode("AUTH123")
                .referenceNumber("REF123456789")
                .balance(100000L)
                .accountName("CHEN HSIAO MING")
                .responseTime(LocalDateTime.of(2024, 1, 15, 14, 30, 0))
                .build();

        // When
        Iso8583Message result = converter.convert(mainframeResponse);

        // Then
        assertNotNull(result);
        assertEquals("0210", result.getMti());
        assertEquals("00", result.getField(39)); // Response code
        assertEquals("AUTH123", result.getField(38)); // Auth code
        assertEquals("REF123456789", result.getField(37)); // RRN
        assertEquals("C000000100000", result.getField(54)); // Balance (Credit)
        assertEquals("CHEN HSIAO MING", result.getField(63)); // Account name
        assertEquals("143000", result.getField(12)); // Time
        assertEquals("0115", result.getField(13)); // Date
    }

    @Test
    void testConvert_DeclinedResponse() {
        // Given
        MainframeResponse mainframeResponse = MainframeResponse.builder()
                .transactionId("TXN123456")
                .responseCode("51")
                .responseMessage("Insufficient funds")
                .build();

        // When
        Iso8583Message result = converter.convert(mainframeResponse);

        // Then
        assertNotNull(result);
        assertEquals("51", result.getField(39)); // Response code
    }

    @Test
    void testConvert_WithAdditionalFields() {
        // Given
        MainframeResponse mainframeResponse = MainframeResponse.builder()
                .transactionId("TXN123456")
                .responseCode("00")
                .field1("ADDITIONAL1")
                .field2("ADDITIONAL2")
                .field3("ADDITIONAL3")
                .build();

        // When
        Iso8583Message result = converter.convert(mainframeResponse);

        // Then
        assertEquals("ADDITIONAL1", result.getField(102));
        assertEquals("ADDITIONAL2", result.getField(103));
        assertEquals("ADDITIONAL3", result.getField(62));
    }

    @Test
    void testParseCobolFormat() {
        // Given - Build a COBOL format response
        // Format: ResponseCode(4) + TxnId(20) + DateTime(14) + RefNum(20) + Balance(12) + AuthCode(6) + Name(50) + Message(100)
        StringBuilder cobol = new StringBuilder();
        cobol.append(String.format("%-4s", "00"));                      // Response code (4)
        cobol.append(String.format("%-20s", "TXN123456"));              // Transaction ID (20)
        cobol.append("20240115143000");                                  // DateTime (14)
        cobol.append(String.format("%-20s", "REF123456789"));           // Reference number (20)
        cobol.append("000000100000");                                    // Balance (12)
        cobol.append("AUTH12");                                          // Auth code (6)
        cobol.append(String.format("%-50s", "CHEN HSIAO MING"));        // Name (50)
        cobol.append(String.format("%-100s", "Approved"));              // Message (100)
        cobol.append(String.format("%-30s", "FIELD1"));                 // Field1 (30)
        cobol.append(String.format("%-30s", "FIELD2"));                 // Field2 (30)
        cobol.append(String.format("%-100s", "FIELD3"));                // Field3 (100)

        String rawPayload = cobol.toString();

        // When
        MainframeResponse result = converter.parseCobolFormat(rawPayload);

        // Then
        assertNotNull(result);
        assertEquals("00", result.getResponseCode());
        assertEquals("TXN123456", result.getTransactionId());
        assertEquals("REF123456789", result.getReferenceNumber());
        assertEquals(100000L, result.getBalance());
        assertEquals("AUTH12", result.getAuthorizationCode());
        assertEquals("CHEN HSIAO MING", result.getAccountName());
        assertEquals("Approved", result.getResponseMessage());
        assertTrue(result.isSuccess());
        assertFalse(result.isDeclined());
    }

    @Test
    void testResponseCodeMapping() {
        // Test various response code mappings
        assertEquals("00", converter.convert(buildResponse("00")).getField(39));
        assertEquals("51", converter.convert(buildResponse("51")).getField(39));
        assertEquals("54", converter.convert(buildResponse("54")).getField(39));
        assertEquals("55", converter.convert(buildResponse("55")).getField(39));
        assertEquals("91", converter.convert(buildResponse("91")).getField(39));
        assertEquals("96", converter.convert(buildResponse("96")).getField(39));
        assertEquals("96", converter.convert(buildResponse("XX")).getField(39)); // Unknown -> 96
    }

    @Test
    void testBalanceFormatting() {
        // Credit balance
        MainframeResponse creditResponse = MainframeResponse.builder()
                .responseCode("00")
                .balance(50000L)
                .build();
        Iso8583Message creditResult = converter.convert(creditResponse);
        assertEquals("C000000050000", creditResult.getField(54));

        // Debit balance (negative)
        MainframeResponse debitResponse = MainframeResponse.builder()
                .responseCode("00")
                .balance(-25000L)
                .build();
        Iso8583Message debitResult = converter.convert(debitResponse);
        assertEquals("D000000025000", debitResult.getField(54));

        // Zero balance
        MainframeResponse zeroResponse = MainframeResponse.builder()
                .responseCode("00")
                .balance(0L)
                .build();
        Iso8583Message zeroResult = converter.convert(zeroResponse);
        assertEquals("C000000000000", zeroResult.getField(54));
    }

    @Test
    void testSupports() {
        assertTrue(converter.supports(MainframeResponse.class));
        assertFalse(converter.supports(String.class));
    }

    private MainframeResponse buildResponse(String responseCode) {
        return MainframeResponse.builder()
                .responseCode(responseCode)
                .transactionId("TEST")
                .build();
    }
}
