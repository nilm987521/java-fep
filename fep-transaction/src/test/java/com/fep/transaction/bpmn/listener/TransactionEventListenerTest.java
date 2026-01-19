package com.fep.transaction.bpmn.listener;

import com.fep.common.event.FiscResponseEvent;
import com.fep.common.event.TransactionRequestEvent;
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

    @InjectMocks
    private TransactionEventListener listener;

    private static final String TEST_STAN = "123456";
    private static final String TEST_PROCESS_ID = "process-001";
    private static final String TEST_CHANNEL_ID = "ATM_FISC_V1";

    @Nested
    @DisplayName("handleTransactionRequest Tests")
    class HandleTransactionRequestTests {

        @Test
        @DisplayName("should start BPMN process when receiving transfer request")
        void shouldStartBpmnProcessForTransferRequest() {
            // Given
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            // When
            listener.handleTransactionRequest(event);

            // Then
            verify(processService).startTransferProcess(any());
            assertThat(listener.getProcessId(TEST_STAN)).isEqualTo(TEST_PROCESS_ID);
        }

        @Test
        @DisplayName("should register STAN to process mapping")
        void shouldRegisterStanToProcessMapping() {
            // Given
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = createTransactionRequestEvent(
                    TransactionRequestEvent.TransactionType.TRANSFER);

            // When
            listener.handleTransactionRequest(event);

            // Then
            assertThat(listener.getProcessId(TEST_STAN)).isEqualTo(TEST_PROCESS_ID);
            assertThat(listener.getStan(TEST_PROCESS_ID)).isEqualTo(TEST_STAN);
        }

        @Test
        @DisplayName("should register response callback")
        void shouldRegisterResponseCallback() {
            // Given
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);

            TransactionRequestEvent event = TransactionRequestEvent.builder()
                    .source(this)
                    .transactionType(TransactionRequestEvent.TransactionType.TRANSFER)
                    .mti("0200")
                    .stan(TEST_STAN)
                    .channelId(TEST_CHANNEL_ID)
                    .clientId("127.0.0.1:12345")
                    .processingCode("400000")
                    .amount("10000")
                    .pan("1234567890123456")
                    .targetAccount("9876543210987654")
                    .sourceBankCode("812")
                    .targetBankCode("013")
                    .responseCallback(data -> callbackCalled.set(true))
                    .build();

            // When
            listener.handleTransactionRequest(event);

            // Then - 可以透過 sendResponseToClientByStan 來驗證 callback 是否被註冊
            assertThat(listener.getPendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should increment pending count")
        void shouldIncrementPendingCount() {
            // Given
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);
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
        void setUp() {
            // Pre-register a mapping
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);
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
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            byte[] expectedData = new byte[]{1, 2, 3};

            TransactionRequestEvent event = TransactionRequestEvent.builder()
                    .source(this)
                    .transactionType(TransactionRequestEvent.TransactionType.TRANSFER)
                    .mti("0200")
                    .stan(TEST_STAN)
                    .channelId(TEST_CHANNEL_ID)
                    .clientId("127.0.0.1:12345")
                    .processingCode("400000")
                    .amount("10000")
                    .pan("1234567890123456")
                    .targetAccount("9876543210987654")
                    .sourceBankCode("812")
                    .targetBankCode("013")
                    .responseCallback(data -> {
                        callbackCalled.set(true);
                        assertThat(data).isEqualTo(expectedData);
                    })
                    .build();

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
            when(processService.startTransferProcess(any())).thenReturn(TEST_PROCESS_ID);

            TransactionRequestEvent event = TransactionRequestEvent.builder()
                    .source(this)
                    .transactionType(TransactionRequestEvent.TransactionType.TRANSFER)
                    .mti("0200")
                    .stan(TEST_STAN)
                    .channelId(TEST_CHANNEL_ID)
                    .clientId("127.0.0.1:12345")
                    .processingCode("400000")
                    .amount("10000")
                    .pan("1234567890123456")
                    .targetAccount("9876543210987654")
                    .sourceBankCode("812")
                    .targetBankCode("013")
                    .responseCallback(data -> {})
                    .build();

            listener.handleTransactionRequest(event);
            int countBefore = listener.getPendingCount();

            // When
            listener.sendResponseToClientByStan(TEST_STAN, new byte[]{1});

            // Then
            assertThat(listener.getPendingCount()).isEqualTo(countBefore - 1);
            assertThat(listener.getProcessId(TEST_STAN)).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private TransactionRequestEvent createTransactionRequestEvent(TransactionRequestEvent.TransactionType type) {
        return TransactionRequestEvent.builder()
                .source(this)
                .transactionType(type)
                .mti(type == TransactionRequestEvent.TransactionType.REVERSAL ? "0400" : "0200")
                .stan(TEST_STAN)
                .channelId(TEST_CHANNEL_ID)
                .clientId("127.0.0.1:12345")
                .processingCode("400000")
                .amount("10000")
                .pan("1234567890123456")
                .targetAccount("9876543210987654")
                .sourceBankCode("812")
                .targetBankCode("013")
                .responseCallback(data -> {})
                .build();
    }
}
