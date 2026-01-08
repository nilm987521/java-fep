package com.fep.transaction.timeout;

/**
 * Status of a transaction timeout check.
 */
public enum TimeoutStatus {

    /** Transaction is within timeout period */
    ACTIVE,

    /** Transaction is approaching timeout (warning threshold) */
    WARNING,

    /** Transaction has exceeded timeout */
    EXPIRED,

    /** Transaction completed before timeout */
    COMPLETED
}
