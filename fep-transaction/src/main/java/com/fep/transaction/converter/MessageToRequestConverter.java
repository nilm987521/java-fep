package com.fep.transaction.converter;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Converts ISO 8583 messages to TransactionRequest objects.
 */
public class MessageToRequestConverter {

    private static final Logger log = LoggerFactory.getLogger(MessageToRequestConverter.class);

    /**
     * Converts an ISO 8583 message to a TransactionRequest.
     *
     * @param message the ISO 8583 message
     * @return the transaction request
     */
    public TransactionRequest convert(Iso8583Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        String txnId = generateTransactionId(message);

        log.debug("[{}] Converting ISO 8583 message MTI: {}", txnId, message.getMti());

        TransactionRequest.TransactionRequestBuilder builder = TransactionRequest.builder()
                .transactionId(txnId);

        // Field 2: Primary Account Number (PAN)
        String pan = message.getFieldAsString(2);
        if (pan != null) {
            builder.pan(pan);
        }

        // Field 3: Processing Code
        String processingCode = message.getFieldAsString(3);
        if (processingCode != null) {
            builder.processingCode(processingCode);
            builder.transactionType(parseTransactionType(processingCode));
            builder.sourceAccountType(AccountType.getSourceAccountType(processingCode));
            builder.destinationAccountType(AccountType.getDestAccountType(processingCode));
        }

        // Field 4: Transaction Amount (in minor units)
        String amountStr = message.getFieldAsString(4);
        if (amountStr != null) {
            BigDecimal amount = parseAmount(amountStr);
            builder.amount(amount);
        }

        // Field 11: System Trace Audit Number (STAN)
        builder.stan(message.getFieldAsString(11));

        // Field 12 + 13: Local Transaction Time and Date
        builder.transactionDateTime(parseTransactionDateTime(message));

        // Field 14: Card Expiration Date (YYMM)
        builder.expirationDate(message.getFieldAsString(14));

        // Field 32: Acquiring Institution ID Code
        builder.acquiringBankCode(message.getFieldAsString(32));

        // Field 35: Track 2 Data
        builder.track2Data(message.getFieldAsString(35));

        // Field 37: Retrieval Reference Number (RRN)
        builder.rrn(message.getFieldAsString(37));

        // Field 41: Card Acceptor Terminal ID
        builder.terminalId(message.getFieldAsString(41));

        // Field 42: Card Acceptor ID Code (Merchant ID)
        builder.merchantId(message.getFieldAsString(42));

        // Field 49: Currency Code
        String currencyCode = message.getFieldAsString(49);
        if (currencyCode != null) {
            builder.currencyCode(currencyCode);
        }

        // Field 52: PIN Data (encrypted)
        builder.pinBlock(message.getFieldAsString(52));

        // Field 100: Receiving Institution ID (for transfers)
        String destBank = message.getFieldAsString(100);
        if (destBank != null) {
            builder.destinationBankCode(destBank);
        }

        // Field 102: Account Identification 1 (Source Account)
        builder.sourceAccount(message.getFieldAsString(102));

        // Field 103: Account Identification 2 (Destination Account)
        builder.destinationAccount(message.getFieldAsString(103));

        // Field 48 or 62: Additional Data
        String additionalData = message.getFieldAsString(48);
        if (additionalData == null) {
            additionalData = message.getFieldAsString(62);
        }
        builder.additionalData(additionalData);

        // Field 90: Original Data Elements (for reversals)
        String originalData = message.getFieldAsString(90);
        if (originalData != null && originalData.length() >= 16) {
            // Original data contains original MTI (4) + STAN (6) + date/time (10) + ...
            builder.originalTransactionId(originalData.substring(4, 10)); // Original STAN
        }

        // Determine channel from terminal ID pattern
        builder.channel(determineChannel(message.getFieldAsString(41)));

        // Set request time
        builder.requestTime(LocalDateTime.now());

        TransactionRequest request = builder.build();

        log.debug("[{}] Converted to TransactionRequest: type={}, amount={}",
                txnId, request.getTransactionType(), request.getAmount());

        return request;
    }

    /**
     * Generates a transaction ID.
     */
    private String generateTransactionId(Iso8583Message message) {
        // Try to use trace ID from message if available
        if (message.getTraceId() != null) {
            return message.getTraceId();
        }

        // Generate based on RRN + STAN if available
        String rrn = message.getFieldAsString(37);
        String stan = message.getFieldAsString(11);
        if (rrn != null && stan != null) {
            return rrn + stan;
        }

        // Fall back to UUID
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * Parses the transaction type from processing code.
     */
    private TransactionType parseTransactionType(String processingCode) {
        if (processingCode == null || processingCode.length() < 2) {
            return null;
        }
        return TransactionType.fromCode(processingCode.substring(0, 2));
    }

    /**
     * Parses amount from ISO 8583 format (12-digit, implied 2 decimals).
     */
    private BigDecimal parseAmount(String amountStr) {
        try {
            // Amount is in minor units (cents/分)
            long minorUnits = Long.parseLong(amountStr);
            return BigDecimal.valueOf(minorUnits).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Invalid amount format: {}", amountStr);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Parses transaction date/time from fields 12 and 13.
     */
    private LocalDateTime parseTransactionDateTime(Iso8583Message message) {
        String time = message.getFieldAsString(12); // HHMMSS
        String date = message.getFieldAsString(13); // MMDD

        if (time == null || date == null) {
            return LocalDateTime.now();
        }

        try {
            int year = LocalDateTime.now().getYear();
            int month = Integer.parseInt(date.substring(0, 2));
            int day = Integer.parseInt(date.substring(2, 4));
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = Integer.parseInt(time.substring(2, 4));
            int second = Integer.parseInt(time.substring(4, 6));

            return LocalDateTime.of(year, month, day, hour, minute, second);
        } catch (Exception e) {
            log.warn("Invalid date/time format: date={}, time={}", date, time);
            return LocalDateTime.now();
        }
    }

    /**
     * Determines channel from terminal ID pattern.
     */
    private String determineChannel(String terminalId) {
        if (terminalId == null) {
            return "UNKNOWN";
        }

        // Common patterns:
        // ATM terminals often start with "ATM" or have specific prefixes
        // POS terminals often start with "POS" or merchant-specific codes
        String upper = terminalId.toUpperCase();

        if (upper.startsWith("ATM")) {
            return "ATM";
        } else if (upper.startsWith("POS")) {
            return "POS";
        } else if (upper.startsWith("MOB") || upper.startsWith("APP")) {
            return "MOBILE";
        } else if (upper.startsWith("WEB") || upper.startsWith("IB")) {
            return "INTERNET";
        } else {
            return "OTHER";
        }
    }
}
