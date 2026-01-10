package com.fep.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TransactionResponse.
 */
@DisplayName("TransactionResponse Tests")
class TransactionResponseTest {

    @Test
    @DisplayName("Should create success response")
    void shouldCreateSuccessResponse() {
        String referenceNumber = "123456789012";
        String message = "Transaction approved";

        TransactionResponse response = TransactionResponse.success(referenceNumber, message);

        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo(message);
        assertThat(response.getReferenceNumber()).isEqualTo(referenceNumber);
        assertThat(response.getTransactionTime()).isNotNull();
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should create error response")
    void shouldCreateErrorResponse() {
        String errorCode = "51";
        String errorMessage = "Insufficient funds";

        TransactionResponse response = TransactionResponse.error(errorCode, errorMessage);

        assertThat(response.getResponseCode()).isEqualTo(errorCode);
        assertThat(response.getResponseMessage()).isEqualTo(errorMessage);
        assertThat(response.getTransactionTime()).isNotNull();
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return true for isSuccess when response code is 00")
    void shouldReturnTrueForSuccessWhenResponseCodeIs00() {
        TransactionResponse response = TransactionResponse.builder()
            .responseCode("00")
            .build();

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should return false for isSuccess when response code is not 00")
    void shouldReturnFalseForSuccessWhenResponseCodeIsNot00() {
        assertThat(TransactionResponse.builder().responseCode("01").build().isSuccess()).isFalse();
        assertThat(TransactionResponse.builder().responseCode("51").build().isSuccess()).isFalse();
        assertThat(TransactionResponse.builder().responseCode("99").build().isSuccess()).isFalse();
        assertThat(TransactionResponse.builder().responseCode(null).build().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should create response using builder with all fields")
    void shouldCreateResponseUsingBuilderWithAllFields() {
        LocalDateTime now = LocalDateTime.now();

        TransactionResponse response = TransactionResponse.builder()
            .responseCode("00")
            .responseMessage("Approved")
            .referenceNumber("123456789012")
            .traceNumber("000001")
            .authorizationCode("AUTH01")
            .transactionTime(now)
            .amount(new BigDecimal("1000.00"))
            .availableBalance(new BigDecimal("5000.00"))
            .ledgerBalance(new BigDecimal("5500.00"))
            .processingTimeMs(150L)
            .build();

        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Approved");
        assertThat(response.getReferenceNumber()).isEqualTo("123456789012");
        assertThat(response.getTraceNumber()).isEqualTo("000001");
        assertThat(response.getAuthorizationCode()).isEqualTo("AUTH01");
        assertThat(response.getTransactionTime()).isEqualTo(now);
        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("5500.00");
        assertThat(response.getProcessingTimeMs()).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should create response using no-args constructor and setters")
    void shouldCreateResponseUsingNoArgsConstructorAndSetters() {
        TransactionResponse response = new TransactionResponse();

        response.setResponseCode("00");
        response.setResponseMessage("Success");
        response.setReferenceNumber("RRN123");
        response.setTraceNumber("STAN456");
        response.setAuthorizationCode("AUTH789");
        response.setAmount(new BigDecimal("500.00"));
        response.setAvailableBalance(new BigDecimal("10000.00"));
        response.setLedgerBalance(new BigDecimal("10100.00"));
        response.setProcessingTimeMs(100L);

        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Success");
        assertThat(response.getReferenceNumber()).isEqualTo("RRN123");
        assertThat(response.getTraceNumber()).isEqualTo("STAN456");
        assertThat(response.getAuthorizationCode()).isEqualTo("AUTH789");
        assertThat(response.getAmount()).isEqualByComparingTo("500.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("10100.00");
        assertThat(response.getProcessingTimeMs()).isEqualTo(100L);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should create response using all-args constructor")
    void shouldCreateResponseUsingAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();

        TransactionResponse response = new TransactionResponse(
            "00",
            "Approved",
            "RRN123",
            "STAN456",
            "AUTH789",
            now,
            new BigDecimal("1000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("5500.00"),
            200L
        );

        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Approved");
        assertThat(response.getReferenceNumber()).isEqualTo("RRN123");
        assertThat(response.getTraceNumber()).isEqualTo("STAN456");
        assertThat(response.getAuthorizationCode()).isEqualTo("AUTH789");
        assertThat(response.getTransactionTime()).isEqualTo(now);
        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("5500.00");
        assertThat(response.getProcessingTimeMs()).isEqualTo(200L);
    }
}
