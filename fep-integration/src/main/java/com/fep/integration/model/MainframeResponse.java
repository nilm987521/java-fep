package com.fep.integration.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response message from mainframe core banking system.
 */
@Data
@Builder
public class MainframeResponse {

    /**
     * Transaction ID for tracking.
     */
    private String transactionId;

    /**
     * Response code from mainframe.
     */
    private String responseCode;

    /**
     * Response message description.
     */
    private String responseMessage;

    /**
     * Account balance (if applicable).
     */
    private Long balance;

    /**
     * Available balance (if applicable).
     */
    private Long availableBalance;

    /**
     * Authorization code (if applicable).
     */
    private String authorizationCode;

    /**
     * Transaction reference number from mainframe.
     */
    private String referenceNumber;

    /**
     * Response timestamp.
     */
    private LocalDateTime responseTime;

    /**
     * Account name/holder name.
     */
    private String accountName;

    /**
     * Additional response field 1.
     */
    private String field1;

    /**
     * Additional response field 2.
     */
    private String field2;

    /**
     * Additional response field 3.
     */
    private String field3;

    /**
     * Reserved field for future use.
     */
    private String reserved;

    /**
     * Raw response payload (COBOL format).
     */
    private String rawPayload;

    /**
     * Indicates if the transaction was successful.
     */
    public boolean isSuccess() {
        return "00".equals(responseCode) || "0000".equals(responseCode);
    }

    /**
     * Indicates if the transaction was declined.
     */
    public boolean isDeclined() {
        return !isSuccess();
    }
}
