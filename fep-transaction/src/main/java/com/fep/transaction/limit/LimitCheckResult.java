package com.fep.transaction.limit;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a limit check operation.
 */
@Data
@Builder
public class LimitCheckResult {

    /** Whether all limits passed */
    private boolean passed;

    /** The limit that was exceeded (if any) */
    private TransactionLimit exceededLimit;

    /** Type of exceeded limit */
    private LimitType exceededLimitType;

    /** Message describing the result */
    private String message;

    /** Remaining available amount (minimum across all limits) */
    private BigDecimal remainingAmount;

    /** All checked limits */
    @Builder.Default
    private List<TransactionLimit> checkedLimits = new ArrayList<>();

    /**
     * Creates a passed result.
     */
    public static LimitCheckResult passed(BigDecimal remainingAmount) {
        return LimitCheckResult.builder()
                .passed(true)
                .remainingAmount(remainingAmount)
                .message("All limits passed")
                .build();
    }

    /**
     * Creates a failed result.
     */
    public static LimitCheckResult failed(TransactionLimit exceededLimit, String message) {
        return LimitCheckResult.builder()
                .passed(false)
                .exceededLimit(exceededLimit)
                .exceededLimitType(exceededLimit.getLimitType())
                .remainingAmount(exceededLimit.getRemainingValue())
                .message(message)
                .build();
    }

    /**
     * Gets the response code for exceeded limit.
     */
    public String getResponseCode() {
        if (passed) {
            return "00";
        }
        if (exceededLimitType == null) {
            return "61";
        }
        return switch (exceededLimitType) {
            case SINGLE_TRANSACTION -> "61";
            case DAILY_CUMULATIVE, MONTHLY_CUMULATIVE -> "61";
            case DAILY_COUNT -> "65";
            case NON_DESIGNATED_TRANSFER -> "61";
        };
    }
}
