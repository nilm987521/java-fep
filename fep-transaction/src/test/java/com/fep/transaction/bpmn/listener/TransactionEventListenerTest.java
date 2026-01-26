package com.fep.transaction.bpmn.listener;

import com.fep.common.event.FiscResponseEvent;
import com.fep.common.event.TransactionRequestEvent;
import com.fep.common.message.InternalMessage;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.transaction.bpmn.service.TransferProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionEventListener.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionEventListener Tests")
class TransactionEventListenerTest {

    @Mock
    private TransferProcessService processService;

    @Mock
    private Iso8583MessageFactory messageFactory;

    @InjectMocks
    private TransactionEventListener listener;

    private static final String TEST_STAN = "123456";
    private static final String TEST_PROCESS_ID = "process-001";
    private static final String TEST_CHANNEL_ID = "ATM_FISC_V1";
    private static final String TEST_PROCESS_KEY = "Process_TransferRequest";

    @Nested
    @DisplayName("handleTransactionRequest Tests")
    class HandleTransactionRequestTests {

        @Test
        @DisplayName("should start BPMN process when receiving transfer request with processKey")
        void shouldStartBpmnProcessForTransferRequest() {
            // Given - 使用新的 startProcessWithKey 方法（因為有 processKey）
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            // When
            listener.handleTransactionRequest(event);

            // Then - callbackKey 格式: channelId:clientId:stan
            String expectedCallbackKey = TransactionEventListener.generateCallbackKey(
                    TEST_CHANNEL_ID, "127.0.0.1:12345", TEST_STAN);
            verify(processService).startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any());
            assertThat(listener.getProcessId(expectedCallbackKey)).isEqualTo(TEST_PROCESS_ID);
        }

        @Test
        @DisplayName("should register STAN to process mapping")
        void shouldRegisterStanToProcessMapping() {
            // Given
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            // When
            listener.handleTransactionRequest(event);

            // Then - callbackKey 格式: channelId:clientId:stan
            String expectedCallbackKey = TransactionEventListener.generateCallbackKey(
                    TEST_CHANNEL_ID, "127.0.0.1:12345", TEST_STAN);
            assertThat(listener.getProcessId(expectedCallbackKey)).isEqualTo(TEST_PROCESS_ID);
            assertThat(listener.getCallbackKey(TEST_PROCESS_ID)).isEqualTo(expectedCallbackKey);
        }

        @Test
        @DisplayName("should register response callback")
        void shouldRegisterResponseCallback() {
            // Given
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);

            TransactionRequestEvent event = createTransactionRequestEventWithCallback(
                    TransactionRequestEvent.TransactionType.TRANSFER,
                    data -> callbackCalled.set(true));

            // When
            listener.handleTransactionRequest(event);

            // Then - 可以透過 sendResponseToClientByStan 來驗證 callback 是否被註冊
            assertThat(listener.getPendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should increment pending count")
        void shouldIncrementPendingCount() {
            // Given
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);
            int initialCount = listener.getPendingCount();

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            // When
            listener.handleTransactionRequest(event);

            // Then
            assertThat(listener.getPendingCount()).isEqualTo(initialCount + 1);
        }
    }

    @Nested
    @DisplayName("handleFiscResponse Tests")
    class HandleFiscResponseTests {

        @BeforeEach
        void setUp() throws Exception {
            // 測試傳統模式（非高 TPS 模式）- 使用反射設定 highTpsMode 為 false
            java.lang.reflect.Field highTpsModeField = TransactionEventListener.class.getDeclaredField("highTpsMode");
            highTpsModeField.setAccessible(true);
            highTpsModeField.setBoolean(listener, false);

            // 註冊 FISC STAN → CallbackKey 映射（模擬 AssembleMessageDelegate 的行為）
            // 在非高 TPS 模式下，handleFiscResponse 使用 fiscStanToCallbackKeyMap 來查找 callbackKey
            String callbackKey = TransactionEventListener.generateCallbackKey(
                    TEST_CHANNEL_ID, "127.0.0.1:12345", TEST_STAN);
            listener.registerFiscStanMapping(TEST_STAN, callbackKey);

            // Pre-register a mapping
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);
            listener.handleTransactionRequest(createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER));
        }

        @Test
        @DisplayName("should correlate message when receiving FISC response")
        void shouldCorrelateMessageForFiscResponse() {
            // Given
            FiscResponseEvent event = FiscResponseEvent.builder()
                    .source(this)
                    .responseType(FiscResponseEvent.ResponseType.FINANCIAL_RESPONSE)
                    .mti("0210")
                    .stan(TEST_STAN)
                    .responseCode("00")
                    .rawMessage(new byte[0])
                    .channelId(TEST_CHANNEL_ID)
                    .responseTime(System.currentTimeMillis())
                    .build();

            // When
            listener.handleFiscResponse(event);

            // Then
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(processService).correlateMessage(eq(TEST_PROCESS_ID), messageCaptor.capture(), any());
            assertThat(messageCaptor.getValue()).isEqualTo("FiscResponse");
        }

        @Test
        @DisplayName("should use correct message name for reversal response")
        void shouldUseCorrectMessageNameForReversalResponse() {
            // Given
            FiscResponseEvent event = FiscResponseEvent.builder()
                    .source(this)
                    .responseType(FiscResponseEvent.ResponseType.REVERSAL_RESPONSE)
                    .mti("0410")
                    .stan(TEST_STAN)
                    .responseCode("00")
                    .rawMessage(new byte[0])
                    .channelId(TEST_CHANNEL_ID)
                    .responseTime(System.currentTimeMillis())
                    .build();

            // When
            listener.handleFiscResponse(event);

            // Then
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(processService).correlateMessage(eq(TEST_PROCESS_ID), messageCaptor.capture(), any());
            assertThat(messageCaptor.getValue()).isEqualTo("ReversalResponse");
        }

        @Test
        @DisplayName("should pass response code in variables")
        @SuppressWarnings("unchecked")
        void shouldPassResponseCodeInVariables() {
            // Given
            FiscResponseEvent event = FiscResponseEvent.builder()
                    .source(this)
                    .responseType(FiscResponseEvent.ResponseType.FINANCIAL_RESPONSE)
                    .mti("0210")
                    .stan(TEST_STAN)
                    .responseCode("00")
                    .rawMessage(new byte[0])
                    .channelId(TEST_CHANNEL_ID)
                    .responseTime(System.currentTimeMillis())
                    .build();

            // When
            listener.handleFiscResponse(event);

            // Then
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(processService).correlateMessage(any(), any(), varsCaptor.capture());
            assertThat(varsCaptor.getValue()).containsEntry("responseCode", "00");
        }

        @Test
        @DisplayName("should ignore response when no matching STAN")
        void shouldIgnoreResponseWhenNoMatchingStan() {
            // Given - 清除預設註冊
            reset(processService);

            FiscResponseEvent event = FiscResponseEvent.builder()
                    .source(this)
                    .responseType(FiscResponseEvent.ResponseType.FINANCIAL_RESPONSE)
                    .mti("0210")
                    .stan("UNKNOWN_STAN")
                    .responseCode("00")
                    .rawMessage(new byte[0])
                    .channelId(TEST_CHANNEL_ID)
                    .responseTime(System.currentTimeMillis())
                    .build();

            // When
            listener.handleFiscResponse(event);

            // Then
            verify(processService, never()).correlateMessage(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("sendResponseToClient Tests")
    class SendResponseToClientTests {

        @Test
        @DisplayName("should send response via callback")
        void shouldSendResponseViaCallback() {
            // Given
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            byte[] expectedData = new byte[]{1, 2, 3};

            TransactionRequestEvent event = createTransactionRequestEventWithCallback(
                    TransactionRequestEvent.TransactionType.TRANSFER,
                    data -> {
                        callbackCalled.set(true);
                        assertThat(data).isEqualTo(expectedData);
                    });

            listener.handleTransactionRequest(event);

            // When
            boolean result = listener.sendResponseToClientByStan(TEST_STAN, expectedData);

            // Then
            assertThat(result).isTrue();
            assertThat(callbackCalled.get()).isTrue();
        }

        @Test
        @DisplayName("should return false when STAN not found")
        void shouldReturnFalseWhenStanNotFound() {
            // When
            boolean result = listener.sendResponseToClientByStan("UNKNOWN_STAN", new byte[]{1, 2, 3});

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should cleanup mappings after sending response")
        void shouldCleanupMappingsAfterSendingResponse() {
            // Given
            when(processService.startProcessWithKey(any(), eq(TEST_PROCESS_KEY), any(), any(), any(), any(), any()))
                    .thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            listener.handleTransactionRequest(event);
            int countBefore = listener.getPendingCount();

            // When
            listener.sendResponseToClientByStan(TEST_STAN, new byte[]{1});

            // Then
            assertThat(listener.getPendingCount()).isEqualTo(countBefore - 1);
            // callbackKey 格式: channelId:clientId:stan
            String expectedCallbackKey = TransactionEventListener.generateCallbackKey(
                    TEST_CHANNEL_ID, "127.0.0.1:12345", TEST_STAN);
            assertThat(listener.getProcessId(expectedCallbackKey)).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private TransactionRequestEvent createTransactionRequestEvent(TransactionRequestEvent.TransactionType type) {
        // 建立 InternalMessage
        InternalMessage internal = InternalMessage.builder()
                .messageType(type == TransactionRequestEvent.TransactionType.REVERSAL
                        ? InternalMessage.MessageType.REVERSAL_REQUEST
                        : InternalMessage.MessageType.FINANCIAL_REQUEST)
                .traceNumber(TEST_STAN)
                .transactionCode("400000")
                .transactionAmount(10000L)
                .cardNumber("1234567890123456")
                .sourceAccount("1234567890123456")
                .destinationAccount("9876543210987654")
                .sourceBankCode("812")
                .destinationBankCode("013")
                .sourceChannelId(TEST_CHANNEL_ID)
                .sourceClientId("127.0.0.1:12345")
                .build();

        return TransactionRequestEvent.builder()
                .source(this)
                .message(internal)
                .transactionType(type)
                .processKey(TEST_PROCESS_KEY)
                .responseCallback(data -> {})
                .build();
    }

    private TransactionRequestEvent createTransactionRequestEventWithCallback(
            TransactionRequestEvent.TransactionType type,
            Consumer<byte[]> callback) {
        // 建立 InternalMessage
        InternalMessage internal = InternalMessage.builder()
                .messageType(type == TransactionRequestEvent.TransactionType.REVERSAL
                        ? InternalMessage.MessageType.REVERSAL_REQUEST
                        : InternalMessage.MessageType.FINANCIAL_REQUEST)
                .traceNumber(TEST_STAN)
                .transactionCode("400000")
                .transactionAmount(10000L)
                .cardNumber("1234567890123456")
                .sourceAccount("1234567890123456")
                .destinationAccount("9876543210987654")
                .sourceBankCode("812")
                .destinationBankCode("013")
                .sourceChannelId(TEST_CHANNEL_ID)
                .sourceClientId("127.0.0.1:12345")
                .build();

        return TransactionRequestEvent.builder()
                .source(this)
                .message(internal)
                .transactionType(type)
                .processKey(TEST_PROCESS_KEY)
                .responseCallback(callback)
                .build();
    }
}
