package com.fep.transaction.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Transaction processing status.
 */
@Getter
@RequiredArgsConstructor
public enum TransactionStatus {

    /** Transaction received, pending processing */
    PENDING("待處理", false),

    /** Transaction is being processed */
    PROCESSING("處理中", false),

    /** Sent to host, awaiting response */
    SENT_TO_HOST("已送主機", false),

    /** Successfully completed */
    COMPLETED("完成", true),

    /** Transaction was approved */
    APPROVED("核准", true),

    /** Transaction was declined */
    DECLINED("拒絕", true),

    /** Transaction timed out */
    TIMEOUT("逾時", true),

    /** Transaction failed with error */
    FAILED("失敗", true),

    /** Transaction was reversed */
    REVERSED("已沖正", true),

    /** Reversal pending */
    REVERSAL_PENDING("沖正待處理", false),

    /** Transaction cancelled */
    CANCELLED("取消", true);

    private final String chineseDescription;
    private final boolean terminal;

    /**
     * Checks if this status represents a successful outcome.
     */
    public boolean isSuccessful() {
        return this == COMPLETED || this == APPROVED;
    }

    /**
     * Checks if this status can transition to the given status.
     */
    public boolean canTransitionTo(TransactionStatus newStatus) {
        if (this.terminal && newStatus != REVERSED && newStatus != REVERSAL_PENDING) {
            return false;
        }
        return switch (this) {
            case PENDING -> newStatus == PROCESSING || newStatus == CANCELLED;
            case PROCESSING -> newStatus == SENT_TO_HOST || newStatus == COMPLETED ||
                    newStatus == APPROVED || newStatus == DECLINED ||
                    newStatus == FAILED || newStatus == TIMEOUT;
            case SENT_TO_HOST -> newStatus == COMPLETED || newStatus == APPROVED ||
                    newStatus == DECLINED || newStatus == FAILED ||
                    newStatus == TIMEOUT;
            case COMPLETED, APPROVED -> newStatus == REVERSAL_PENDING || newStatus == REVERSED;
            case REVERSAL_PENDING -> newStatus == REVERSED || newStatus == FAILED;
            default -> false;
        };
    }
}
