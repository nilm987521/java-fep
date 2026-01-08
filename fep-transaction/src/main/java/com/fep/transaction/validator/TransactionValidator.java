package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;

import java.util.List;

/**
 * Interface for transaction validators.
 * Validators perform specific validation checks on transaction requests.
 */
public interface TransactionValidator {

    /**
     * Validates the transaction request.
     *
     * @param request the transaction request to validate
     * @throws TransactionException if validation fails
     */
    void validate(TransactionRequest request);

    /**
     * Gets the validator name for logging.
     *
     * @return the validator name
     */
    default String getValidatorName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Gets the order in which this validator should run.
     * Lower numbers run first.
     *
     * @return the order value
     */
    default int getOrder() {
        return 100;
    }
}
