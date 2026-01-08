package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;

/**
 * Interface for transaction processors.
 * Each transaction type should have its own processor implementation.
 */
public interface TransactionProcessor {

    /**
     * Gets the transaction type this processor handles.
     *
     * @return the supported transaction type
     */
    TransactionType getSupportedType();

    /**
     * Processes the transaction request.
     *
     * @param request the transaction request
     * @return the transaction response
     */
    TransactionResponse process(TransactionRequest request);

    /**
     * Validates the transaction request before processing.
     *
     * @param request the transaction request
     * @throws com.fep.transaction.exception.TransactionException if validation fails
     */
    void validate(TransactionRequest request);

    /**
     * Checks if this processor supports the given transaction type.
     *
     * @param transactionType the transaction type to check
     * @return true if supported
     */
    default boolean supports(TransactionType transactionType) {
        return getSupportedType() == transactionType;
    }

    /**
     * Gets the processor name for logging purposes.
     *
     * @return the processor name
     */
    default String getProcessorName() {
        return this.getClass().getSimpleName();
    }
}
