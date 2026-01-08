package com.fep.integration.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request message to mainframe core banking system.
 */
@Data
@Builder
public class MainframeRequest {

    /**
     * Transaction ID for tracking.
     */
    private String transactionId;

    /**
     * Transaction code (功能代碼).
     */
    private String transactionCode;

    /**
     * Account number.
     */
    private String accountNumber;

    /**
     * Card number (optional).
     */
    private String cardNumber;

    /**
     * Transaction amount.
     */
    private Long amount;

    /**
     * Currency code (default TWD).
     */
    @Builder.Default
    private String currencyCode = "TWD";

    /**
     * Terminal ID.
     */
    private String terminalId;

    /**
     * Merchant ID (for POS).
     */
    private String merchantId;

    /**
     * Transaction timestamp.
     */
    private LocalDateTime transactionTime;

    /**
     * Additional data field 1.
     */
    private String field1;

    /**
     * Additional data field 2.
     */
    private String field2;

    /**
     * Additional data field 3.
     */
    private String field3;

    /**
     * Reserved field for future use.
     */
    private String reserved;

    /**
     * Raw message payload (COBOL format).
     */
    private String rawPayload;
}
