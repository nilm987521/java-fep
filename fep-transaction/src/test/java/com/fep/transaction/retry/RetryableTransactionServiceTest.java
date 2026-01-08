package com.fep.transaction.retry;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetryableTransactionService.
 */
@ExtendWith(MockitoExtension.class)
class RetryableTransactionServiceTest {

    @Mock
    private TransactionService transactionService;

    private RetryableTransactionService retryableService;

    @BeforeEach
    void setUp() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(10))
                .exponentialBackoff(true)
                .backoffMultiplier(2.0)
                .build();
        retryableService = new RetryableTransactionService(transactionService, policy);
    }

    @AfterEach
    void tearDown() {
        retryableService.shutdown();
    }

    @Nested
    @DisplayName("Success Without Retry Tests")
    class SuccessWithoutRetryTests {

        @Test
        @DisplayName("Should return success on first attempt")
        void shouldReturnSuccessOnFirstAttempt() {
            TransactionRequest request = createRequest();
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any())).thenReturn(successResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertTrue(result.isApproved());
            verify(transactionService, times(1)).process(any());
        }
    }

    @Nested
    @DisplayName("Retry on Retryable Response Code Tests")
    class RetryOnResponseCodeTests {

        @Test
        @DisplayName("Should retry on system malfunction (96)")
        void shouldRetryOnSystemMalfunction() {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "96");
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any()))
                    .thenReturn(failResponse)
                    .thenReturn(successResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertTrue(result.isApproved());
            verify(transactionService, times(2)).process(any());
        }

        @Test
        @DisplayName("Should retry on issuer inoperative (91)")
        void shouldRetryOnIssuerInoperative() {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "91");
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any()))
                    .thenReturn(failResponse)
                    .thenReturn(failResponse)
                    .thenReturn(successResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertTrue(result.isApproved());
            verify(transactionService, times(3)).process(any());
        }

        @Test
        @DisplayName("Should not retry on insufficient funds (51)")
        void shouldNotRetryOnInsufficientFunds() {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "51");

            when(transactionService.process(any())).thenReturn(failResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertFalse(result.isApproved());
            assertEquals("51", result.getResponseCode());
            verify(transactionService, times(1)).process(any());
        }

        @Test
        @DisplayName("Should exhaust retries when always failing")
        void shouldExhaustRetriesWhenAlwaysFailing() {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "96");

            when(transactionService.process(any())).thenReturn(failResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertFalse(result.isApproved());
            // Initial + 3 retries = 4 total attempts
            verify(transactionService, times(4)).process(any());
        }
    }

    @Nested
    @DisplayName("Retry on Exception Tests")
    class RetryOnExceptionTests {

        @Test
        @DisplayName("Should retry on timeout exception")
        void shouldRetryOnTimeoutException() {
            TransactionRequest request = createRequest();
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any()))
                    .thenThrow(new RuntimeException(new java.net.SocketTimeoutException("Timeout")))
                    .thenReturn(successResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertTrue(result.isApproved());
            verify(transactionService, times(2)).process(any());
        }

        @Test
        @DisplayName("Should retry on connection exception")
        void shouldRetryOnConnectionException() {
            TransactionRequest request = createRequest();
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any()))
                    .thenThrow(new RuntimeException(new java.net.ConnectException("Connection refused")))
                    .thenReturn(successResponse);

            TransactionResponse result = retryableService.processWithRetry(request);

            assertTrue(result.isApproved());
            verify(transactionService, times(2)).process(any());
        }

        @Test
        @DisplayName("Should not retry on non-retryable exception")
        void shouldNotRetryOnNonRetryableException() {
            TransactionRequest request = createRequest();

            when(transactionService.process(any()))
                    .thenThrow(new IllegalArgumentException("Invalid request"));

            TransactionResponse result = retryableService.processWithRetry(request);

            assertFalse(result.isApproved());
            verify(transactionService, times(1)).process(any());
        }
    }

    @Nested
    @DisplayName("Async Processing Tests")
    class AsyncProcessingTests {

        @Test
        @DisplayName("Should process asynchronously")
        void shouldProcessAsync() throws Exception {
            TransactionRequest request = createRequest();
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any())).thenReturn(successResponse);

            CompletableFuture<TransactionResponse> future =
                    retryableService.processWithRetryAsync(request);

            TransactionResponse result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.isApproved());
        }

        @Test
        @DisplayName("Should retry asynchronously")
        void shouldRetryAsync() throws Exception {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "96");
            TransactionResponse successResponse = createSuccessResponse(request);

            when(transactionService.process(any()))
                    .thenReturn(failResponse)
                    .thenReturn(successResponse);

            CompletableFuture<TransactionResponse> future =
                    retryableService.processWithRetryAsync(request);

            TransactionResponse result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.isApproved());
            verify(transactionService, times(2)).process(any());
        }
    }

    @Nested
    @DisplayName("Retry Listener Tests")
    class RetryListenerTests {

        @Test
        @DisplayName("Should notify listener on events")
        void shouldNotifyListenerOnEvents() {
            TransactionRequest request = createRequest();
            TransactionResponse failResponse = createFailResponse(request, "96");
            TransactionResponse successResponse = createSuccessResponse(request);

            AtomicInteger startCount = new AtomicInteger(0);
            AtomicInteger attemptCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);

            retryableService.setListener(new RetryableTransactionService.RetryListener() {
                @Override
                public void onRetryStart(RetryContext context) {
                    startCount.incrementAndGet();
                }

                @Override
                public void onRetryAttempt(RetryContext context, Duration delay) {
                    attemptCount.incrementAndGet();
                }

                @Override
                public void onRetrySuccess(RetryContext context) {
                    successCount.incrementAndGet();
                }
            });

            when(transactionService.process(any()))
                    .thenReturn(failResponse)
                    .thenReturn(successResponse);

            retryableService.processWithRetry(request);

            assertEquals(1, startCount.get());
            assertEquals(1, attemptCount.get());
            assertEquals(1, successCount.get());
        }
    }

    @Nested
    @DisplayName("Retry Policy Tests")
    class RetryPolicyTests {

        @Test
        @DisplayName("Should calculate exponential backoff delay")
        void shouldCalculateExponentialBackoffDelay() {
            RetryPolicy policy = RetryPolicy.builder()
                    .initialDelay(Duration.ofMillis(100))
                    .backoffMultiplier(2.0)
                    .exponentialBackoff(true)
                    .jitterFactor(0)
                    .build();

            assertEquals(100, policy.getDelayForAttempt(1).toMillis());
            assertEquals(200, policy.getDelayForAttempt(2).toMillis());
            assertEquals(400, policy.getDelayForAttempt(3).toMillis());
        }

        @Test
        @DisplayName("Should respect max delay")
        void shouldRespectMaxDelay() {
            RetryPolicy policy = RetryPolicy.builder()
                    .initialDelay(Duration.ofMillis(100))
                    .maxDelay(Duration.ofMillis(300))
                    .backoffMultiplier(2.0)
                    .exponentialBackoff(true)
                    .jitterFactor(0)
                    .build();

            assertEquals(300, policy.getDelayForAttempt(10).toMillis());
        }

        @Test
        @DisplayName("Should use fixed delay when not exponential")
        void shouldUseFixedDelayWhenNotExponential() {
            RetryPolicy policy = RetryPolicy.builder()
                    .initialDelay(Duration.ofMillis(100))
                    .exponentialBackoff(false)
                    .jitterFactor(0)
                    .build();

            assertEquals(100, policy.getDelayForAttempt(1).toMillis());
            assertEquals(100, policy.getDelayForAttempt(2).toMillis());
            assertEquals(100, policy.getDelayForAttempt(3).toMillis());
        }

        @Test
        @DisplayName("Should check retryable response codes")
        void shouldCheckRetryableResponseCodes() {
            RetryPolicy policy = RetryPolicy.financialTransaction();

            assertTrue(policy.isRetryableResponseCode("91"));
            assertTrue(policy.isRetryableResponseCode("96"));
            assertFalse(policy.isRetryableResponseCode("51"));
            assertFalse(policy.isRetryableResponseCode("00"));
        }

        @Test
        @DisplayName("Should check retryable exceptions")
        void shouldCheckRetryableExceptions() {
            RetryPolicy policy = RetryPolicy.financialTransaction();

            assertTrue(policy.isRetryableException(new java.net.SocketTimeoutException("Timeout")));
            assertTrue(policy.isRetryableException(new java.io.IOException("IO Error")));
            assertFalse(policy.isRetryableException(new IllegalArgumentException("Bad arg")));
        }
    }

    @Nested
    @DisplayName("Retry Context Tests")
    class RetryContextTests {

        @Test
        @DisplayName("Should track attempt history")
        void shouldTrackAttemptHistory() {
            RetryPolicy policy = RetryPolicy.financialTransaction();
            TransactionRequest request = createRequest();
            RetryContext context = RetryContext.create(request, policy);

            context.recordFailure(createFailResponse(request, "96"), Duration.ofMillis(100));
            context.prepareNextAttempt();
            context.recordSuccess(createSuccessResponse(request), Duration.ofMillis(50));

            assertEquals(2, context.getTotalAttempts());
            assertEquals(1, context.getRetryCount());
            assertEquals(RetryContext.RetryStatus.SUCCESS, context.getStatus());
        }

        @Test
        @DisplayName("Should detect when complete")
        void shouldDetectWhenComplete() {
            RetryPolicy policy = RetryPolicy.financialTransaction();
            TransactionRequest request = createRequest();
            RetryContext context = RetryContext.create(request, policy);

            assertFalse(context.isComplete());

            context.recordSuccess(createSuccessResponse(request), Duration.ofMillis(100));

            assertTrue(context.isComplete());
        }
    }

    // Helper methods

    private TransactionRequest createRequest() {
        return TransactionRequest.builder()
                .transactionId("RETRY-TEST-001")
                .transactionType(TransactionType.WITHDRAWAL)
                .pan("4111111111111111")
                .amount(new BigDecimal("1000"))
                .currencyCode("TWD")
                .build();
    }

    private TransactionResponse createSuccessResponse(TransactionRequest request) {
        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .approved(true)
                .build();
    }

    private TransactionResponse createFailResponse(TransactionRequest request, String responseCode) {
        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(responseCode)
                .approved(false)
                .build();
    }
}
