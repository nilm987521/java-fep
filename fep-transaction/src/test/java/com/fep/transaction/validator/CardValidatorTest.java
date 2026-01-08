package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CardValidator.
 */
class CardValidatorTest {

    private CardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CardValidator();
    }

    @Test
    @DisplayName("Should validate valid Visa card")
    void testValidVisaCard() {
        TransactionRequest request = createRequest("4111111111111111", "2912"); // Dec 2029

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    @DisplayName("Should validate valid Mastercard")
    void testValidMastercard() {
        TransactionRequest request = createRequest("5500000000000004", "2912"); // Dec 2029

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    @DisplayName("Should reject null PAN")
    void testNullPan() {
        TransactionRequest request = createRequest(null, "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject empty PAN")
    void testEmptyPan() {
        TransactionRequest request = createRequest("", "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject PAN too short")
    void testPanTooShort() {
        TransactionRequest request = createRequest("411111111111", "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject PAN too long")
    void testPanTooLong() {
        TransactionRequest request = createRequest("41111111111111111111", "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject PAN with non-digits")
    void testPanWithNonDigits() {
        TransactionRequest request = createRequest("4111-1111-1111-1111", "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject invalid Luhn checksum")
    void testInvalidLuhnChecksum() {
        TransactionRequest request = createRequest("4111111111111112", "2512");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should reject expired card")
    void testExpiredCard() {
        TransactionRequest request = createRequest("4111111111111111", "2301"); // Jan 2023

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("54", ex.getResponseCode()); // Expired card
    }

    @Test
    @DisplayName("Should reject invalid expiration month")
    void testInvalidExpirationMonth() {
        TransactionRequest request = createRequest("4111111111111111", "2913"); // Month 13

        TransactionException ex = assertThrows(TransactionException.class,
                () -> validator.validate(request));
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should accept null expiration date")
    void testNullExpirationDate() {
        TransactionRequest request = createRequest("4111111111111111", null);

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    @DisplayName("Should have low order priority")
    void testOrderPriority() {
        assertEquals(10, validator.getOrder());
    }

    private TransactionRequest createRequest(String pan, String expDate) {
        return TransactionRequest.builder()
                .transactionId("TEST001")
                .pan(pan)
                .expirationDate(expDate)
                .amount(new BigDecimal("1000"))
                .build();
    }
}
