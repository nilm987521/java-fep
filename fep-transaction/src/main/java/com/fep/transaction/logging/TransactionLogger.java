package com.fep.transaction.logging;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;

/**
 * Interface for transaction logging.
 */
public interface TransactionLogger {

    /**
     * Logs a transaction request.
     *
     * @param request the transaction request
     */
    void logRequest(TransactionRequest request);

    /**
     * Logs a transaction response.
     *
     * @param request the original request
     * @param response the transaction response
     */
    void logResponse(TransactionRequest request, TransactionResponse response);

    /**
     * Logs a transaction error.
     *
     * @param request the original request
     * @param error the error that occurred
     */
    void logError(TransactionRequest request, Throwable error);

    /**
     * Creates a complete transaction log entry.
     *
     * @param request the transaction request
     * @param response the transaction response
     * @return the transaction log entry
     */
    TransactionLog createLog(TransactionRequest request, TransactionResponse response);
}
