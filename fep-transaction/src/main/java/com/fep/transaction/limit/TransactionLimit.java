package com.fep.transaction.limit;

import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Represents a transaction limit configuration.
 */
@Data
@Builder
public class TransactionLimit {

    /** Transaction type this limit applies to */
    private TransactionType transactionType;

    /** Type of limit */
    private LimitType limitType;

    /** Limit amount or count */
    private BigDecimal limitValue;

    /** Current used amount or count */
    @Builder.Default
    private BigDecimal usedValue = BigDecimal.ZERO;

    /** Channel (optional - for channel-specific limits) */
    private String channel;

    /** Whether this limit is active */
    @Builder.Default
    private boolean active = true;

    /**
     * Gets the remaining available value.
     */
    public BigDecimal getRemainingValue() {
        return limitValue.subtract(usedValue);
    }

    /**
     * Checks if the limit would be exceeded by the given amount.
     */
    public boolean wouldExceed(BigDecimal amount) {
        return usedValue.add(amount).compareTo(limitValue) > 0;
    }

    /**
     * Checks if the limit is already exceeded.
     */
    public boolean isExceeded() {
        return usedValue.compareTo(limitValue) >= 0;
    }

    /**
     * Gets the usage percentage.
     */
    public int getUsagePercentage() {
        if (limitValue.compareTo(BigDecimal.ZERO) == 0) {
            return 100;
        }
        return usedValue.multiply(BigDecimal.valueOf(100))
                .divide(limitValue, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }
}
