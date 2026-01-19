package com.fep.transaction.bpmn.service;

import com.fep.common.event.FiscResponseEvent;
import com.fep.common.event.FiscResponseEvent.ResponseType;
import com.fep.transaction.bpmn.handler.FiscResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * FISC 通訊服務
 *
 * <p>封裝與 FISC 的通訊邏輯，提供給 BPMN Delegate 使用。
 * 此服務透過 Spring Events 與 fep-communication 模組解耦。
 *
 * <p>職責：
 * <ul>
 *   <li>提供 {@code sendAsync(message, processId)} 方法給 BPMN delegate 使用</li>
 *   <li>註冊 STAN 到 {@link FiscResponseHandler} 進行關聯</li>
 *   <li>發布 {@link FiscResponseEvent} 供回應處理</li>
 * </ul>
 *
 * <p>設計說明：
 * <ul>
 *   <li>由於 fep-transaction 不能依賴 fep-communication，此服務不直接使用
 *       FiscDualChannelClient，而是透過事件機制與其通訊</li>
 *   <li>實際的發送邏輯會由 fep-application 或 fep-communication 中的
 *       FiscClientBridge 處理</li>
 * </ul>
 *
 * <p>流程：
 * <pre>
 * SendToFiscDelegate
 *        │
 *        ▼
 * FiscCommunicationService.sendAsync()
 *        │
 *        ├── 1. 註冊 STAN → ProcessId (FiscResponseHandler)
 *        │
 *        ├── 2. 呼叫 fiscClientBridge.sendMessage()
 *        │       └── FiscDualChannelClient.sendAndReceive()
 *        │
 *        └── 3. 等待回應或超時
 *                └── 收到回應後發布 FiscResponseEvent
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiscCommunicationService {

    private final FiscResponseHandler fiscResponseHandler;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * FISC Client Bridge (由外部注入)
     * 此介面用於解耦 fep-transaction 與 fep-communication
     */
    private volatile FiscClientBridge fiscClientBridge;

    /**
     * 預設 FISC 通道 ID
     */
    private static final String DEFAULT_FISC_CHANNEL = "FISC_INTERBANK_V1";

    /**
     * 設定 FISC Client Bridge
     *
     * <p>此方法由 BpmnIntegrationConfig 呼叫，注入實際的 FISC 通訊橋接器
     *
     * @param bridge FISC Client Bridge 實作
     */
    public void setFiscClientBridge(FiscClientBridge bridge) {
        this.fiscClientBridge = bridge;
        log.info("已設定 FiscClientBridge");
    }

    /**
     * 非同步發送訊息至 FISC
     *
     * <p>此方法會：
     * <ol>
     *   <li>註冊 STAN 與流程的關聯</li>
     *   <li>透過 FiscClientBridge 發送訊息</li>
     *   <li>返回 Future 供呼叫者等待</li>
     * </ol>
     *
     * @param messageData 訊息資料 (序列化的 Iso8583Message)
     * @param processId BPMN 流程實例 ID
     * @param stan STAN (System Trace Audit Number)
     * @return CompletableFuture 包含回應資料
     */
    public CompletableFuture<FiscResponse> sendAsync(byte[] messageData, String processId, String stan) {
        return sendAsync(messageData, processId, stan, DEFAULT_FISC_CHANNEL);
    }

    /**
     * 非同步發送訊息至指定 FISC 通道
     *
     * @param messageData 訊息資料
     * @param processId 流程實例 ID
     * @param stan STAN
     * @param channelId 通道 ID
     * @return CompletableFuture 包含回應資料
     */
    public CompletableFuture<FiscResponse> sendAsync(byte[] messageData, String processId,
                                                      String stan, String channelId) {
        log.info("準備發送至 FISC: processId={}, STAN={}, channel={}",
                processId, stan, channelId);

        // 1. 註冊 STAN 與流程的關聯
        fiscResponseHandler.registerStan(stan, processId);

        // 2. 檢查 bridge 是否已設定
        if (fiscClientBridge == null) {
            log.error("FiscClientBridge 未設定");
            fiscResponseHandler.unregisterStan(stan);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("FiscClientBridge not configured"));
        }

        // 3. 透過 bridge 發送訊息
        try {
            CompletableFuture<FiscResponse> future = fiscClientBridge.sendMessage(
                    channelId, messageData, stan);

            // 設定回應處理
            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    log.error("FISC 通訊失敗: STAN={}, error={}",
                            stan, throwable.getMessage());
                    // 不移除 STAN 映射，讓超時處理機制來處理
                } else if (response != null) {
                    log.info("收到 FISC 回應: STAN={}, RC={}", stan, response.getResponseCode());
                    // 發布回應事件
                    publishFiscResponse(stan, response, channelId);
                }
            });

            return future;

        } catch (Exception e) {
            log.error("發送至 FISC 失敗: STAN={}, error={}", stan, e.getMessage(), e);
            fiscResponseHandler.unregisterStan(stan);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 發送訊息並等待回應（同步版本）
     *
     * @param messageData 訊息資料
     * @param processId 流程實例 ID
     * @param stan STAN
     * @param timeoutMs 超時時間（毫秒）
     * @return FISC 回應
     * @throws Exception 如果發送失敗或超時
     */
    public FiscResponse sendAndWait(byte[] messageData, String processId,
                                    String stan, long timeoutMs) throws Exception {
        return sendAsync(messageData, processId, stan)
                .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 發布 FISC 回應事件
     */
    private void publishFiscResponse(String stan, FiscResponse response, String channelId) {
        FiscResponseEvent event = FiscResponseEvent.builder()
                .source(this)
                .responseType(determineResponseType(response.getMti()))
                .mti(response.getMti())
                .stan(stan)
                .responseCode(response.getResponseCode())
                .rawMessage(response.getRawMessage())
                .channelId(channelId)
                .responseTime(System.currentTimeMillis())
                .authCode(response.getAuthCode())
                .build();

        log.debug("發布 FiscResponseEvent: STAN={}, RC={}", stan, response.getResponseCode());
        eventPublisher.publishEvent(event);
    }

    /**
     * 判斷回應類型
     */
    private ResponseType determineResponseType(String mti) {
        if (mti == null) {
            return ResponseType.UNKNOWN;
        }
        return switch (mti) {
            case "0210" -> ResponseType.FINANCIAL_RESPONSE;
            case "0410" -> ResponseType.REVERSAL_RESPONSE;
            case "0810" -> ResponseType.NETWORK_RESPONSE;
            default -> ResponseType.UNKNOWN;
        };
    }

    /**
     * 檢查 FISC 連線狀態
     *
     * @return true 如果連線可用
     */
    public boolean isConnected() {
        return isConnected(DEFAULT_FISC_CHANNEL);
    }

    /**
     * 檢查指定通道的連線狀態
     *
     * @param channelId 通道 ID
     * @return true 如果連線可用
     */
    public boolean isConnected(String channelId) {
        if (fiscClientBridge == null) {
            return false;
        }
        return fiscClientBridge.isConnected(channelId);
    }

    // ==================== Inner Classes & Interfaces ====================

    /**
     * FISC 回應
     */
    @lombok.Data
    @lombok.Builder
    public static class FiscResponse {
        private String mti;
        private String stan;
        private String responseCode;
        private String authCode;
        private byte[] rawMessage;
        private long responseTimeMs;
    }

    /**
     * FISC Client Bridge 介面
     *
     * <p>此介面用於解耦 fep-transaction 與 fep-communication。
     * 實作類別由 fep-application 提供，注入到此服務中。
     */
    public interface FiscClientBridge {

        /**
         * 發送訊息至 FISC
         *
         * @param channelId 通道 ID
         * @param messageData 訊息資料
         * @param stan STAN
         * @return CompletableFuture 包含回應
         */
        CompletableFuture<FiscResponse> sendMessage(String channelId, byte[] messageData, String stan);

        /**
         * 檢查連線狀態
         *
         * @param channelId 通道 ID
         * @return true 如果連線可用
         */
        boolean isConnected(String channelId);
    }
}
