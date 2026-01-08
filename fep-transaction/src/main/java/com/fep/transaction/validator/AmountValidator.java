package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Validates transaction amounts based on transaction type and configured limits.
 */
public class AmountValidator implements TransactionValidator {

    private final Map<TransactionType, BigDecimal> maxLimits;
    private final Map<TransactionType, BigDecimal> minLimits;

    public AmountValidator() {
        this.maxLimits = new EnumMap<>(TransactionType.class);
        this.minLimits = new EnumMap<>(TransactionType.class);
        initializeDefaultLimits();
    }

    private void initializeDefaultLimits() {
        // Default maximum limits
        maxLimits.put(TransactionType.WITHDRAWAL, new BigDecimal("20000"));
        maxLimits.put(TransactionType.TRANSFER, new BigDecimal("2000000"));
        maxLimits.put(TransactionType.DEPOSIT, new BigDecimal("200000"));
        maxLimits.put(TransactionType.BILL_PAYMENT, new BigDecimal("500000"));
        maxLimits.put(TransactionType.PURCHASE, new BigDecimal("1000000"));
        maxLimits.put(TransactionType.P2P_TRANSFER, new BigDecimal("50000"));
        maxLimits.put(TransactionType.QR_PAYMENT, new BigDecimal("50000"));

        // Default minimum limits
        minLimits.put(TransactionType.WITHDRAWAL, new BigDecimal("100"));
        minLimits.put(TransactionType.TRANSFER, BigDecimal.ONE);
        minLimits.put(TransactionType.DEPOSIT, new BigDecimal("100"));
        minLimits.put(TransactionType.BILL_PAYMENT, BigDecimal.ONE);
        minLimits.put(TransactionType.PURCHASE, BigDecimal.ONE);
    }

    @Override
    public void validate(TransactionRequest request) {
        TransactionType type = request.getTransactionType();
        BigDecimal amount = request.getAmount();

        // Skip validation for non-monetary transactions
        if (type != null && !type.isMonetaryTransaction()) {
            return;
        }

        // Validate amount is present
        if (amount == null) {
            throw TransactionException.invalidAmount();
        }

        // Validate amount is positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw TransactionException.invalidAmount();
        }

        // Validate minimum limit
        BigDecimal minLimit = minLimits.get(type);
        if (minLimit != null && amount.compareTo(minLimit) < 0) {
            throw TransactionException.invalidAmount();
        }

        // Validate maximum limit
        BigDecimal maxLimit = maxLimits.get(type);
        if (maxLimit != null && amount.compareTo(maxLimit) > 0) {
            throw TransactionException.exceedsLimit(type != null ? type.getDescription() : "transaction");
        }
    }

    /**
     * Sets a custom maximum limit for a transaction type.
     *
     * @param type the transaction type
     * @param limit the maximum limit
     */
    public void setMaxLimit(TransactionType type, BigDecimal limit) {
        maxLimits.put(type, limit);
    }

    /**
     * Sets a custom minimum limit for a transaction type.
     *
     * @param type the transaction type
     * @param limit the minimum limit
     */
    public void setMinLimit(TransactionType type, BigDecimal limit) {
        minLimits.put(type, limit);
    }

    /**
     * Gets the maximum limit for a transaction type.
     *
     * @param type the transaction type
     * @return the maximum limit, or null if not set
     */
    public BigDecimal getMaxLimit(TransactionType type) {
        return maxLimits.get(type);
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
