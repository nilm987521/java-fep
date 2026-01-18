package com.fep.communication.handler;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Default implementation of ServerMessageHandler.
 *
 * <p>This handler processes incoming messages based on MTI:
 * <ul>
 *   <li>0200 (Financial Request) - Forward to FISC or process locally</li>
 *   <li>0400 (Reversal Request) - Forward to FISC or process locally</li>
 *   <li>0800 (Network Management) - Process locally (echo/status)</li>
 * </ul>
 *
 * <p>Response MTI convention:
 * <ul>
 *   <li>Request MTI + 10 = Response MTI</li>
 *   <li>0200 → 0210</li>
 *   <li>0400 → 0410</li>
 *   <li>0800 → 0810</li>
 * </ul>
 */
@Slf4j
public class DefaultServerMessageHandler implements ServerMessageHandler {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    /**
     * Function to get FISC client for forwarding transactions.
     * If null, transactions are processed locally (echo mode).
     */
    @Setter
    private Function<String, FiscDualChannelClient> fiscClientProvider;

    /**
     * Timeout for waiting FISC response (milliseconds).
     */
    @Setter
    private long fiscResponseTimeoutMs = 30000;

    @Override
    public void handleMessage(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String mti = request.getMti();
        String channelId = context.getChannelId();
        String clientId = context.getClientId();

        log.info("[{}] Processing message from {}: MTI={}, STAN={}",
                channelId, clientId, mti, request.getFieldAsString(11));

        try {
            switch (mti) {
                case "0200" -> handleFinancialRequest(context);
                case "0400" -> handleReversalRequest(context);
                case "0800" -> handleNetworkManagement(context);
                default -> handleUnknownMti(context);
            }
        } catch (Exception e) {
            log.error("[{}] Error processing message from {}: {}",
                    channelId, clientId, e.getMessage(), e);
            sendErrorResponse(context, "96"); // System malfunction
        }
    }

    /**
     * Handles 0200 Financial Request (withdrawal, transfer, inquiry).
     */
    private void handleFinancialRequest(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String processingCode = request.getFieldAsString(3);

        log.debug("[{}] Financial request: processingCode={}",
                context.getChannelId(), processingCode);

        if (fiscClientProvider != null) {
            // Forward to FISC
            forwardToFisc(context);
        } else {
            // Echo mode - process locally
            Iso8583Message response = createResponse(request, "0210", "00");
            context.sendResponse(response);
            log.info("[{}] Sent echo response to {}: MTI=0210, RC=00",
                    context.getChannelId(), context.getClientId());
        }
    }

    /**
     * Handles 0400 Reversal Request.
     */
    private void handleReversalRequest(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();

        log.debug("[{}] Reversal request: originalSTAN={}",
                context.getChannelId(), request.getFieldAsString(11));

        if (fiscClientProvider != null) {
            forwardToFisc(context);
        } else {
            // Echo mode
            Iso8583Message response = createResponse(request, "0410", "00");
            context.sendResponse(response);
            log.info("[{}] Sent reversal response to {}: MTI=0410, RC=00",
                    context.getChannelId(), context.getClientId());
        }
    }

    /**
     * Handles 0800 Network Management (echo test, sign-on, key exchange).
     */
    private void handleNetworkManagement(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String processingCode = request.getFieldAsString(3);

        log.debug("[{}] Network management: processingCode={}",
                context.getChannelId(), processingCode);

        // Network management is always processed locally
        Iso8583Message response = createResponse(request, "0810", "00");
        context.sendResponse(response);

        log.info("[{}] Sent network response to {}: MTI=0810, RC=00",
                context.getChannelId(), context.getClientId());
    }

    /**
     * Handles unknown MTI.
     */
    private void handleUnknownMti(ServerMessageContext context) {
        String mti = context.getMessage().getMti();
        log.warn("[{}] Unknown MTI from {}: {}",
                context.getChannelId(), context.getClientId(), mti);

        // Calculate response MTI (add 10)
        String responseMti = calculateResponseMti(mti);
        Iso8583Message response = createResponse(context.getMessage(), responseMti, "12"); // Invalid transaction
        context.sendResponse(response);
    }

    /**
     * Forwards request to FISC and sends response back to client.
     */
    private void forwardToFisc(ServerMessageContext context) {
        String channelId = context.getChannelId();
        String clientId = context.getClientId();
        Iso8583Message request = context.getMessage();

        // Get FISC client
        FiscDualChannelClient fiscClient = fiscClientProvider.apply("FISC_INTERBANK_V1");
        if (fiscClient == null || !fiscClient.isConnected()) {
            log.error("[{}] FISC client not available, returning error to {}", channelId, clientId);
            sendErrorResponse(context, "91"); // Issuer unavailable
            return;
        }

        log.info("[{}] Forwarding to FISC: MTI={}, STAN={}",
                channelId, request.getMti(), request.getFieldAsString(11));

        // Forward asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return fiscClient.sendAndReceive(request)
                        .get(fiscResponseTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("[{}] FISC request failed: {}", channelId, e.getMessage());
                return null;
            }
        }).thenAccept(fiscResponse -> {
            if (fiscResponse != null) {
                // Forward FISC response back to ATM
                context.sendResponse(fiscResponse);
                log.info("[{}] Forwarded FISC response to {}: MTI={}, RC={}",
                        channelId, clientId, fiscResponse.getMti(),
                        fiscResponse.getFieldAsString(39));
            } else {
                // FISC timeout or error
                sendErrorResponse(context, "68"); // Response received too late
            }
        });
    }

    /**
     * Creates a response message based on the request.
     */
    private Iso8583Message createResponse(Iso8583Message request, String responseMti, String responseCode) {
        Iso8583Message response = new Iso8583Message();
        response.setMti(responseMti);

        // Copy key fields from request
        copyField(request, response, 2);  // PAN
        copyField(request, response, 3);  // Processing Code
        copyField(request, response, 4);  // Amount
        copyField(request, response, 11); // STAN
        copyField(request, response, 41); // Terminal ID
        copyField(request, response, 42); // Merchant ID

        // Set response-specific fields
        response.setField(39, responseCode);

        // Set current time/date
        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

        // Set authorization code for approved transactions
        if ("00".equals(responseCode)) {
            response.setField(38, generateAuthCode());
        }

        // Ensure required fields have default values if missing
        // This handles cases where bitmap parsing didn't include all fields
        ensureRequiredField(response, 3, "000000");    // Processing Code
        ensureRequiredField(response, 11, "000000");   // STAN
        ensureRequiredField(response, 41, "UNKNOWN ");  // Terminal ID (8 chars)

        return response;
    }

    /**
     * Ensures a required field has a value, setting default if missing.
     */
    private void ensureRequiredField(Iso8583Message message, int fieldNum, String defaultValue) {
        if (message.getField(fieldNum) == null) {
            message.setField(fieldNum, defaultValue);
        }
    }

    /**
     * Copies a field from source to target if present.
     */
    private void copyField(Iso8583Message source, Iso8583Message target, int fieldNum) {
        Object value = source.getField(fieldNum);
        if (value != null) {
            target.setField(fieldNum, value);
        }
    }

    /**
     * Calculates response MTI (request MTI + 10).
     */
    private String calculateResponseMti(String requestMti) {
        try {
            int mti = Integer.parseInt(requestMti);
            return String.format("%04d", mti + 10);
        } catch (NumberFormatException e) {
            return "0210"; // Default to financial response
        }
    }

    /**
     * Generates a 6-character authorization code.
     */
    private String generateAuthCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * Sends an error response.
     */
    private void sendErrorResponse(ServerMessageContext context, String responseCode) {
        Iso8583Message request = context.getMessage();
        String responseMti = calculateResponseMti(request.getMti());
        Iso8583Message response = createResponse(request, responseMti, responseCode);
        context.sendResponse(response);

        log.warn("[{}] Sent error response to {}: MTI={}, RC={}",
                context.getChannelId(), context.getClientId(), responseMti, responseCode);
    }
}
