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
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BpmnServerMessageHandler Tests")
class BpmnServerMessageHandlerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DefaultServerMessageHandler defaultHandler;

    @Mock
    private FiscDualChannelServer server;

    private BpmnServerMessageHandler handler;

    private static final String TEST_CHANNEL_ID = "ATM_FISC_V1";
    private static final String TEST_CLIENT_ID = "127.0.0.1:12345";
    private static final String TEST_STAN = "123456";

    @BeforeEach
    void setUp() {
        handler = new BpmnServerMessageHandler(eventPublisher, defaultHandler);
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
    @DisplayName("handleMessage - 0800 Network Management")
    class NetworkManagementTests {

        @Test
        @DisplayName("should delegate 0800 to default handler")
        void shouldDelegateToDefaultHandler() {
            // Given
            Iso8583Message request = createRequest("0800", "000000");
            ServerMessageHandler.ServerMessageContext context = createContext(request);

            // When
            handler.handleMessage(context);

            // Then
            verify(defaultHandler).handleMessage(context);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("handleMessage - Unknown MTI")
    class UnknownMtiTests {

        @Test
        @DisplayName("should send error response for unknown MTI")
        void shouldSendErrorResponseForUnknownMti() {
            // Given
            Iso8583Message request = createRequest("9999", "000000");
            TestServerMessageContext context = new TestServerMessageContext(request);

            // When
            handler.handleMessage(context);

            // Then
            assertThat(context.responseSent).isTrue();
            assertThat(context.sentResponse.getFieldAsString(39)).isEqualTo("12"); // Invalid transaction
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
