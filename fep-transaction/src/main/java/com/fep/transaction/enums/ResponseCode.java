package com.fep.transaction.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * ISO 8583 Response Codes (Field 39).
 * Standard response codes used in FISC transactions.
 */
@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    // Approval codes
    APPROVED("00", "Approved", "交易成功", true),
    APPROVED_WITH_ID("01", "Approved with ID", "需驗證身分後核准", true),
    APPROVED_PARTIAL("02", "Approved Partial", "部分金額核准", true),
    APPROVED_VIP("03", "Approved VIP", "VIP客戶核准", true),

    // Denial codes
    REFER_TO_ISSUER("01", "Refer to card issuer", "請聯絡發卡行", false),
    INVALID_MERCHANT("03", "Invalid merchant", "無效特約商店", false),
    PICKUP_CARD("04", "Pick up card", "沒收卡片", false),
    DO_NOT_HONOR("05", "Do not honor", "不予承兌", false),
    ERROR("06", "Error", "錯誤", false),
    INVALID_TRANSACTION("12", "Invalid transaction", "無效交易", false),
    INVALID_AMOUNT("13", "Invalid amount", "金額無效", false),
    INVALID_CARD_NUMBER("14", "Invalid card number", "卡號無效", false),
    NO_SUCH_ISSUER("15", "No such issuer", "無此發卡機構", false),
    FORMAT_ERROR("30", "Format error", "格式錯誤", false),

    // Processing errors
    LOST_CARD("41", "Lost card", "掛失卡", false),
    STOLEN_CARD("43", "Stolen card", "被竊卡", false),
    INSUFFICIENT_FUNDS("51", "Insufficient funds", "餘額不足", false),
    NO_CHECKING_ACCOUNT("52", "No checking account", "無支票帳戶", false),
    NO_SAVINGS_ACCOUNT("53", "No savings account", "無儲蓄帳戶", false),
    EXPIRED_CARD("54", "Expired card", "卡片過期", false),
    INCORRECT_PIN("55", "Incorrect PIN", "密碼錯誤", false),
    TRANSACTION_NOT_PERMITTED("57", "Transaction not permitted", "交易不允許", false),
    SUSPECTED_FRAUD("59", "Suspected fraud", "疑似詐欺", false),
    EXCEEDS_WITHDRAWAL_LIMIT("61", "Exceeds withdrawal limit", "超過提款限額", false),
    RESTRICTED_CARD("62", "Restricted card", "受限制的卡", false),
    SECURITY_VIOLATION("63", "Security violation", "安全違規", false),
    EXCEEDS_FREQUENCY_LIMIT("65", "Exceeds frequency limit", "超過交易次數限制", false),

    // Technical errors
    RESPONSE_RECEIVED_TOO_LATE("68", "Response received too late", "回應逾時", false),
    PIN_TRIES_EXCEEDED("75", "PIN tries exceeded", "密碼錯誤次數超過", false),
    ISSUER_INOPERATIVE("91", "Issuer or switch inoperative", "發卡行無法作業", false),
    DUPLICATE_TRANSACTION("94", "Duplicate transaction", "重複交易", false),
    SYSTEM_MALFUNCTION("96", "System malfunction", "系統故障", false);

    private final String code;
    private final String description;
    private final String chineseDescription;
    private final boolean approved;

    private static final Map<String, ResponseCode> CODE_MAP = new HashMap<>();

    static {
        for (ResponseCode rc : values()) {
            CODE_MAP.put(rc.code, rc);
        }
    }

    /**
     * Gets ResponseCode from code string.
     *
     * @param code the 2-character response code
     * @return the ResponseCode, or null if not found
     */
    public static ResponseCode fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * Checks if the response code indicates approval.
     *
     * @param code the response code
     * @return true if approved
     */
    public static boolean isApproved(String code) {
        return "00".equals(code);
    }
}
