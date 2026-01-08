package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationChain.
 */
class ValidationChainTest {

    @Test
    @DisplayName("Should create empty chain")
    void testEmptyChain() {
        ValidationChain chain = new ValidationChain();

        assertEquals(0, chain.size());
    }

    @Test
    @DisplayName("Should add validators to chain")
    void testAddValidator() {
        ValidationChain chain = new ValidationChain()
                .addValidator(new CardValidator())
                .addValidator(new AmountValidator());

        assertEquals(2, chain.size());
    }

    @Test
    @DisplayName("Should create chain with list of validators")
    void testCreateWithList() {
        ValidationChain chain = new ValidationChain(Arrays.asList(
                new CardValidator(),
                new AmountValidator()
        ));

        assertEquals(2, chain.size());
    }

    @Test
    @DisplayName("Should create default chain")
    void testCreateDefault() {
        ValidationChain chain = ValidationChain.createDefault();

        assertEquals(2, chain.size());
    }

    @Test
    @DisplayName("Should validate valid request")
    void testValidateValidRequest() {
        ValidationChain chain = ValidationChain.createDefault();
        TransactionRequest request = createValidRequest();

        assertDoesNotThrow(() -> chain.validate(request));
    }

    @Test
    @DisplayName("Should fail on invalid PAN")
    void testValidateInvalidPan() {
        ValidationChain chain = ValidationChain.createDefault();
        TransactionRequest request = createValidRequest();
        request.setPan("1234567890123456"); // Invalid Luhn

        assertThrows(TransactionException.class, () -> chain.validate(request));
    }

    @Test
    @DisplayName("Should fail on invalid amount")
    void testValidateInvalidAmount() {
        ValidationChain chain = ValidationChain.createDefault();
        TransactionRequest request = createValidRequest();
        request.setAmount(new BigDecimal("-100"));

        assertThrows(TransactionException.class, () -> chain.validate(request));
    }

    @Test
    @DisplayName("Should run validators in order")
    void testValidatorOrder() {
        // Card validator has order 10, Amount validator has order 20
        // Card should run first
        ValidationChain chain = ValidationChain.createDefault();
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TEST001")
                .transactionType(TransactionType.WITHDRAWAL)
                .pan("invalid") // Invalid PAN
                .amount(new BigDecimal("-100")) // Also invalid amount
                .build();

        TransactionException ex = assertThrows(TransactionException.class,
                () -> chain.validate(request));

        // Should fail on PAN validation first (code 14)
        assertEquals("14", ex.getResponseCode());
    }

    @Test
    @DisplayName("Should remove validator from chain")
    void testRemoveValidator() {
        CardValidator cardValidator = new CardValidator();
        ValidationChain chain = new ValidationChain()
                .addValidator(cardValidator)
                .addValidator(new AmountValidator());

        assertEquals(2, chain.size());

        chain.removeValidator(cardValidator);

        assertEquals(1, chain.size());
    }

    private TransactionRequest createValidRequest() {
        return TransactionRequest.builder()
                .transactionId("TEST001")
                .transactionType(TransactionType.WITHDRAWAL)
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .build();
    }
}
