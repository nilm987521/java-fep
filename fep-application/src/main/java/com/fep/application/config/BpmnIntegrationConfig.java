package com.fep.application.config;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.handler.BpmnServerMessageHandler;
import com.fep.communication.handler.DefaultServerMessageHandler;
import com.fep.communication.handler.ServerMessageHandler;
import com.fep.communication.manager.DynamicConnectionManager;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.transaction.bpmn.service.FiscCommunicationService;
import com.fep.transaction.bpmn.service.FiscCommunicationService.FiscClientBridge;
import com.fep.transaction.bpmn.service.FiscCommunicationService.FiscResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * BPMN 整合配置
 *
 * <p>此配置類別負責整合 fep-communication 和 fep-transaction 模組，
 * 實現 MessageHandler 與 BPMN 流程的整合。
 *
 * <p>配置方式：
 * <pre>
 * fep:
 *   communication:
 *     server:
 *       message-handler: bpmn  # 使用 BPMN 整合模式
 * </pre>
 *
 * <p>整合架構：
 * <pre>
 * ATM/POS ──0200──► BpmnServerMessageHandler
 *                          │
 *                          ▼ (publish event)
 *                  TransactionRequestEvent
 *                          │
 *                          ▼
 *                  TransactionEventListener
 *                          │
 *                          ▼ (start process)
 *                  Camunda BPMN Process
 *                          │
 *                          ▼
 *                  SendToFiscDelegate
 *                          │
 *                          ▼ (via FiscClientBridge)
 *                  FiscDualChannelClient
 *                          │
 *                          ▼
 *                        FISC
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BpmnIntegrationConfig {

    private final ApplicationEventPublisher eventPublisher;
    private final FiscCommunicationService fiscCommunicationService;
    private final DynamicConnectionManager connectionManager;

    @Value("${fep.communication.server.message-handler:default}")
    private String messageHandlerType;

    @Value("${fep.communication.fisc.default-channel:FISC_INTERBANK_V1}")
    private String defaultFiscChannel;

    @Value("${fep.communication.fisc.timeout-ms:30000}")
    private long fiscTimeoutMs;

    /**
     * 初始化 BPMN 整合
     */
    @PostConstruct
    public void init() {
        log.info("初始化 BPMN 整合配置: messageHandlerType={}", messageHandlerType);

        // 設定 FiscClientBridge
        FiscClientBridge bridge = createFiscClientBridge();
        fiscCommunicationService.setFiscClientBridge(bridge);
        log.info("已設定 FiscClientBridge: defaultChannel={}, timeout={}ms",
                defaultFiscChannel, fiscTimeoutMs);

        // 設定 ServerMessageHandler
        if ("bpmn".equalsIgnoreCase(messageHandlerType)) {
            configureForBpmnMode();
        } else {
            configureForDefaultMode();
        }
    }

    /**
     * 建立 BPMN ServerMessageHandler Bean
     *
     * <p>當 fep.communication.server.message-handler=bpmn 時啟用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "fep.communication.server.message-handler", havingValue = "bpmn")
    public ServerMessageHandler bpmnServerMessageHandler() {
        log.info("建立 BpmnServerMessageHandler");
        DefaultServerMessageHandler defaultHandler = new DefaultServerMessageHandler();
        defaultHandler.setFiscClientProvider(this::getFiscClient);

        return new BpmnServerMessageHandler(eventPublisher, defaultHandler);
    }

    /**
     * 建立預設 ServerMessageHandler Bean
     *
     * <p>當 fep.communication.server.message-handler 不是 bpmn 時啟用
     */
    @Bean
    @ConditionalOnProperty(name = "fep.communication.server.message-handler",
            havingValue = "default", matchIfMissing = true)
    public ServerMessageHandler defaultServerMessageHandler() {
        log.info("建立 DefaultServerMessageHandler");
        DefaultServerMessageHandler handler = new DefaultServerMessageHandler();
        handler.setFiscClientProvider(this::getFiscClient);
        return handler;
    }

    /**
     * 設定 BPMN 模式
     */
    private void configureForBpmnMode() {
        log.info("設定為 BPMN 模式");

        // 建立 BPMN Handler
        DefaultServerMessageHandler defaultHandler = new DefaultServerMessageHandler();
        defaultHandler.setFiscClientProvider(this::getFiscClient);

        BpmnServerMessageHandler bpmnHandler =
                new BpmnServerMessageHandler(eventPublisher, defaultHandler);

        // 設定到 DynamicConnectionManager
        connectionManager.setServerMessageHandler(bpmnHandler);
        log.info("已設定 BpmnServerMessageHandler 到 DynamicConnectionManager");
    }

    /**
     * 設定預設模式
     */
    private void configureForDefaultMode() {
        log.info("設定為預設模式");

        DefaultServerMessageHandler handler = new DefaultServerMessageHandler();
        handler.setFiscClientProvider(this::getFiscClient);

        connectionManager.setServerMessageHandler(handler);
        log.info("已設定 DefaultServerMessageHandler 到 DynamicConnectionManager");
    }

    /**
     * 建立 FiscClientBridge
     *
     * <p>此 Bridge 連接 fep-transaction 和 fep-communication，
     * 讓 BPMN Delegate 可以透過它發送訊息到 FISC。
     */
    private FiscClientBridge createFiscClientBridge() {
        return new FiscClientBridge() {
            @Override
            public CompletableFuture<FiscResponse> sendMessage(String channelId,
                                                                byte[] messageData,
                                                                String stan) {
                log.debug("FiscClientBridge.sendMessage: channel={}, STAN={}", channelId, stan);

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

                // 反序列化訊息
                Iso8583Message request;
                try {
                    request = deserializeMessage(messageData);
                    if (request == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Failed to deserialize message"));
                    }
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }

                // 發送訊息並等待回應
                return client.sendAndReceive(request)
                        .orTimeout(fiscTimeoutMs, TimeUnit.MILLISECONDS)
                        .thenApply(response -> {
                            return FiscResponse.builder()
                                    .mti(response.getMti())
                                    .stan(response.getFieldAsString(11))
                                    .responseCode(response.getFieldAsString(39))
                                    .authCode(response.getFieldAsString(38))
                                    .rawMessage(serializeMessage(response))
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
     * 取得 FISC Client
     */
    private FiscDualChannelClient getFiscClient(String channelId) {
        String channel = (channelId != null && !channelId.isEmpty())
                ? channelId : defaultFiscChannel;

        return connectionManager.getConnection(channel).orElse(null);
    }

    /**
     * 反序列化 ISO 8583 訊息
     */
    private Iso8583Message deserializeMessage(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Iso8583Message) ois.readObject();
        } catch (Exception e) {
            log.error("反序列化訊息失敗: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 序列化 ISO 8583 訊息
     */
    private byte[] serializeMessage(Iso8583Message message) {
        if (message == null) {
            return new byte[0];
        }
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(message);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("序列化訊息失敗: {}", e.getMessage());
            return new byte[0];
        }
    }
}
