package com.fep.transaction.scheduled;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Status of a scheduled transfer.
 */
@Getter
@RequiredArgsConstructor
public enum ScheduledTransferStatus {

    /** Transfer is pending activation */
    PENDING("P", "Pending", "待啟用"),

    /** Transfer is active and will be executed on schedule */
    ACTIVE("A", "Active", "啟用中"),

    /** Transfer has been completed */
    COMPLETED("C", "Completed", "已完成"),

    /** Transfer failed */
    FAILED("F", "Failed", "失敗"),

    /** Transfer was cancelled by user */
    CANCELLED("X", "Cancelled", "已取消"),

    /** Transfer is suspended */
    SUSPENDED("S", "Suspended", "暫停");

    private final String code;
    private final String description;
    private final String chineseDescription;

    public static ScheduledTransferStatus fromCode(String code) {
        for (ScheduledTransferStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
