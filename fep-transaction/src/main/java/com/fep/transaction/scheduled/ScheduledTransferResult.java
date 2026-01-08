package com.fep.transaction.scheduled;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Result of a scheduled transfer execution.
 */
@Data
@Builder
public class ScheduledTransferResult {

    /** Schedule ID */
    private String scheduleId;

    /** Transaction ID of the executed transfer */
    private String transactionId;

    /** Whether the transfer was successful */
    private boolean success;

    /** Response code from the transfer */
    private String responseCode;

    /** Authorization code (if successful) */
    private String authorizationCode;

    /** Error message (if failed) */
    private String errorMessage;

    /** Execution timestamp */
    private LocalDateTime executedAt;
}
