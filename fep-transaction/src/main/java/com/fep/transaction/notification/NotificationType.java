package com.fep.transaction.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Types of transaction notifications.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /** Transaction completed successfully */
    TRANSACTION_SUCCESS("交易成功", "Transaction Successful"),

    /** Transaction failed */
    TRANSACTION_FAILED("交易失敗", "Transaction Failed"),

    /** Transaction pending/processing */
    TRANSACTION_PENDING("交易處理中", "Transaction Pending"),

    /** Large amount transaction alert */
    LARGE_AMOUNT_ALERT("大額交易提醒", "Large Amount Alert"),

    /** International transaction alert */
    INTERNATIONAL_ALERT("跨境交易提醒", "International Transaction Alert"),

    /** Account balance low warning */
    LOW_BALANCE_WARNING("餘額不足提醒", "Low Balance Warning"),

    /** Unusual activity detected */
    SECURITY_ALERT("異常交易警示", "Security Alert"),

    /** Daily transaction summary */
    DAILY_SUMMARY("每日交易摘要", "Daily Transaction Summary"),

    /** OTP verification code */
    OTP_CODE("驗證碼", "OTP Verification Code"),

    /** Password change notification */
    PASSWORD_CHANGED("密碼變更通知", "Password Changed"),

    /** Card blocked notification */
    CARD_BLOCKED("卡片凍結通知", "Card Blocked");

    private final String chineseTitle;
    private final String englishTitle;

    /**
     * Gets the title based on locale.
     */
    public String getTitle(boolean chinese) {
        return chinese ? chineseTitle : englishTitle;
    }

    /**
     * Checks if this notification type is security-related.
     */
    public boolean isSecurityRelated() {
        return this == SECURITY_ALERT || this == CARD_BLOCKED ||
               this == PASSWORD_CHANGED || this == OTP_CODE;
    }

    /**
     * Checks if this notification type should be sent immediately (high priority).
     */
    public boolean isHighPriority() {
        return this == OTP_CODE || this == SECURITY_ALERT ||
               this == CARD_BLOCKED || this == TRANSACTION_SUCCESS ||
               this == TRANSACTION_FAILED;
    }
}
