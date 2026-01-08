package com.fep.integration.converter;

import com.fep.integration.model.MainframeRequest;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Iso8583ToMainframeConverterTest {

    private Iso8583ToMainframeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new Iso8583ToMainframeConverter();
    }

    @Test
    void testConvert_WithdrawalRequest() {
        // Given
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti("0200");
        iso8583.setField(2, "4111111111111111");    // PAN
        iso8583.setField(4, "000000010000");        // Amount: 100.00
        iso8583.setField(7, "0615120530");          // DateTime
        iso8583.setField(11, "123456");             // STAN
        iso8583.setField(37, "123456789012");       // RRN
        iso8583.setField(41, "ATM00001");           // Terminal ID
        iso8583.setField(42, "MERCHANT001");        // Merchant ID
        iso8583.setField(49, "901");                // Currency (TWD)

        // When
        MainframeRequest result = converter.convert(iso8583);

        // Then
        assertNotNull(result);
        assertEquals("3001", result.getTransactionCode()); // Withdrawal code
        assertEquals("4111111111111111", result.getCardNumber());
        assertEquals(10000L, result.getAmount());
        assertEquals("901", result.getCurrencyCode());
        assertEquals("ATM00001", result.getTerminalId());
        assertEquals("MERCHANT001", result.getMerchantId());
        assertNotNull(result.getRawPayload());
        assertTrue(result.getRawPayload().length() > 0);
    }

    @Test
    void testConvert_TransferRequest() {
        // Given
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti("0220");
        iso8583.setField(2, "4111111111111111");
        iso8583.setField(4, "000000050000");        // Amount: 500.00
        iso8583.setField(11, "789012");
        iso8583.setField(37, "999888777666");
        iso8583.setField(102, "1234567890");        // From account
        iso8583.setField(103, "0987654321");        // To account

        // When
        MainframeRequest result = converter.convert(iso8583);

        // Then
        assertNotNull(result);
        assertEquals("3003", result.getTransactionCode()); // Transfer code
        assertEquals(50000L, result.getAmount());
        assertEquals("1234567890", result.getField1());    // From account
        assertEquals("0987654321", result.getField2());    // To account
    }

    @Test
    void testConvert_BalanceInquiry() {
        // Given
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti("0100");
        iso8583.setField(2, "4111111111111111");
        iso8583.setField(11, "123456");
        iso8583.setField(37, "123456789012");

        // When
        MainframeRequest result = converter.convert(iso8583);

        // Then
        assertNotNull(result);
        assertEquals("3002", result.getTransactionCode()); // Authorization code
    }

    @Test
    void testConvert_Reversal() {
        // Given
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti("0400");
        iso8583.setField(2, "4111111111111111");
        iso8583.setField(4, "000000010000");
        iso8583.setField(11, "123456");
        iso8583.setField(37, "123456789012");

        // When
        MainframeRequest result = converter.convert(iso8583);

        // Then
        assertNotNull(result);
        assertEquals("9001", result.getTransactionCode()); // Reversal code
    }

    @Test
    void testConvert_RawPayloadFormat() {
        // Given
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti("0200");
        iso8583.setField(2, "4111111111111111");
        iso8583.setField(4, "000000010000");
        iso8583.setField(11, "123456");
        iso8583.setField(37, "RRN123456");
        iso8583.setField(41, "ATM00001");
        iso8583.setField(42, "MERCHANT001");

        // When
        MainframeRequest result = converter.convert(iso8583);

        // Then
        String rawPayload = result.getRawPayload();
        assertNotNull(rawPayload);
        
        // Check fixed-length format
        assertTrue(rawPayload.startsWith("3001")); // Transaction code (4 bytes)
        assertTrue(rawPayload.length() >= 300);     // Minimum expected length
    }

    @Test
    void testSupports() {
        assertTrue(converter.supports(Iso8583Message.class));
        assertFalse(converter.supports(String.class));
    }
}
