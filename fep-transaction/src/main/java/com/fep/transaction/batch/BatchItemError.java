package com.fep.transaction.batch;

import lombok.Builder;
import lombok.Data;

/**
 * Error details for a failed batch item.
 */
@Data
@Builder
public class BatchItemError {

    /** Transaction ID of the failed item */
    private String transactionId;

    /** Response code from processing */
    private String responseCode;

    /** Detailed error message */
    private String errorDetail;

    /** Exception class name if applicable */
    private String exceptionType;

    /** Original transaction index in batch */
    private int batchIndex;
}
