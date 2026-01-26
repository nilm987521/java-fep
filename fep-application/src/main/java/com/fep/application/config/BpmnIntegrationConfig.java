package com.fep.application.config;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.handler.BpmnServerMessageHandler;
import com.fep.communication.handler.ServerMessageHandler;
import com.fep.communication.manager.DynamicConnectionManager;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.transform.MessageTransformer;
import com.fep.transaction.bpmn.config.ProcessRoutingProperties;
import com.fep.transaction.bpmn.service.FiscCommunicationService;
import com.fep.transaction.bpmn.service.FiscCommunicationService.FiscClientBridge;
import com.fep.transaction.bpmn.service.FiscCommunicationService.FiscResponse;
import com.fep.transaction.bpmn.service.ProcessRouterService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * BPMN 整合配置
 *
 * <p>此配置類別負責整合 fep-communication 和 fep-transaction 模組，
 * 實現 MessageHandler 與 BPMN 流程的整合。
 *
 * <p><b>完全配置化設計</b>：
 * <ul>
 *   <li>所有 MTI（包含 0800 網路管理）統一走 BPMN 流程</li>
 *   <li>具體走哪個流程，完全由 {@code ProcessRoutingProperties} 決定</li>
 *   <li>新增 MTI 時，只需在 application.yml 加入規則 + 部署對應 BPMN 流程</li>
 * </ul>
 *
 * <p>整合架構：
 * <pre>
 * ATM/POS Request (任意 MTI)
 *         ↓
 * BpmnServerMessageHandler.handleMessage()
 *         ↓
 * ProcessRouterService.resolveProcessKey(channelId, mti, processingCode)
 *         ↓ (從 application.yml 規則中匹配)
 * TransactionRequestEvent (含 processKey)
 *         ↓
 * TransactionEventListener.handleTransactionRequest()
 *         ↓
 * Camunda: startProcessInstanceByKey(processKey, ...)
 *         ↓
 * 對應的 BPMN 流程執行
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ProcessRoutingProperties.class)
public class BpmnIntegrationConfig {

    private final ApplicationEventPublisher eventPublisher;
    private final FiscCommunicationService fiscCommunicationService;
    private final DynamicConnectionManager connectionManager;
    private final ProcessRouterService processRouterService;
    private final MessageTransformer messageTransformer;

    private Iso8583MessageFactory messageFactory;

    @Value("${fep.communication.fisc.default-channel:FISC_INTERBANK_V1}")
    private String defaultFiscChannel;

    @Value("${fep.communication.fisc.timeout-ms:30000}")
    private long fiscTimeoutMs;

    /**
     * 建立 Iso8583MessageFactory Bean
     *
     * <p>提供 ISO 8583 電文的解析與組裝功能，包含：
     * <ul>
     *   <li>欄位長度補齊（固定長度欄位補零/補空白）</li>
     *   <li>LLVAR/LLLVAR 長度前綴處理</li>
     *   <li>BCD/ASCII/BINARY 編碼轉換</li>
     * </ul>
     */
    @Bean
    public Iso8583MessageFactory iso8583MessageFactory() {
        // 在這裡初始化並保存引用，確保 @PostConstruct 可以使用
        this.messageFactory = new Iso8583MessageFactory();
        return this.messageFactory;
    }

    /**
     * 初始化 BPMN 整合
     */
    @PostConstruct
    public void init() {
        log.info("初始化 BPMN 整合配置（完全配置化模式）");

        // messageFactory 已在 @Bean 方法中初始化
        if (this.messageFactory == null) {
            this.messageFactory = new Iso8583MessageFactory();
        }

        // 設定 FiscClientBridge
        FiscClientBridge bridge = createFiscClientBridge();
        fiscCommunicationService.setFiscClientBridge(bridge);
        log.info("已設定 FiscClientBridge: defaultChannel={}, timeout={}ms",
                defaultFiscChannel, fiscTimeoutMs);

        // 設定 BPMN ServerMessageHandler
        configureForBpmnMode();

        log.info("BPMN 整合配置完成，所有 MTI 統一走 BPMN 流程，路由規則數: {}",
                processRouterService.getRuleCount());
    }

    /**
     * 建立 BPMN ServerMessageHandler Bean
     *
     * <p>所有交易訊息均透過 BPMN 流程處理，流程路由由 ProcessRouterService 決定
     */
    @Bean
    public ServerMessageHandler bpmnServerMessageHandler() {
        log.info("建立 BpmnServerMessageHandler（完全配置化模式）");

        // 建立 ProcessKeyResolver，使用 ProcessRouterService 進行路由
        BpmnServerMessageHandler.ProcessKeyResolver resolver =
                (channelId, mti, processingCode) ->
                        processRouterService.resolveProcessKey(channelId, mti, processingCode);

        return new BpmnServerMessageHandler(eventPublisher, resolver, messageTransformer);
    }

    /**
     * 設定 BPMN 模式
     */
    private void configureForBpmnMode() {
        log.info("設定為 BPMN 模式（所有 MTI 統一走 BPMN 流程）");

        // 建立 ProcessKeyResolver
        BpmnServerMessageHandler.ProcessKeyResolver resolver =
                (channelId, mti, processingCode) ->
                        processRouterService.resolveProcessKey(channelId, mti, processingCode);

        // 建立 BPMN Handler
        BpmnServerMessageHandler bpmnHandler = new BpmnServerMessageHandler(eventPublisher, resolver, messageTransformer);

        // 設定到 DynamicConnectionManager
        connectionManager.setServerMessageHandler(bpmnHandler);
        log.info("已設定 BpmnServerMessageHandler 到 DynamicConnectionManager，預設流程: {}",
                processRouterService.getDefaultProcessKey());
    }

    /**
     * 建立 FiscClientBridge
     *
     * <p>此 Bridge 連接 fep-transaction 和 fep-communication，
     * 讓 BPMN Delegate 可以透過它發送訊息到 FISC。
     *
     * <p><b>優化：直接發送 byte[]</b>
     * <br>使用 {@code sendRawAndReceive()} 方法直接發送已組裝的 byte[]，
     * 避免不必要的反序列化/序列化：
     * <pre>
     * 原本流程（有重複序列化）：
     *   AssembleDelegate: Iso8583Message → byte[]
     *   Bridge: byte[] → Iso8583Message (反序列化)
     *   Client: Iso8583Message → byte[] (重複序列化)
     *
     * 優化後流程：
     *   AssembleDelegate: Iso8583Message → byte[]
     *   Bridge: byte[] → 直接發送
     * </pre>
     */
    private FiscClientBridge createFiscClientBridge() {
        return new FiscClientBridge() {
            @Override
            public CompletableFuture<FiscResponse> sendMessage(String channelId,
                                                                byte[] messageData,
                                                                String stan) {
                log.debug("FiscClientBridge.sendMessage: channel={}, STAN={}, size={} bytes",
                        channelId, stan, messageData != null ? messageData.length : 0);

                // 取得 FISC 連線
                FiscDualChannelClient client = getFiscClient(channelId);
                if (client == null) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("FISC client not available: " + channelId));
                }

                if (!client.isConnected()) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("FISC client not connected: " + channelId));
                }

                if (messageData == null || messageData.length == 0) {
                    return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Message data is empty"));
                }

                // 直接發送 byte[]，避免反序列化
                return client.sendRawAndReceive(messageData, stan)
                        .orTimeout(fiscTimeoutMs, TimeUnit.MILLISECONDS)
                        .thenApply(responseBytes -> {
                            // 解析回應以取得必要欄位
                            Iso8583Message response = parseResponseForFields(responseBytes);
                            return FiscResponse.builder()
                                    .mti(response != null ? response.getMti() : null)
                                    .stan(response != null ? response.getFieldAsString(11) : stan)
                                    .responseCode(response != null ? response.getFieldAsString(39) : null)
                                    .authCode(response != null ? response.getFieldAsString(38) : null)
                                    .rawMessage(responseBytes)
                                    .responseTimeMs(System.currentTimeMillis())
                                    .build();
                        });
            }

            @Override
            public boolean isConnected(String channelId) {
                String channel = (channelId != null && !channelId.isEmpty())
                        ? channelId : defaultFiscChannel;

                Optional<FiscDualChannelClient> client = connectionManager.getConnection(channel);
                return client.map(FiscDualChannelClient::isConnected).orElse(false);
            }
        };
    }

    /**
     * 解析回應電文以取得必要欄位（MTI, STAN, RC, AuthCode）
     *
     * <p>此方法只解析回應一次，用於建構 FiscResponse
     */
    private Iso8583Message parseResponseForFields(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return messageFactory.parse(data);
        } catch (Exception e) {
            log.warn("解析回應電文失敗: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取得 FISC Client
     */
    private FiscDualChannelClient getFiscClient(String channelId) {
        String channel = (channelId != null && !channelId.isEmpty())
                ? channelId : defaultFiscChannel;

        return connectionManager.getConnection(channel).orElse(null);
    }

}
