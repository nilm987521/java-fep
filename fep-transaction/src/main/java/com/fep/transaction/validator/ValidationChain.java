package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chain of validators that processes transaction requests in order.
 */
public class ValidationChain {

    private static final Logger log = LoggerFactory.getLogger(ValidationChain.class);

    private final List<TransactionValidator> validators;

    public ValidationChain() {
        this.validators = new ArrayList<>();
    }

    public ValidationChain(List<TransactionValidator> validators) {
        this.validators = new ArrayList<>(validators);
        sortValidators();
    }

    /**
     * Adds a validator to the chain.
     *
     * @param validator the validator to add
     * @return this chain for fluent API
     */
    public ValidationChain addValidator(TransactionValidator validator) {
        validators.add(validator);
        sortValidators();
        return this;
    }

    /**
     * Removes a validator from the chain.
     *
     * @param validator the validator to remove
     * @return this chain for fluent API
     */
    public ValidationChain removeValidator(TransactionValidator validator) {
        validators.remove(validator);
        return this;
    }

    /**
     * Validates the request through all validators in the chain.
     *
     * @param request the transaction request to validate
     * @throws TransactionException if any validation fails
     */
    public void validate(TransactionRequest request) {
        String txnId = request.getTransactionId();
        log.debug("[{}] Running validation chain with {} validators", txnId, validators.size());

        for (TransactionValidator validator : validators) {
            log.trace("[{}] Running validator: {}", txnId, validator.getValidatorName());
            try {
                validator.validate(request);
            } catch (TransactionException e) {
                log.debug("[{}] Validation failed at {}: {}",
                        txnId, validator.getValidatorName(), e.getMessage());
                throw e;
            }
        }

        log.debug("[{}] All validations passed", txnId);
    }

    /**
     * Gets the number of validators in the chain.
     *
     * @return the validator count
     */
    public int size() {
        return validators.size();
    }

    /**
     * Sorts validators by their order.
     */
    private void sortValidators() {
        validators.sort(Comparator.comparingInt(TransactionValidator::getOrder));
    }

    /**
     * Creates a default validation chain with standard validators.
     *
     * @return a new validation chain with default validators
     */
    public static ValidationChain createDefault() {
        return new ValidationChain()
                .addValidator(new CardValidator())
                .addValidator(new AmountValidator());
    }
}
