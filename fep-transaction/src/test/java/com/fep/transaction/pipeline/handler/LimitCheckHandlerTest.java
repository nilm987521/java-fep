package com.fep.transaction.pipeline.handler;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.limit.LimitCheckResult;
import com.fep.transaction.limit.LimitManager;
import com.fep.transaction.limit.LimitType;
import com.fep.transaction.limit.TransactionLimit;
import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LimitCheckHandler.
 */
@ExtendWith(MockitoExtension.class)
class LimitCheckHandlerTest {

    @Mock
    private LimitManager limitManager;

    private LimitCheckHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LimitCheckHandler(limitManager);
    }

    @Nested
    @DisplayName("Handler Configuration Tests")
    class HandlerConfigurationTests {

        @Test
        @DisplayName("Should return VALIDATION stage")
        void shouldReturnValidationStage() {
            assertEquals(PipelineStage.VALIDATION, handler.getStage());
        }

        @Test
        @DisplayName("Should return order 200")
        void shouldReturnOrder200() {
            assertEquals(200, handler.getOrder());
        }
    }

    @Nested
    @DisplayName("Limit Check Pass Tests")
    class LimitCheckPassTests {

        @Test
        @DisplayName("Should pass when limit check succeeds")
        void shouldPassWhenLimitCheckSucceeds() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-001", request);

            LimitCheckResult passResult = LimitCheckResult.builder()
                    .passed(true)
                    .remainingAmount(new BigDecimal("100000"))
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(passResult);

            handler.handle(context);

            assertTrue(context.isContinueProcessing());
            assertNull(context.getResponse());
            assertEquals(passResult, context.getAttribute("limitCheckResult"));
            assertEquals(new BigDecimal("100000"), context.getAttribute("remainingLimit"));
        }

        @Test
        @DisplayName("Should store limit check result in context")
        void shouldStoreLimitCheckResultInContext() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            LimitCheckResult passResult = LimitCheckResult.builder()
                    .passed(true)
                    .remainingAmount(new BigDecimal("50000"))
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(passResult);

            handler.handle(context);

            assertNotNull(context.getAttribute("limitCheckResult"));
            assertEquals(new BigDecimal("50000"), context.getAttribute("remainingLimit"));
        }
    }

    @Nested
    @DisplayName("Limit Check Fail Tests")
    class LimitCheckFailTests {

        @Test
        @DisplayName("Should stop processing when limit exceeded")
        void shouldStopProcessingWhenLimitExceeded() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            LimitCheckResult failResult = LimitCheckResult.builder()
                    .passed(false)
                    .message("Single transaction limit exceeded")
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(failResult);

            handler.handle(context);

            assertFalse(context.isContinueProcessing());
            assertNotNull(context.getResponse());
            assertFalse(context.getResponse().isApproved());
        }

        @Test
        @DisplayName("Should set appropriate response code when limit exceeded")
        void shouldSetAppropriateResponseCode() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            LimitCheckResult failResult = LimitCheckResult.builder()
                    .passed(false)
                    .message("Daily limit exceeded")
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(failResult);

            handler.handle(context);

            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(),
                    context.getResponse().getResponseCode());
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT,
                    context.getResponse().getResponseCodeEnum());
        }

        @Test
        @DisplayName("Should include error message in response")
        void shouldIncludeErrorMessageInResponse() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            LimitCheckResult failResult = LimitCheckResult.builder()
                    .passed(false)
                    .message("Daily cumulative limit exceeded")
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(failResult);

            handler.handle(context);

            assertTrue(context.getResponse().getResponseDescription()
                    .contains("Daily cumulative limit exceeded"));
            assertEquals("Daily cumulative limit exceeded", context.getResponse().getErrorDetails());
        }

        @Test
        @DisplayName("Should include Chinese description when limit type provided")
        void shouldIncludeChineseDescriptionWhenLimitTypeProvided() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            TransactionLimit exceededLimit = TransactionLimit.builder()
                    .limitType(LimitType.SINGLE_TRANSACTION)
                    .limitValue(new BigDecimal("30000"))
                    .build();

            LimitCheckResult failResult = LimitCheckResult.builder()
                    .passed(false)
                    .message("Single transaction limit exceeded")
                    .exceededLimit(exceededLimit)
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(failResult);

            handler.handle(context);

            assertNotNull(context.getResponse().getResponseDescriptionChinese());
            assertTrue(context.getResponse().getResponseDescriptionChinese().contains("交易限額超過"));
        }
    }

    @Nested
    @DisplayName("Record Usage Tests")
    class RecordUsageTests {

        @Test
        @DisplayName("Should record usage after successful transaction")
        void shouldRecordUsageAfterSuccess() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            // Simulate approved response
            com.fep.transaction.domain.TransactionResponse response =
                    com.fep.transaction.domain.TransactionResponse.builder()
                            .transactionId(request.getTransactionId())
                            .approved(true)
                            .build();
            context.setResponse(response);

            handler.recordUsageIfSuccess(context);

            verify(limitManager).recordUsage(request);
        }

        @Test
        @DisplayName("Should not record usage after declined transaction")
        void shouldNotRecordUsageAfterDecline() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            // Simulate declined response
            com.fep.transaction.domain.TransactionResponse response =
                    com.fep.transaction.domain.TransactionResponse.builder()
                            .transactionId(request.getTransactionId())
                            .approved(false)
                            .build();
            context.setResponse(response);

            handler.recordUsageIfSuccess(context);

            verify(limitManager, never()).recordUsage(any());
        }

        @Test
        @DisplayName("Should not record usage when no response")
        void shouldNotRecordUsageWhenNoResponse() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            handler.recordUsageIfSuccess(context);

            verify(limitManager, never()).recordUsage(any());
        }
    }

    @Nested
    @DisplayName("Integration With LimitManager Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should call limitManager with correct request")
        void shouldCallLimitManagerWithCorrectRequest() {
            TransactionRequest request = createRequest();
            PipelineContext context = new PipelineContext("PIPE-TEST", request);

            LimitCheckResult passResult = LimitCheckResult.builder()
                    .passed(true)
                    .remainingAmount(new BigDecimal("100000"))
                    .build();
            when(limitManager.checkLimits(any())).thenReturn(passResult);

            handler.handle(context);

            verify(limitManager).checkLimits(request);
        }
    }

    // Helper methods

    private TransactionRequest createRequest() {
        return TransactionRequest.builder()
                .transactionId("LIMIT-TEST-001")
                .transactionType(TransactionType.WITHDRAWAL)
                .pan("4111111111111111")
                .amount(new BigDecimal("10000"))
                .currencyCode("TWD")
                .build();
    }
}
