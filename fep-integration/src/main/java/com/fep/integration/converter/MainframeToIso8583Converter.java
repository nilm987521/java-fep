package com.fep.integration.converter;

import com.fep.integration.model.MainframeResponse;
import com.fep.message.iso8583.Iso8583Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converts mainframe COBOL format response to ISO 8583 message.
 */
@Slf4j
@Component
public class MainframeToIso8583Converter implements MessageConverter<MainframeResponse, Iso8583Message> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    @Override
    public Iso8583Message convert(MainframeResponse mainframeResponse) {
        log.debug("Converting Mainframe response to ISO 8583: txnId={}, code={}",
                mainframeResponse.getTransactionId(), mainframeResponse.getResponseCode());

        Iso8583Message iso8583 = new Iso8583Message();

        // Set MTI (0210 for financial response, 0110 for authorization response)
        iso8583.setMti("0210");

        // Set response code (Field 39)
        iso8583.setField(39, mapResponseCode(mainframeResponse.getResponseCode()));

        // Set authorization code if available (Field 38)
        if (mainframeResponse.getAuthorizationCode() != null) {
            iso8583.setField(38, mainframeResponse.getAuthorizationCode());
        }

        // Set reference number (Field 37 - RRN)
        if (mainframeResponse.getReferenceNumber() != null) {
            iso8583.setField(37, mainframeResponse.getReferenceNumber());
        }

        // Set balance fields if available
        if (mainframeResponse.getBalance() != null) {
            iso8583.setField(54, formatBalance(mainframeResponse.getBalance()));
        }

        // Set account name (Field 63)
        if (mainframeResponse.getAccountName() != null) {
            iso8583.setField(63, mainframeResponse.getAccountName());
        }

        // Set response time (Field 12 - Local transaction time)
        if (mainframeResponse.getResponseTime() != null) {
            iso8583.setField(12, formatTime(mainframeResponse.getResponseTime()));
            iso8583.setField(13, formatDate(mainframeResponse.getResponseTime()));
        }

        // Set additional fields
        if (mainframeResponse.getField1() != null) {
            iso8583.setField(102, mainframeResponse.getField1());
        }
        if (mainframeResponse.getField2() != null) {
            iso8583.setField(103, mainframeResponse.getField2());
        }
        if (mainframeResponse.getField3() != null) {
            iso8583.setField(62, mainframeResponse.getField3());
        }

        log.debug("Converted to ISO 8583: MTI={}, Field 39={}",
                iso8583.getMti(), iso8583.getField(39));

        return iso8583;
    }

    @Override
    public boolean supports(Class<?> sourceType) {
        return MainframeResponse.class.isAssignableFrom(sourceType);
    }

    /**
     * Maps mainframe response code to ISO 8583 response code.
     */
    private String mapResponseCode(String mainframeCode) {
        if (mainframeCode == null || mainframeCode.isEmpty()) {
            return "96"; // System error
        }

        // Map mainframe codes to ISO 8583 response codes
        return switch (mainframeCode) {
            case "00", "0000" -> "00";  // Approved
            case "51" -> "51";          // Insufficient funds
            case "54" -> "54";          // Expired card
            case "55" -> "55";          // Incorrect PIN
            case "57" -> "57";          // Transaction not permitted
            case "58" -> "58";          // Transaction not permitted to terminal
            case "61" -> "61";          // Exceeds withdrawal limit
            case "62" -> "62";          // Restricted card
            case "63" -> "63";          // Security violation
            case "65" -> "65";          // Exceeds withdrawal frequency
            case "75" -> "75";          // PIN tries exceeded
            case "91" -> "91";          // Issuer unavailable
            case "96" -> "96";          // System malfunction
            default -> "96";            // Default to system error
        };
    }

    /**
     * Formats balance to ISO 8583 field 54 format (C/D + amount).
     * C = Credit (positive balance), D = Debit (negative balance)
     */
    private String formatBalance(Long balance) {
        if (balance == null) {
            return "C000000000000";
        }

        char indicator = balance >= 0 ? 'C' : 'D';
        long absBalance = Math.abs(balance);
        return indicator + String.format("%012d", absBalance);
    }

    /**
     * Formats time to HHmmss format (Field 12).
     */
    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now();
        }
        return dateTime.format(TIME_FORMATTER);
    }

    /**
     * Formats date to MMdd format (Field 13).
     */
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now();
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Parses mainframe COBOL format response.
     * This method extracts fields from fixed-length COBOL structure.
     */
    public MainframeResponse parseCobolFormat(String rawPayload) {
        if (rawPayload == null || rawPayload.length() < 50) {
            throw new IllegalArgumentException("Invalid COBOL payload length");
        }

        log.debug("Parsing COBOL format response: length={}", rawPayload.length());

        int offset = 0;

        // Parse header
        String responseCode = extractField(rawPayload, offset, 4).trim();
        offset += 4;

        String transactionId = extractField(rawPayload, offset, 20).trim();
        offset += 20;

        String dateTimeStr = extractField(rawPayload, offset, 14).trim();
        offset += 14;

        // Parse body
        String referenceNumber = extractField(rawPayload, offset, 20).trim();
        offset += 20;

        String balanceStr = extractField(rawPayload, offset, 12).trim();
        offset += 12;

        String authCode = extractField(rawPayload, offset, 6).trim();
        offset += 6;

        String accountName = extractField(rawPayload, offset, 50).trim();
        offset += 50;

        String responseMessage = extractField(rawPayload, offset, 100).trim();
        offset += 100;

        // Parse additional fields
        String field1 = offset + 30 <= rawPayload.length() ? 
                extractField(rawPayload, offset, 30).trim() : null;
        offset += 30;

        String field2 = offset + 30 <= rawPayload.length() ? 
                extractField(rawPayload, offset, 30).trim() : null;
        offset += 30;

        String field3 = offset + 100 <= rawPayload.length() ? 
                extractField(rawPayload, offset, 100).trim() : null;

        // Build response object
        MainframeResponse response = MainframeResponse.builder()
                .transactionId(transactionId)
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .referenceNumber(referenceNumber)
                .balance(parseBalance(balanceStr))
                .authorizationCode(authCode.isEmpty() ? null : authCode)
                .accountName(accountName.isEmpty() ? null : accountName)
                .responseTime(parseDateTime(dateTimeStr))
                .field1(field1)
                .field2(field2)
                .field3(field3)
                .rawPayload(rawPayload)
                .build();

        log.debug("Parsed COBOL response: txnId={}, code={}, success={}",
                response.getTransactionId(), response.getResponseCode(), response.isSuccess());

        return response;
    }

    /**
     * Extracts field from fixed-length COBOL string.
     */
    private String extractField(String source, int offset, int length) {
        if (offset + length > source.length()) {
            return StringUtils.rightPad("", length);
        }
        return source.substring(offset, offset + length);
    }

    /**
     * Parses balance from string.
     */
    private Long parseBalance(String balanceStr) {
        if (balanceStr == null || balanceStr.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(balanceStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse balance: {}", balanceStr, e);
            return null;
        }
    }

    /**
     * Parses date/time from yyyyMMddHHmmss format.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.length() < 14) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date/time: {}", dateTimeStr, e);
            return LocalDateTime.now();
        }
    }
}
