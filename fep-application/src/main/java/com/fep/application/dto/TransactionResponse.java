package com.fep.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    /**
     * Response code (00=success)
     */
    private String responseCode;

    /**
     * Response message
     */
    private String responseMessage;

    /**
     * Transaction reference number (RRN)
     */
    private String referenceNumber;

    /**
     * System trace audit number (STAN)
     */
    private String traceNumber;

    /**
     * Authorization code
     */
    private String authorizationCode;

    /**
     * Transaction timestamp
     */
    private LocalDateTime transactionTime;

    /**
     * Transaction amount
     */
    private BigDecimal amount;

    /**
     * Available balance (for balance inquiry)
     */
    private BigDecimal availableBalance;

    /**
     * Ledger balance (for balance inquiry)
     */
    private BigDecimal ledgerBalance;

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;

    /**
     * Success flag
     */
    public boolean isSuccess() {
        return "00".equals(responseCode);
    }

    /**
     * Create success response
     */
    public static TransactionResponse success(String referenceNumber, String message) {
        return TransactionResponse.builder()
                .responseCode("00")
                .responseMessage(message)
                .referenceNumber(referenceNumber)
                .transactionTime(LocalDateTime.now())
                .build();
    }

    /**
     * Create error response
     */
    public static TransactionResponse error(String code, String message) {
        return TransactionResponse.builder()
                .responseCode(code)
                .responseMessage(message)
                .transactionTime(LocalDateTime.now())
                .build();
    }
}
