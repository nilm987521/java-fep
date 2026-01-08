package com.fep.integration.converter;

import com.fep.integration.model.MainframeRequest;
import com.fep.message.iso8583.Iso8583Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converts ISO 8583 message to mainframe COBOL format.
 */
@Slf4j
@Component
public class Iso8583ToMainframeConverter implements MessageConverter<Iso8583Message, MainframeRequest> {

    private static final Charset BIG5 = Charset.forName("Big5");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public MainframeRequest convert(Iso8583Message iso8583) {
        log.debug("Converting ISO 8583 to Mainframe format: MTI={}", iso8583.getMti());

        MainframeRequest request = MainframeRequest.builder()
                .transactionId(extractTransactionId(iso8583))
                .transactionCode(mapTransactionCode(iso8583.getMti()))
                .accountNumber(iso8583.getFieldAsString(2))  // PAN
                .cardNumber(iso8583.getFieldAsString(2))
                .amount(parseAmount(iso8583.getFieldAsString(4)))
                .currencyCode(iso8583.getFieldAsString(49) != null ? iso8583.getFieldAsString(49) : "TWD")
                .terminalId(iso8583.getFieldAsString(41))
                .merchantId(iso8583.getFieldAsString(42))
                .transactionTime(parseDateTime(iso8583.getFieldAsString(7)))
                .field1(iso8583.getFieldAsString(102))  // Account ID 1
                .field2(iso8583.getFieldAsString(103))  // Account ID 2
                .field3(iso8583.getFieldAsString(62))   // Additional data
                .build();

        // Build raw payload in COBOL Copybook format
        String rawPayload = buildCobolFormat(request, iso8583);
        request.setRawPayload(rawPayload);

        log.debug("Converted to Mainframe request: txnId={}, code={}, payload length={}",
                request.getTransactionId(), request.getTransactionCode(), rawPayload.length());

        return request;
    }

    @Override
    public boolean supports(Class<?> sourceType) {
        return Iso8583Message.class.isAssignableFrom(sourceType);
    }

    /**
     * Extracts transaction ID from ISO 8583 message.
     */
    private String extractTransactionId(Iso8583Message iso8583) {
        // Combination of RRN (Field 37) + STAN (Field 11)
        String rrn = iso8583.getFieldAsString(37);
        String stan = iso8583.getFieldAsString(11);
        if (rrn != null && stan != null) {
            return rrn + stan;
        }
        return stan != null ? stan : "UNKNOWN";
    }

    /**
     * Maps ISO 8583 MTI to mainframe transaction code.
     */
    private String mapTransactionCode(String mti) {
        if (mti == null) {
            return "0000";
        }

        return switch (mti) {
            case "0200" -> "3001";  // Financial transaction request -> Withdrawal
            case "0210" -> "3001";  // Financial transaction response
            case "0100" -> "3002";  // Authorization request
            case "0110" -> "3002";  // Authorization response
            case "0220" -> "3003";  // Transfer
            case "0400" -> "9001";  // Reversal
            case "0800" -> "9999";  // Network management
            default -> "0000";       // Unknown
        };
    }

    /**
     * Parses amount from ISO 8583 field (12 digits, right-aligned, zero-padded).
     */
    private Long parseAmount(String amountField) {
        if (amountField == null || amountField.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(amountField.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountField, e);
            return 0L;
        }
    }

    /**
     * Parses date/time from ISO 8583 field 7 (MMDDhhmmss).
     */
    private LocalDateTime parseDateTime(String dateTimeField) {
        if (dateTimeField == null || dateTimeField.length() < 10) {
            return LocalDateTime.now();
        }
        try {
            // Field 7: MMDDhhmmss (10 digits)
            int month = Integer.parseInt(dateTimeField.substring(0, 2));
            int day = Integer.parseInt(dateTimeField.substring(2, 4));
            int hour = Integer.parseInt(dateTimeField.substring(4, 6));
            int minute = Integer.parseInt(dateTimeField.substring(6, 8));
            int second = Integer.parseInt(dateTimeField.substring(8, 10));

            return LocalDateTime.now()
                    .withMonth(month)
                    .withDayOfMonth(day)
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(second);
        } catch (Exception e) {
            log.warn("Failed to parse date/time: {}", dateTimeField, e);
            return LocalDateTime.now();
        }
    }

    /**
     * Builds COBOL Copybook format message.
     * This is a simplified example - actual format should match mainframe COBOL structure.
     */
    private String buildCobolFormat(MainframeRequest request, Iso8583Message iso8583) {
        StringBuilder sb = new StringBuilder();

        // Header (Fixed length fields typical in mainframe systems)
        appendField(sb, request.getTransactionCode(), 4);      // Transaction code (4 bytes)
        appendField(sb, request.getTransactionId(), 20);       // Transaction ID (20 bytes)
        appendField(sb, formatDateTime(request.getTransactionTime()), 14);  // DateTime (14 bytes)

        // Body
        appendField(sb, request.getCardNumber(), 19);          // Card number (19 bytes)
        appendField(sb, request.getAccountNumber(), 16);       // Account number (16 bytes)
        appendField(sb, formatAmount(request.getAmount()), 12); // Amount (12 bytes)
        appendField(sb, request.getCurrencyCode(), 3);         // Currency (3 bytes)
        appendField(sb, request.getTerminalId(), 8);           // Terminal ID (8 bytes)
        appendField(sb, request.getMerchantId(), 15);          // Merchant ID (15 bytes)

        // Additional fields
        appendField(sb, request.getField1(), 30);              // Field1 (30 bytes)
        appendField(sb, request.getField2(), 30);              // Field2 (30 bytes)
        appendField(sb, request.getField3(), 100);             // Field3 (100 bytes)

        // Trailer
        appendField(sb, "", 50);                                // Reserved (50 bytes)

        return sb.toString();
    }

    /**
     * Appends field with fixed length (space-padded or truncated).
     */
    private void appendField(StringBuilder sb, String value, int length) {
        String padded = StringUtils.rightPad(value != null ? value : "", length);
        if (padded.length() > length) {
            padded = padded.substring(0, length);
        }
        sb.append(padded);
    }

    /**
     * Formats amount to fixed-length string (12 digits, right-aligned, zero-padded).
     */
    private String formatAmount(Long amount) {
        if (amount == null) {
            return "000000000000";
        }
        return String.format("%012d", amount);
    }

    /**
     * Formats LocalDateTime to yyyyMMddHHmmss.
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now();
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
