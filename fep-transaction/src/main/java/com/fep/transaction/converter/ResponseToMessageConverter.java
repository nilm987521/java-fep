package com.fep.transaction.converter;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converts TransactionResponse to ISO 8583 response messages.
 */
public class ResponseToMessageConverter {

    private static final Logger log = LoggerFactory.getLogger(ResponseToMessageConverter.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMdd");

    /**
     * Converts a TransactionResponse to an ISO 8583 response message.
     *
     * @param request the original request
     * @param response the transaction response
     * @param originalMessage the original ISO 8583 request message (optional)
     * @return the ISO 8583 response message
     */
    public Iso8583Message convert(TransactionRequest request, TransactionResponse response,
                                  Iso8583Message originalMessage) {

        log.debug("[{}] Converting TransactionResponse to ISO 8583 message",
                response.getTransactionId());

        Iso8583Message responseMessage;

        if (originalMessage != null) {
            // Create response from original message (copies common fields)
            responseMessage = originalMessage.createResponse();
        } else {
            // Create new response message
            responseMessage = new Iso8583Message(MessageType.FINANCIAL_RESPONSE);
            populateFromRequest(responseMessage, request);
        }

        // Field 38: Authorization Identification Response (Auth Code)
        if (response.getAuthorizationCode() != null) {
            responseMessage.setField(38, response.getAuthorizationCode());
        }

        // Field 39: Response Code
        responseMessage.setField(39, response.getResponseCode());

        // Field 54: Additional Amounts (Balance info for inquiries)
        if (response.getAvailableBalance() != null || response.getLedgerBalance() != null) {
            String additionalAmounts = formatAdditionalAmounts(
                    response.getAvailableBalance(),
                    response.getLedgerBalance(),
                    response.getCurrencyCode());
            responseMessage.setField(54, additionalAmounts);
        }

        // Field 48/62: Additional Response Data
        if (response.getAdditionalData() != null) {
            responseMessage.setField(62, response.getAdditionalData());
        }

        // Set trace ID for logging
        responseMessage.setTraceId(response.getTransactionId());

        log.debug("[{}] Created ISO 8583 response: MTI={}, F39={}",
                response.getTransactionId(),
                responseMessage.getMti(),
                response.getResponseCode());

        return responseMessage;
    }

    /**
     * Populates response message fields from request when no original message available.
     */
    private void populateFromRequest(Iso8583Message message, TransactionRequest request) {
        if (request == null) {
            return;
        }

        // Field 2: PAN
        if (request.getPan() != null) {
            message.setField(2, request.getPan());
        }

        // Field 3: Processing Code
        if (request.getProcessingCode() != null) {
            message.setField(3, request.getProcessingCode());
        }

        // Field 4: Amount
        if (request.getAmount() != null) {
            message.setField(4, formatAmount(request.getAmount()));
        }

        // Field 11: STAN
        if (request.getStan() != null) {
            message.setField(11, request.getStan());
        }

        // Field 12 + 13: Date/Time
        LocalDateTime txnTime = request.getTransactionDateTime();
        if (txnTime != null) {
            message.setField(12, txnTime.format(TIME_FORMATTER));
            message.setField(13, txnTime.format(DATE_FORMATTER));
        }

        // Field 32: Acquiring Bank
        if (request.getAcquiringBankCode() != null) {
            message.setField(32, request.getAcquiringBankCode());
        }

        // Field 37: RRN
        if (request.getRrn() != null) {
            message.setField(37, request.getRrn());
        }

        // Field 41: Terminal ID
        if (request.getTerminalId() != null) {
            message.setField(41, request.getTerminalId());
        }

        // Field 42: Merchant ID
        if (request.getMerchantId() != null) {
            message.setField(42, request.getMerchantId());
        }

        // Field 49: Currency Code
        if (request.getCurrencyCode() != null) {
            message.setField(49, request.getCurrencyCode());
        }
    }

    /**
     * Formats amount for ISO 8583 (12 digits, implied 2 decimals).
     */
    private String formatAmount(BigDecimal amount) {
        long minorUnits = amount.multiply(BigDecimal.valueOf(100)).longValue();
        return String.format("%012d", minorUnits);
    }

    /**
     * Formats additional amounts for Field 54 (balance information).
     *
     * <p>Field 54 format: Account Type (2) + Amount Type (2) + Currency (3) +
     * Amount Sign (1) + Amount (12) = 20 chars per amount
     */
    private String formatAdditionalAmounts(BigDecimal availableBalance,
                                           BigDecimal ledgerBalance,
                                           String currencyCode) {
        StringBuilder sb = new StringBuilder();

        String currency = currencyCode != null ? currencyCode : "901"; // Default TWD

        // Available Balance (Amount Type: 01 = Available)
        if (availableBalance != null) {
            sb.append("10"); // Account Type: Savings
            sb.append("01"); // Amount Type: Available Balance
            sb.append(currency);
            sb.append(availableBalance.compareTo(BigDecimal.ZERO) >= 0 ? "C" : "D");
            sb.append(String.format("%012d",
                    availableBalance.abs().multiply(BigDecimal.valueOf(100)).longValue()));
        }

        // Ledger Balance (Amount Type: 02 = Ledger)
        if (ledgerBalance != null) {
            sb.append("10"); // Account Type: Savings
            sb.append("02"); // Amount Type: Ledger Balance
            sb.append(currency);
            sb.append(ledgerBalance.compareTo(BigDecimal.ZERO) >= 0 ? "C" : "D");
            sb.append(String.format("%012d",
                    ledgerBalance.abs().multiply(BigDecimal.valueOf(100)).longValue()));
        }

        return sb.toString();
    }
}
