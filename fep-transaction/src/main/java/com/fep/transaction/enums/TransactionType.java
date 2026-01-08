package com.fep.transaction.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Transaction types for FISC interbank operations.
 *
 * <p>Processing Code (Field 3) format: TTAASS
 * <ul>
 *   <li>TT: Transaction Type (00=Purchase, 01=Withdrawal, 31=Balance Inquiry, etc.)</li>
 *   <li>AA: From Account Type (00=Default, 10=Savings, 20=Checking, 30=Credit)</li>
 *   <li>SS: To Account Type</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum TransactionType {

    // ATM Transactions
    WITHDRAWAL("01", "Cash Withdrawal", "提款"),
    DEPOSIT("21", "Cash Deposit", "存款"),
    BALANCE_INQUIRY("31", "Balance Inquiry", "餘額查詢"),
    TRANSFER("40", "Fund Transfer", "轉帳"),
    BILL_PAYMENT("50", "Bill Payment", "繳費"),

    // Purchase Transactions
    PURCHASE("00", "Purchase", "消費"),
    PURCHASE_WITH_CASHBACK("09", "Purchase with Cashback", "消費加提款"),

    // Reversal/Void
    REVERSAL("20", "Reversal", "沖正"),
    VOID("02", "Void", "取消"),

    // Administrative
    PIN_CHANGE("90", "PIN Change", "密碼變更"),
    MINI_STATEMENT("38", "Mini Statement", "交易明細查詢"),

    // Mobile/Digital
    QR_PAYMENT("26", "QR Code Payment", "QR碼支付"),
    P2P_TRANSFER("27", "Person to Person Transfer", "個人對個人轉帳"),

    // E-Ticket/Stored Value Card
    E_TICKET_TOPUP("51", "E-Ticket Top-up", "電子票證加值"),

    // Cardless Transactions
    CARDLESS_WITHDRAWAL("28", "Cardless Withdrawal", "無卡提款"),

    // Taiwan Pay
    TAIWAN_PAY("29", "Taiwan Pay", "台灣Pay"),

    // Cross-Border Payment
    CROSS_BORDER_PAYMENT("52", "Cross-Border Payment", "跨境支付"),

    // Foreign Currency Exchange
    CURRENCY_EXCHANGE("53", "Currency Exchange", "外幣兌換"),

    // E-Wallet
    E_WALLET_TOPUP("54", "E-Wallet Top-up", "電子錢包加值"),
    E_WALLET_PAYMENT("55", "E-Wallet Payment", "電子錢包支付"),
    E_WALLET_TRANSFER("56", "E-Wallet Transfer", "電子錢包轉帳");

    private final String code;
    private final String description;
    private final String chineseDescription;

    private static final Map<String, TransactionType> CODE_MAP = new HashMap<>();

    static {
        for (TransactionType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    /**
     * Gets the TransactionType from code.
     *
     * @param code the 2-digit transaction type code
     * @return the TransactionType, or null if not found
     */
    public static TransactionType fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * Extracts transaction type code from processing code (field 3).
     *
     * @param processingCode the 6-digit processing code
     * @return the TransactionType, or null if not found
     */
    public static TransactionType fromProcessingCode(String processingCode) {
        if (processingCode == null || processingCode.length() < 2) {
            return null;
        }
        return fromCode(processingCode.substring(0, 2));
    }

    /**
     * Checks if this transaction type requires PIN verification.
     *
     * @return true if PIN is required
     */
    public boolean requiresPin() {
        return switch (this) {
            case WITHDRAWAL, TRANSFER, BALANCE_INQUIRY, PURCHASE, PURCHASE_WITH_CASHBACK,
                 PIN_CHANGE, QR_PAYMENT, P2P_TRANSFER -> true;
            default -> false;
        };
    }

    /**
     * Checks if this transaction type involves money movement.
     *
     * @return true if money is moved
     */
    public boolean isMonetaryTransaction() {
        return switch (this) {
            case WITHDRAWAL, DEPOSIT, TRANSFER, PURCHASE, PURCHASE_WITH_CASHBACK,
                 BILL_PAYMENT, QR_PAYMENT, P2P_TRANSFER, E_TICKET_TOPUP,
                 CARDLESS_WITHDRAWAL, TAIWAN_PAY, CROSS_BORDER_PAYMENT,
                 CURRENCY_EXCHANGE, E_WALLET_TOPUP, E_WALLET_PAYMENT,
                 E_WALLET_TRANSFER -> true;
            default -> false;
        };
    }
}
