package com.fep.transaction.exception;

import com.fep.common.exception.FepException;
import lombok.Getter;

/**
 * Exception thrown during transaction processing.
 */
@Getter
public class TransactionException extends FepException {

    private final String responseCode;

    public TransactionException(String errorCode, String errorMessage, String responseCode) {
        super(errorCode, errorMessage);
        this.responseCode = responseCode;
    }

    public TransactionException(String errorCode, String errorMessage, String responseCode, Throwable cause) {
        super(errorCode, errorMessage, cause);
        this.responseCode = responseCode;
    }

    public static TransactionException invalidRequest(String message) {
        return new TransactionException("TXN001", "Invalid request: " + message, "30");
    }

    public static TransactionException insufficientFunds() {
        return new TransactionException("TXN002", "Insufficient funds", "51");
    }

    public static TransactionException accountNotFound(String account) {
        return new TransactionException("TXN003", "Account not found: " + account, "14");
    }

    public static TransactionException invalidCard(String reason) {
        return new TransactionException("TXN004", "Invalid card: " + reason, "14");
    }

    public static TransactionException expiredCard() {
        return new TransactionException("TXN005", "Expired card", "54");
    }

    public static TransactionException invalidPin() {
        return new TransactionException("TXN006", "Invalid PIN", "55");
    }

    public static TransactionException exceedsLimit(String limitType) {
        return new TransactionException("TXN007", "Exceeds " + limitType + " limit", "61");
    }

    public static TransactionException restrictedCard() {
        return new TransactionException("TXN008", "Restricted card", "62");
    }

    public static TransactionException systemError(String message) {
        return new TransactionException("TXN009", "System error: " + message, "96");
    }

    public static TransactionException systemError(String message, Throwable cause) {
        return new TransactionException("TXN009", "System error: " + message, "96", cause);
    }

    public static TransactionException timeout() {
        return new TransactionException("TXN010", "Transaction timeout", "68");
    }

    public static TransactionException duplicateTransaction() {
        return new TransactionException("TXN011", "Duplicate transaction", "94");
    }

    public static TransactionException hostUnavailable() {
        return new TransactionException("TXN012", "Host unavailable", "91");
    }

    public static TransactionException invalidAmount() {
        return new TransactionException("TXN013", "Invalid amount", "13");
    }

    public static TransactionException transactionNotPermitted() {
        return new TransactionException("TXN014", "Transaction not permitted", "57");
    }

    public static TransactionException routingFailed(String message) {
        return new TransactionException("TXN015", "Routing failed: " + message, "92");
    }

    public static TransactionException unsupportedTransaction() {
        return new TransactionException("TXN016", "Unsupported transaction type", "40");
    }

    public static TransactionException invalidAccount(String message) {
        return new TransactionException("TXN017", "Invalid account: " + message, "14");
    }

    public static TransactionException invalidTransaction(String message) {
        return new TransactionException("TXN018", "Invalid transaction: " + message, "12");
    }

    public static TransactionException reversalNotFound() {
        return new TransactionException("TXN019", "Original transaction not found for reversal", "25");
    }

    public static TransactionException alreadyReversed() {
        return new TransactionException("TXN020", "Transaction already reversed", "26");
    }
}
