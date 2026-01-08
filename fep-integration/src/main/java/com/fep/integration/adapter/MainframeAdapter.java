package com.fep.integration.adapter;

import com.fep.integration.converter.Iso8583ToMainframeConverter;
import com.fep.integration.converter.MainframeToIso8583Converter;
import com.fep.integration.model.MainframeRequest;
import com.fep.integration.model.MainframeResponse;
import com.fep.integration.mq.client.MqTemplate;
import com.fep.integration.mq.exception.MqException;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for mainframe core banking system via IBM MQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainframeAdapter implements CoreSystemAdapter {

    private final MqTemplate mqTemplate;
    private final Iso8583ToMainframeConverter toMainframeConverter;
    private final MainframeToIso8583Converter toIso8583Converter;

    @Override
    public TransactionResponse process(TransactionRequest request) {
        String txnId = request.getTransactionId();
        log.info("[{}] Processing transaction via Mainframe Adapter", txnId);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Convert TransactionRequest to ISO 8583 (if not already)
            Iso8583Message requestMessage = convertToIso8583(request);

            // Step 2: Convert ISO 8583 to Mainframe format
            MainframeRequest mainframeRequest = toMainframeConverter.convert(requestMessage);

            // Step 3: Send to mainframe via MQ and wait for response
            log.debug("[{}] Sending to mainframe MQ: payload length={}", 
                    txnId, mainframeRequest.getRawPayload().length());

            String rawResponse = mqTemplate.sendAndReceive(mainframeRequest.getRawPayload());

            log.debug("[{}] Received from mainframe MQ: payload length={}", 
                    txnId, rawResponse.length());

            // Step 4: Parse mainframe response
            MainframeResponse mainframeResponse = toIso8583Converter.parseCobolFormat(rawResponse);

            // Step 5: Convert mainframe response to ISO 8583
            Iso8583Message responseMessage = toIso8583Converter.convert(mainframeResponse);

            // Step 6: Convert ISO 8583 to TransactionResponse
            TransactionResponse response = convertToTransactionResponse(responseMessage, mainframeResponse);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] Mainframe transaction completed: code={}, duration={}ms",
                    txnId, mainframeResponse.getResponseCode(), duration);

            return response;

        } catch (MqException e) {
            log.error("[{}] MQ error occurred: {}", txnId, e.getMessage(), e);
            return createErrorResponse(request, "91", "MQ communication error: " + e.getMessage());

        } catch (Exception e) {
            log.error("[{}] Unexpected error in mainframe adapter: {}", txnId, e.getMessage(), e);
            return createErrorResponse(request, "96", "System error: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            log.debug("Performing mainframe health check");

            // Send a simple echo/ping message to mainframe
            MainframeRequest pingRequest = MainframeRequest.builder()
                    .transactionCode("9999")  // Echo/ping code
                    .transactionId("HEALTHCHECK")
                    .rawPayload(buildPingMessage())
                    .build();

            String response = mqTemplate.sendAndReceive(pingRequest.getRawPayload(), 5000);

            boolean healthy = response != null && !response.isEmpty();
            log.debug("Mainframe health check result: {}", healthy ? "OK" : "FAILED");

            return healthy;

        } catch (Exception e) {
            log.warn("Mainframe health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getAdapterName() {
        return "MAINFRAME-MQ-ADAPTER";
    }

    /**
     * Converts TransactionRequest to ISO 8583 message.
     */
    private Iso8583Message convertToIso8583(TransactionRequest request) {
        // Build ISO message from request fields
        Iso8583Message iso8583 = new Iso8583Message();
        iso8583.setMti(determineMti(request));
        iso8583.setField(2, request.getPan());                  // PAN
        iso8583.setField(3, request.getProcessingCode());       // Processing code
        iso8583.setField(4, String.format("%012d", request.getAmountInMinorUnits())); // Amount in cents
        iso8583.setField(11, request.getStan());                // STAN
        iso8583.setField(37, request.getRrn());                 // RRN
        iso8583.setField(41, request.getTerminalId());          // Terminal ID
        iso8583.setField(42, request.getMerchantId());          // Merchant ID
        iso8583.setField(49, request.getCurrencyCode());        // Currency code
        iso8583.setField(102, request.getSourceAccount());      // Account ID 1
        iso8583.setField(103, request.getDestinationAccount()); // Account ID 2

        return iso8583;
    }

    /**
     * Determines MTI based on transaction type.
     */
    private String determineMti(TransactionRequest request) {
        return switch (request.getTransactionType()) {
            case WITHDRAWAL, DEPOSIT -> "0200";  // Financial transaction
            case TRANSFER -> "0220";             // Transfer
            case BALANCE_INQUIRY -> "0100";      // Authorization/inquiry
            case REVERSAL -> "0400";             // Reversal
            default -> "0200";
        };
    }

    /**
     * Converts ISO 8583 and MainframeResponse to TransactionResponse.
     */
    private TransactionResponse convertToTransactionResponse(
            Iso8583Message iso8583, MainframeResponse mainframeResponse) {

        return TransactionResponse.builder()
                .responseCode(mainframeResponse.getResponseCode())
                .responseDescription(mainframeResponse.getResponseMessage())
                .approved(mainframeResponse.isSuccess())
                .authorizationCode(mainframeResponse.getAuthorizationCode())
                .hostReferenceNumber(mainframeResponse.getReferenceNumber())
                .ledgerBalance(mainframeResponse.getBalance() != null ?
                        java.math.BigDecimal.valueOf(mainframeResponse.getBalance()) : null)
                .availableBalance(mainframeResponse.getAvailableBalance() != null ?
                        java.math.BigDecimal.valueOf(mainframeResponse.getAvailableBalance()) : null)
                .additionalData(mainframeResponse.getAccountName())
                .hostDateTime(mainframeResponse.getResponseTime())
                .build();
    }

    /**
     * Creates error response.
     */
    private TransactionResponse createErrorResponse(TransactionRequest request,
                                                     String code, String message) {
        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(code)
                .responseDescription(message)
                .approved(false)
                .build();
    }

    /**
     * Builds a ping message for health check.
     */
    private String buildPingMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("9999");  // Ping transaction code
        sb.append("HEALTHCHECK".formatted("%-20s"));  // Transaction ID
        sb.append("00000000000000");  // Timestamp placeholder
        // Pad to minimum length
        while (sb.length() < 100) {
            sb.append(" ");
        }
        return sb.toString();
    }
}
