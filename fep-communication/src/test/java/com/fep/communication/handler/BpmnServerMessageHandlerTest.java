package com.fep.communication.handler;

import com.fep.common.event.TransactionRequestEvent;
import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BpmnServerMessageHandler.
 *
 * <p>測試完全配置化設計：
 * <ul>
 *   <li>所有 MTI（包含 0800 網路管理）統一走 BPMN 流程</li>
 *   <li>processKey 由 ProcessKeyResolver 解析</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BpmnServerMessageHandler Tests")
class BpmnServerMessageHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FiscDualChannelServer server;

    private BpmnServerMessageHandler handler;

    private static final String TEST_CHANNEL_ID = "ATM_FISC_V1";
    private static final String TEST_CLIENT_ID = "127.0.0.1:12345";
    private static final String TEST_STAN = "123456";
    private static final String DEFAULT_PROCESS_KEY = "Process_TransferRequest";
    private static final String NETWORK_PROCESS_KEY = "Process_NetworkManagement";

    @BeforeEach
    void setUp() {
        // 使用 ProcessKeyResolver lambda，模擬路由邏輯
        BpmnServerMessageHandler.ProcessKeyResolver resolver = (channelId, mti, processingCode) -> {
            if ("0800".equals(mti)) {
                return NETWORK_PROCESS_KEY;
            }
            return DEFAULT_PROCESS_KEY;
        };

        handler = new BpmnServerMessageHandler(eventPublisher, resolver);
    }

    @Nested
    @DisplayName("handleMessage - 0200 Financial Request")
    class FinancialRequestTests {

        @Test
        @DisplayName("should publish TransactionRequestEvent for 0200 transfer")
        void shouldPublishEventForTransfer() {
            // Given
            Iso8583Message request = createRequest("0200", "400000"); // 轉帳
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getMti()).isEqualTo("0200");
            assertThat(event.getStan()).isEqualTo(TEST_STAN);
            assertThat(event.getTransactionType()).isEqualTo(TransactionRequestEvent.TransactionType.TRANSFER);
            assertThat(event.getProcessKey()).isEqualTo(DEFAULT_PROCESS_KEY);
        }

        @Test
        @DisplayName("should publish TransactionRequestEvent for 0200 withdrawal")
        void shouldPublishEventForWithdrawal() {
            // Given
            Iso8583Message request = createRequest("0200", "010000"); // 提款
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getTransactionType()).isEqualTo(TransactionRequestEvent.TransactionType.WITHDRAWAL);
        }

        @Test
        @DisplayName("should publish TransactionRequestEvent for 0200 balance inquiry")
        void shouldPublishEventForBalanceInquiry() {
            // Given
            Iso8583Message request = createRequest("0200", "310000"); // 餘額查詢
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getTransactionType()).isEqualTo(TransactionRequestEvent.TransactionType.BALANCE_INQUIRY);
        }

        @Test
        @DisplayName("should include all relevant fields in event")
        void shouldIncludeAllRelevantFieldsInEvent() {
            // Given
            Iso8583Message request = createRequest("0200", "400000");
            request.setField(2, "1234567890123456"); // PAN
            request.setField(4, "000000010000");     // Amount
            request.setField(32, "812");             // Source bank
            request.setField(100, "013");            // Target bank
            request.setField(103, "9876543210987654"); // Target account

            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getPan()).isEqualTo("1234567890123456");
            assertThat(event.getAmount()).isEqualTo("000000010000");
            assertThat(event.getSourceBankCode()).isEqualTo("812");
            assertThat(event.getTargetBankCode()).isEqualTo("013");
            assertThat(event.getTargetAccount()).isEqualTo("9876543210987654");
        }

        @Test
        @DisplayName("should include response callback in event")
        void shouldIncludeResponseCallbackInEvent() {
            // Given
            Iso8583Message request = createRequest("0200", "400000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getResponseCallback()).isNotNull();
        }

        @Test
        @DisplayName("should register callback for pending response")
        void shouldRegisterCallbackForPendingResponse() {
            // Given
            Iso8583Message request = createRequest("0200", "400000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            assertThat(handler.getPendingCallbackCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should include processKey in event")
        void shouldIncludeProcessKeyInEvent() {
            // Given
            Iso8583Message request = createRequest("0200", "400000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getProcessKey()).isEqualTo(DEFAULT_PROCESS_KEY);
        }
    }

    @Nested
    @DisplayName("handleMessage - 0400 Reversal Request")
    class ReversalRequestTests {

        @Test
        @DisplayName("should publish TransactionRequestEvent for 0400 reversal")
        void shouldPublishEventForReversal() {
            // Given
            Iso8583Message request = createRequest("0400", "000000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getMti()).isEqualTo("0400");
            assertThat(event.getTransactionType()).isEqualTo(TransactionRequestEvent.TransactionType.REVERSAL);
        }
    }

    @Nested
    @DisplayName("handleMessage - 0800 Network Management (Now via BPMN)")
    class NetworkManagementTests {

        @Test
        @DisplayName("should publish TransactionRequestEvent for 0800 with network process key")
        void shouldPublishEventForNetworkManagement() {
            // Given
            Iso8583Message request = createRequest("0800", "300000"); // Echo Test
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then - 現在 0800 也走 BPMN 流程
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getMti()).isEqualTo("0800");
            assertThat(event.getProcessKey()).isEqualTo(NETWORK_PROCESS_KEY);
        }
    }

    @Nested
    @DisplayName("handleMessage - Unknown MTI (Still via BPMN with default process)")
    class UnknownMtiTests {

        @Test
        @DisplayName("should publish event with default process key for unknown MTI")
        void shouldPublishEventForUnknownMti() {
            // Given
            Iso8583Message request = createRequest("9999", "000000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then - 未知 MTI 也走 BPMN，使用預設流程
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getMti()).isEqualTo("9999");
            assertThat(event.getProcessKey()).isEqualTo(DEFAULT_PROCESS_KEY);
            assertThat(event.getTransactionType()).isEqualTo(TransactionRequestEvent.TransactionType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("sendResponseByStan Tests")
    class SendResponseByStanTests {

        @Test
        @DisplayName("should send response through callback")
        void shouldSendResponseThroughCallback() {
            // Given - 先發送請求以註冊 callback
            Iso8583Message request = createRequest("0200", "400000");
            TestServerMessageContext context = new TestServerMessageContext(request);

            handler.handleMessage(context);

            // When - 發送回應
            Iso8583Message response = new Iso8583Message();
            response.setMti("0210");
            response.setField(39, "00");

            boolean result = handler.sendResponseByStan(TEST_STAN, response);

            // Then
            assertThat(result).isTrue();
            assertThat(context.responseSent).isTrue();
        }

        @Test
        @DisplayName("should return false when STAN not found")
        void shouldReturnFalseWhenStanNotFound() {
            // Given
            Iso8583Message response = new Iso8583Message();
            response.setMti("0210");
            response.setField(39, "00");

            // When
            boolean result = handler.sendResponseByStan("UNKNOWN_STAN", response);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("ProcessKeyResolver Integration Tests")
    class ProcessKeyResolverTests {

        @Test
        @DisplayName("should use resolver to determine process key")
        void shouldUseResolverToDetermineProcessKey() {
            // Given - 使用自定義 resolver
            BpmnServerMessageHandler.ProcessKeyResolver customResolver =
                    (channelId, mti, processingCode) -> "Custom_Process_" + mti;

            BpmnServerMessageHandler customHandler =
                    new BpmnServerMessageHandler(eventPublisher, customResolver);

            Iso8583Message request = createRequest("0200", "400000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            customHandler.handleMessage(context);

            // Then
            ArgumentCaptor<TransactionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRequestEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            TransactionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getProcessKey()).isEqualTo("Custom_Process_0200");
        }
    }

    // ==================== Helper Methods ====================

    private Iso8583Message createRequest(String mti, String processingCode) {
        Iso8583Message message = new Iso8583Message();
        message.setMti(mti);
        message.setField(3, processingCode);
        message.setField(11, TEST_STAN);
        return message;
    }

    private ServerMessageHandler.ServerMessageContext createContext(Iso8583Message request) {
        return new TestServerMessageContext(request);
    }

    /**
     * Test implementation of ServerMessageContext.
     */
    private class TestServerMessageContext implements ServerMessageHandler.ServerMessageContext {
        private final Iso8583Message message;
        boolean responseSent = false;
        Iso8583Message sentResponse;

        TestServerMessageContext(Iso8583Message message) {
            this.message = message;
        }

        @Override
        public String getChannelId() {
            return TEST_CHANNEL_ID;
        }

        @Override
        public String getClientId() {
            return TEST_CLIENT_ID;
        }

        @Override
        public Iso8583Message getMessage() {
            return message;
        }

        @Override
        public FiscDualChannelServer getServer() {
            return server;
        }

        @Override
        public boolean sendResponse(Iso8583Message response) {
            responseSent = true;
            sentResponse = response;
            return true;
        }
    }
}
