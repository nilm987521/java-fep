package com.fep.transaction.config;

import com.fep.transaction.batch.BatchProcessor;
import com.fep.transaction.batch.LoggingBatchListener;
import com.fep.transaction.logging.RepositoryTransactionLogger;
import com.fep.transaction.logging.TransactionLogger;
import com.fep.transaction.pipeline.*;
import com.fep.transaction.pipeline.handler.*;
import com.fep.transaction.processor.*;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.routing.TransactionRouter;
import com.fep.transaction.service.*;
import com.fep.transaction.validator.DuplicateChecker;
import com.fep.transaction.validator.ValidationChain;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for the transaction module.
 * Provides factory methods to create fully configured transaction processing components.
 */
public class TransactionModuleConfig {

    // Default configuration values
    private static final int DEFAULT_DUPLICATE_WINDOW_MINUTES = 5;
    private static final int DEFAULT_BATCH_PARALLELISM = 10;

    /**
     * Creates a fully configured transaction service with all dependencies.
     *
     * @return configured TransactionService
     */
    public static TransactionService createTransactionService() {
        // Create repository
        TransactionRepository repository = createRepository();

        // Create query service
        TransactionQueryService queryService = new TransactionQueryService(repository);

        // Create duplicate checker
        DuplicateChecker duplicateChecker = new DuplicateChecker(repository, DEFAULT_DUPLICATE_WINDOW_MINUTES);

        // Create router
        TransactionRouter router = createRouter();

        // Create processors
        List<TransactionProcessor> processors = createProcessors();

        // Create pipeline-based service
        return new PipelineTransactionService(processors, router, duplicateChecker, repository, queryService);
    }

    /**
     * Creates a fully configured pipeline transaction service with custom repository.
     *
     * @param repository the transaction repository to use
     * @return configured TransactionService
     */
    public static TransactionService createTransactionService(TransactionRepository repository) {
        TransactionQueryService queryService = new TransactionQueryService(repository);
        DuplicateChecker duplicateChecker = new DuplicateChecker(repository, DEFAULT_DUPLICATE_WINDOW_MINUTES);
        TransactionRouter router = createRouter();
        List<TransactionProcessor> processors = createProcessors();

        return new PipelineTransactionService(processors, router, duplicateChecker, repository, queryService);
    }

    /**
     * Creates a simple transaction service without pipeline (direct processor invocation).
     *
     * @return configured TransactionService
     */
    public static TransactionService createSimpleTransactionService() {
        return new TransactionServiceImpl(createProcessors());
    }

    /**
     * Creates a batch processor for bulk transaction processing.
     *
     * @param transactionService the transaction service to use
     * @return configured BatchProcessor
     */
    public static BatchProcessor createBatchProcessor(TransactionService transactionService) {
        BatchProcessor processor = new BatchProcessor(transactionService, DEFAULT_BATCH_PARALLELISM);
        processor.addListener(new LoggingBatchListener());
        return processor;
    }

    /**
     * Creates a reversal service.
     *
     * @param repository the transaction repository
     * @return configured ReversalService
     */
    public static ReversalService createReversalService(TransactionRepository repository) {
        TransactionQueryService queryService = new TransactionQueryService(repository);
        return new ReversalService(queryService, repository);
    }

    /**
     * Creates the default in-memory repository.
     *
     * @return InMemoryTransactionRepository
     */
    public static TransactionRepository createRepository() {
        return new InMemoryTransactionRepository();
    }

    /**
     * Creates the default transaction router with standard rules.
     *
     * @return configured TransactionRouter
     */
    public static TransactionRouter createRouter() {
        return new TransactionRouter();
    }

    /**
     * Creates the list of standard transaction processors.
     *
     * @return list of TransactionProcessor
     */
    public static List<TransactionProcessor> createProcessors() {
        return Arrays.asList(
                new WithdrawalProcessor(),
                new DepositProcessor(),
                new TransferProcessor(),
                new BalanceInquiryProcessor(),
                new ReversalProcessor(),
                new P2PTransferProcessor(),
                new BillPaymentProcessor(),
                new ETicketTopupProcessor(),
                new TaiwanPayProcessor(),
                new CardlessWithdrawalProcessor(),
                new CrossBorderPaymentProcessor(),
                new CurrencyExchangeProcessor(),
                new EWalletProcessor()
        );
    }

    /**
     * Creates a custom pipeline with specified handlers.
     *
     * @param handlers list of pipeline handlers
     * @return configured TransactionPipeline
     */
    public static TransactionPipeline createPipeline(List<PipelineHandler> handlers) {
        TransactionPipeline pipeline = new TransactionPipeline();
        handlers.forEach(pipeline::addHandler);
        pipeline.addListener(new LoggingPipelineListener());
        return pipeline;
    }

    /**
     * Creates the default pipeline with all standard handlers.
     *
     * @param repository the transaction repository
     * @return configured TransactionPipeline
     */
    public static TransactionPipeline createDefaultPipeline(TransactionRepository repository) {
        DuplicateChecker duplicateChecker = new DuplicateChecker(repository, DEFAULT_DUPLICATE_WINDOW_MINUTES);
        TransactionRouter router = createRouter();
        List<TransactionProcessor> processors = createProcessors();
        TransactionLogger transactionLogger = new RepositoryTransactionLogger(repository);
        ValidationChain validationChain = ValidationChain.createDefault();

        TransactionPipeline pipeline = new TransactionPipeline();
        pipeline.addHandler(new DuplicateCheckHandler(duplicateChecker));
        pipeline.addHandler(new ValidationHandler(validationChain));
        pipeline.addHandler(new RoutingHandler(router));
        pipeline.addHandler(new ProcessingHandler(processors));
        pipeline.addHandler(new AuditHandler(transactionLogger));
        pipeline.addListener(new LoggingPipelineListener());

        return pipeline;
    }

    /**
     * Creates a query service for transaction lookup.
     *
     * @param repository the transaction repository
     * @return configured TransactionQueryService
     */
    public static TransactionQueryService createQueryService(TransactionRepository repository) {
        return new TransactionQueryService(repository);
    }

    /**
     * Creates a duplicate checker for transaction validation.
     *
     * @param repository the transaction repository
     * @param windowMinutes time window in minutes
     * @return configured DuplicateChecker
     */
    public static DuplicateChecker createDuplicateChecker(TransactionRepository repository, int windowMinutes) {
        return new DuplicateChecker(repository, windowMinutes);
    }

    /**
     * Builder for creating customized module configuration.
     */
    public static class ModuleBuilder {
        private TransactionRepository repository;
        private int duplicateWindowMinutes = DEFAULT_DUPLICATE_WINDOW_MINUTES;
        private int batchParallelism = DEFAULT_BATCH_PARALLELISM;
        private List<TransactionProcessor> processors;
        private List<PipelineHandler> additionalHandlers;
        private List<PipelineListener> additionalListeners;

        public ModuleBuilder repository(TransactionRepository repository) {
            this.repository = repository;
            return this;
        }

        public ModuleBuilder duplicateWindowMinutes(int minutes) {
            this.duplicateWindowMinutes = minutes;
            return this;
        }

        public ModuleBuilder batchParallelism(int parallelism) {
            this.batchParallelism = parallelism;
            return this;
        }

        public ModuleBuilder processors(List<TransactionProcessor> processors) {
            this.processors = processors;
            return this;
        }

        public ModuleBuilder additionalHandlers(List<PipelineHandler> handlers) {
            this.additionalHandlers = handlers;
            return this;
        }

        public ModuleBuilder additionalListeners(List<PipelineListener> listeners) {
            this.additionalListeners = listeners;
            return this;
        }

        public TransactionModule build() {
            if (repository == null) {
                repository = createRepository();
            }
            if (processors == null) {
                processors = createProcessors();
            }

            TransactionQueryService queryService = new TransactionQueryService(repository);
            DuplicateChecker duplicateChecker = new DuplicateChecker(repository, duplicateWindowMinutes);
            TransactionRouter router = createRouter();

            PipelineTransactionService.Builder serviceBuilder = PipelineTransactionService.builder()
                    .processors(processors)
                    .router(router)
                    .duplicateChecker(duplicateChecker)
                    .repository(repository)
                    .queryService(queryService);

            if (additionalHandlers != null) {
                additionalHandlers.forEach(serviceBuilder::addHandler);
            }
            if (additionalListeners != null) {
                additionalListeners.forEach(serviceBuilder::addListener);
            }

            TransactionService transactionService = serviceBuilder.build();
            BatchProcessor batchProcessor = new BatchProcessor(transactionService, batchParallelism);
            batchProcessor.addListener(new LoggingBatchListener());
            ReversalService reversalService = new ReversalService(queryService, repository);

            return new TransactionModule(
                    transactionService,
                    batchProcessor,
                    reversalService,
                    queryService,
                    repository
            );
        }
    }

    /**
     * Creates a module builder for customized configuration.
     */
    public static ModuleBuilder moduleBuilder() {
        return new ModuleBuilder();
    }
}
