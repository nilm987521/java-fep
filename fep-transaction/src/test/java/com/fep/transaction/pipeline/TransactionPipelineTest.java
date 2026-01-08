package com.fep.transaction.pipeline;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.logging.DefaultTransactionLogger;
import com.fep.transaction.pipeline.handler.*;
import com.fep.transaction.processor.BalanceInquiryProcessor;
import com.fep.transaction.processor.TransactionProcessor;
import com.fep.transaction.processor.WithdrawalProcessor;
import com.fep.transaction.routing.TransactionRouter;
import com.fep.transaction.validator.AmountValidator;
import com.fep.transaction.validator.DuplicateChecker;
import com.fep.transaction.validator.TransactionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionPipeline.
 */
class TransactionPipelineTest {

    private TransactionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new TransactionPipeline();
    }

    @Test
    void testEmptyPipelineReturnsErrorResponse() {
        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);

        TransactionResponse response = pipeline.execute(request);

        assertNotNull(response);
        // No processing handler means no response set, so error response is returned
        assertFalse(response.isApproved());
    }

    @Test
    void testPipelineWithProcessingHandler() {
        List<TransactionProcessor> processors = List.of(new BalanceInquiryProcessor());
        pipeline.addHandler(new ProcessingHandler(processors));

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);

        TransactionResponse response = pipeline.execute(request);

        assertNotNull(response);
        assertTrue(response.isApproved());
    }

    @Test
    void testFullPipeline() {
        // Set up full pipeline
        DuplicateChecker duplicateChecker = new DuplicateChecker();
        TransactionRouter router = new TransactionRouter();
        List<TransactionValidator> validators = List.of(new AmountValidator());
        List<TransactionProcessor> processors = List.of(
                new WithdrawalProcessor(),
                new BalanceInquiryProcessor()
        );

        pipeline.addHandler(new DuplicateCheckHandler(duplicateChecker))
                .addHandler(new ValidationHandler(validators))
                .addHandler(new RoutingHandler(router))
                .addHandler(new ProcessingHandler(processors))
                .addHandler(new AuditHandler(new DefaultTransactionLogger()));

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);

        TransactionResponse response = pipeline.execute(request);

        assertNotNull(response);
        assertTrue(response.isApproved());
    }

    @Test
    void testPipelineStopsOnDuplicateCheck() {
        DuplicateChecker duplicateChecker = new DuplicateChecker();
        pipeline.addHandler(new DuplicateCheckHandler(duplicateChecker));

        TransactionRequest request1 = createRequest(TransactionType.BALANCE_INQUIRY);
        request1.setRrn("123456789012");
        request1.setStan("123456");
        request1.setTerminalId("ATM001");

        TransactionRequest request2 = createRequest(TransactionType.BALANCE_INQUIRY);
        request2.setRrn("123456789012");
        request2.setStan("123456");
        request2.setTerminalId("ATM001");

        // First request should fail (no processor), but duplicate is recorded
        pipeline.execute(request1);

        // Second request should fail on duplicate check
        TransactionResponse response2 = pipeline.execute(request2);

        assertNotNull(response2);
        assertFalse(response2.isApproved());
    }

    @Test
    void testPipelineListener() {
        AtomicInteger startCount = new AtomicInteger(0);
        AtomicInteger completeCount = new AtomicInteger(0);

        pipeline.addListener(new PipelineListener() {
            @Override
            public void onPipelineStart(PipelineContext context) {
                startCount.incrementAndGet();
            }

            @Override
            public void onPipelineComplete(PipelineContext context) {
                completeCount.incrementAndGet();
            }
        });

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);
        pipeline.execute(request);

        assertEquals(1, startCount.get());
        assertEquals(1, completeCount.get());
    }

    @Test
    void testPipelineErrorListener() {
        AtomicInteger errorCount = new AtomicInteger(0);

        pipeline.addHandler(new PipelineHandler() {
            @Override
            public PipelineStage getStage() {
                return PipelineStage.VALIDATION;
            }

            @Override
            public void handle(PipelineContext context) {
                throw TransactionException.invalidAmount();
            }
        });

        pipeline.addListener(new PipelineListener() {
            @Override
            public void onPipelineError(PipelineContext context, Exception e) {
                errorCount.incrementAndGet();
            }
        });

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);
        TransactionResponse response = pipeline.execute(request);

        assertEquals(1, errorCount.get());
        assertFalse(response.isApproved());
    }

    @Test
    void testGetHandlerCount() {
        assertEquals(0, pipeline.getHandlerCount());

        pipeline.addHandler(new DuplicateCheckHandler(new DuplicateChecker()));
        assertEquals(1, pipeline.getHandlerCount());

        pipeline.addHandler(new RoutingHandler(new TransactionRouter()));
        assertEquals(2, pipeline.getHandlerCount());
    }

    @Test
    void testGetHandlersForStage() {
        pipeline.addHandler(new DuplicateCheckHandler(new DuplicateChecker()));

        var handlers = pipeline.getHandlersForStage(PipelineStage.DUPLICATE_CHECK);
        assertEquals(1, handlers.size());

        var noHandlers = pipeline.getHandlersForStage(PipelineStage.SECURITY_CHECK);
        assertTrue(noHandlers.isEmpty());
    }

    @Test
    void testMultipleHandlersPerStage() {
        AtomicInteger order = new AtomicInteger(0);

        PipelineHandler handler1 = new PipelineHandler() {
            @Override
            public PipelineStage getStage() {
                return PipelineStage.VALIDATION;
            }

            @Override
            public void handle(PipelineContext context) {
                assertEquals(0, order.getAndIncrement());
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };

        PipelineHandler handler2 = new PipelineHandler() {
            @Override
            public PipelineStage getStage() {
                return PipelineStage.VALIDATION;
            }

            @Override
            public void handle(PipelineContext context) {
                assertEquals(1, order.getAndIncrement());
            }

            @Override
            public int getOrder() {
                return 2;
            }
        };

        pipeline.addHandler(handler2); // Add in reverse order
        pipeline.addHandler(handler1);

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);
        pipeline.execute(request);

        assertEquals(2, order.get()); // Both handlers should have been called
    }

    @Test
    void testLoggingPipelineListener() {
        pipeline.addListener(new LoggingPipelineListener());

        List<TransactionProcessor> processors = List.of(new BalanceInquiryProcessor());
        pipeline.addHandler(new ProcessingHandler(processors));

        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);

        // Should not throw
        assertDoesNotThrow(() -> pipeline.execute(request));
    }

    private TransactionRequest createRequest(TransactionType type) {
        return TransactionRequest.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .transactionType(type)
                .pan("4111111111111111")
                .amount(new BigDecimal("1000"))
                .sourceAccount("12345678901234")
                .terminalId("ATM001")
                .pinBlock("1234567890ABCDEF")
                .build();
    }
}
