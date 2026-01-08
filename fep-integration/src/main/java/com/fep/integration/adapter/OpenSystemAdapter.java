package com.fep.integration.adapter;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter for open system core banking via REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSystemAdapter implements CoreSystemAdapter {

    private final WebClient.Builder webClientBuilder;

    // Base URL should be configurable via properties
    private static final String BASE_URL = "http://localhost:8080/api/v1/cbs";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public TransactionResponse process(TransactionRequest request) {
        String txnId = request.getTransactionId();
        log.info("[{}] Processing transaction via Open System Adapter", txnId);

        long startTime = System.currentTimeMillis();

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(BASE_URL)
                    .build();

            // Determine endpoint based on transaction type
            String endpoint = determineEndpoint(request);

            // Build request payload
            Map<String, Object> payload = buildRequestPayload(request);

            log.debug("[{}] Sending to open system API: endpoint={}", txnId, endpoint);

            // Call REST API
            Mono<Map> responseMono = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        log.error("[{}] API error: status={}", txnId, clientResponse.statusCode());
                        return Mono.error(new RuntimeException("API error: " + clientResponse.statusCode()));
                    })
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT);

            // Block and get response
            Map<String, Object> apiResponse = responseMono.block();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] Open system transaction completed: duration={}ms", txnId, duration);

            return parseApiResponse(apiResponse);

        } catch (Exception e) {
            log.error("[{}] Open system API error: {}", txnId, e.getMessage(), e);
            return createErrorResponse("96", "API communication error: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            log.debug("Performing open system health check");

            WebClient webClient = webClientBuilder
                    .baseUrl(BASE_URL)
                    .build();

            Map<String, Object> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            boolean healthy = response != null && "UP".equals(response.get("status"));
            log.debug("Open system health check result: {}", healthy ? "OK" : "FAILED");

            return healthy;

        } catch (Exception e) {
            log.warn("Open system health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getAdapterName() {
        return "OPEN-SYSTEM-API-ADAPTER";
    }

    /**
     * Determines API endpoint based on transaction type.
     */
    private String determineEndpoint(TransactionRequest request) {
        return switch (request.getTransactionType()) {
            case WITHDRAWAL -> "/withdrawal";
            case DEPOSIT -> "/deposit";
            case TRANSFER -> "/transfer";
            case BALANCE_INQUIRY -> "/balance";
            case BILL_PAYMENT -> "/bill-payment";
            case QR_PAYMENT -> "/qr-payment";
            case P2P_TRANSFER -> "/p2p-transfer";
            default -> "/transaction";
        };
    }

    /**
     * Builds request payload for REST API.
     */
    private Map<String, Object> buildRequestPayload(TransactionRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", request.getTransactionId());
        payload.put("transactionType", request.getTransactionType().name());
        payload.put("cardNumber", request.getPan());
        payload.put("amount", request.getAmount());
        payload.put("currencyCode", request.getCurrencyCode());
        payload.put("fromAccount", request.getSourceAccount());
        payload.put("toAccount", request.getDestinationAccount());
        payload.put("terminalId", request.getTerminalId());
        payload.put("merchantId", request.getMerchantId());
        payload.put("stan", request.getStan());
        payload.put("rrn", request.getRrn());
        return payload;
    }

    /**
     * Parses API response to TransactionResponse.
     */
    private TransactionResponse parseApiResponse(Map<String, Object> apiResponse) {
        if (apiResponse == null) {
            return createErrorResponse("96", "Empty response from API");
        }

        String responseCode = getStringValue(apiResponse, "responseCode", "96");
        String responseMessage = getStringValue(apiResponse, "responseMessage", "Unknown");

        java.math.BigDecimal balance = null;
        Object balanceObj = apiResponse.get("balance");
        if (balanceObj instanceof Number) {
            balance = java.math.BigDecimal.valueOf(((Number) balanceObj).longValue());
        }

        java.math.BigDecimal availableBalance = null;
        Object availableBalanceObj = apiResponse.get("availableBalance");
        if (availableBalanceObj instanceof Number) {
            availableBalance = java.math.BigDecimal.valueOf(((Number) availableBalanceObj).longValue());
        }

        return TransactionResponse.builder()
                .responseCode(responseCode)
                .responseDescription(responseMessage)
                .approved("00".equals(responseCode))
                .authorizationCode(getStringValue(apiResponse, "authorizationCode", null))
                .hostReferenceNumber(getStringValue(apiResponse, "referenceNumber", null))
                .ledgerBalance(balance)
                .availableBalance(availableBalance)
                .additionalData(getStringValue(apiResponse, "accountName", null))
                .build();
    }

    /**
     * Gets string value from map with default.
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Creates error response.
     */
    private TransactionResponse createErrorResponse(String code, String message) {
        return TransactionResponse.builder()
                .responseCode(code)
                .responseDescription(message)
                .approved(false)
                .build();
    }
}
