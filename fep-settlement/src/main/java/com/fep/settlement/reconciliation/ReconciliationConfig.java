package com.fep.settlement.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Configuration for reconciliation process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationConfig {

    /** Amount tolerance for matching (difference within this amount is considered match) */
    @Builder.Default
    private BigDecimal amountTolerance = BigDecimal.ZERO;

    /** Whether to match by RRN */
    @Builder.Default
    private boolean matchByRrn = true;

    /** Whether to match by STAN */
    @Builder.Default
    private boolean matchByStan = true;

    /** Whether to match by transaction reference */
    @Builder.Default
    private boolean matchByTxnRef = true;

    /** Whether to match by amount when other keys match */
    @Builder.Default
    private boolean validateAmount = true;

    /** Whether to auto-create discrepancy records */
    @Builder.Default
    private boolean autoCreateDiscrepancies = true;

    /** Whether to continue on errors */
    @Builder.Default
    private boolean continueOnError = true;

    /** Maximum discrepancies before stopping */
    @Builder.Default
    private int maxDiscrepancies = 1000;

    /** Whether to include already matched records in result */
    @Builder.Default
    private boolean includeMatchedRecords = true;

    /** Time window for matching (hours) */
    @Builder.Default
    private int matchingTimeWindowHours = 24;

    /** Default configuration */
    public static ReconciliationConfig defaultConfig() {
        return ReconciliationConfig.builder().build();
    }

    /** Strict configuration - no tolerance */
    public static ReconciliationConfig strict() {
        return ReconciliationConfig.builder()
                .amountTolerance(BigDecimal.ZERO)
                .validateAmount(true)
                .continueOnError(false)
                .build();
    }

    /** Lenient configuration - allows small differences */
    public static ReconciliationConfig lenient() {
        return ReconciliationConfig.builder()
                .amountTolerance(new BigDecimal("1.00"))
                .validateAmount(true)
                .continueOnError(true)
                .build();
    }
}
