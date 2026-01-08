package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;

/**
 * Transaction service interface.
 * Provides the main entry point for transaction processing.
 */
public interface TransactionService {

    /**
     * Processes a transaction request.
     *
     * @param request the transaction request
     * @return the transaction response
     */
    TransactionResponse process(TransactionRequest request);

    /**
     * Checks if a transaction type is supported.
     *
     * @param transactionType the transaction type
     * @return true if supported
     */
    boolean isSupported(TransactionType transactionType);

    /**
     * Gets the list of supported transaction types.
     *
     * @return array of supported transaction types
     */
    TransactionType[] getSupportedTypes();
}
