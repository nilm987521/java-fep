package com.fep.transaction.scheduled;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Recurrence types for scheduled transfers.
 */
@Getter
@RequiredArgsConstructor
public enum RecurrenceType {

    /** One-time transfer */
    ONE_TIME("O", "One Time", "單次"),

    /** Daily recurring transfer */
    DAILY("D", "Daily", "每日"),

    /** Weekly recurring transfer */
    WEEKLY("W", "Weekly", "每週"),

    /** Monthly recurring transfer */
    MONTHLY("M", "Monthly", "每月");

    private final String code;
    private final String description;
    private final String chineseDescription;

    public static RecurrenceType fromCode(String code) {
        for (RecurrenceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
