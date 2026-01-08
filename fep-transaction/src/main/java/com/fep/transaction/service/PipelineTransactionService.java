package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.logging.RepositoryTransactionLogger;
import com.fep.transaction.logging.TransactionLogger;
import com.fep.transaction.pipeline.*;
import com.fep.transaction.pipeline.handler.*;
import com.fep.transaction.processor.TransactionProcessor;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.routing.TransactionRouter;
import com.fep.transaction.validator.DuplicateChecker;
import com.fep.transaction.validator.ValidationChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TransactionService implementation that uses the pipeline architecture.
 * Integrates validation, routing, processing, and audit stages.
 */
public class PipelineTransactionService implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineTransactionService.class);

    private final TransactionPipeline pipeline;
    private final Set<TransactionType> supportedTypes;

    /**
     * Creates a PipelineTransactionService with default configuration.
     */
    public PipelineTransactionService(
            List<TransactionProcessor> processors,
            TransactionRouter router,
            DuplicateChecker duplicateChecker,
            TransactionRepository repository,
            TransactionQueryService queryService) {

        this.supportedTypes = processors.stream()
                .map(TransactionProcessor::getSupportedType)
                .collect(Collectors.toSet());

        this.pipeline = buildPipeline(processors, router, duplicateChecker, repository);

        log.info("PipelineTransactionService initialized with {} supported types: {}",
                supportedTypes.size(), supportedTypes);
    }

    /**
     * Creates a PipelineTransactionService with a custom pipeline.
     */
    public PipelineTransactionService(TransactionPipeline pipeline, Set<TransactionType> supportedTypes) {
        this.pipeline = pipeline;
        this.supportedTypes = supportedTypes;

        log.info("PipelineTransactionService initialized with custom pipeline");
    }

    @Override
    public TransactionResponse process(TransactionRequest request) {
        log.info("[{}] Processing via pipeline: type={}, amount={}",
                request.getTransactionId(),
                request.getTransactionType(),
                request.getAmount());

        return pipeline.execute(request);
    }

    @Override
    public boolean isSupported(TransactionType transactionType) {
        return supportedTypes.contains(transactionType);
    }

    @Override
    public TransactionType[] getSupportedTypes() {
        return supportedTypes.toArray(new TransactionType[0]);
    }

    /**
     * Builds the default pipeline with all standard handlers.
     */
    private TransactionPipeline buildPipeline(
            List<TransactionProcessor> processors,
            TransactionRouter router,
            DuplicateChecker duplicateChecker,
            TransactionRepository repository) {

        TransactionPipeline pipeline = new TransactionPipeline();

        // Create logger
        TransactionLogger transactionLogger = new RepositoryTransactionLogger(repository);

        // Create validation chain
        ValidationChain validationChain = ValidationChain.createDefault();

        // Add standard handlers
        pipeline.addHandler(new DuplicateCheckHandler(duplicateChecker));
        pipeline.addHandler(new ValidationHandler(validationChain));
        pipeline.addHandler(new RoutingHandler(router));
        pipeline.addHandler(new ProcessingHandler(processors));
        pipeline.addHandler(new AuditHandler(transactionLogger));

        // Add logging listener
        pipeline.addListener(new LoggingPipelineListener());

        log.debug("Built pipeline with {} handlers", pipeline.getHandlerCount());

        return pipeline;
    }

    /**
     * Gets the underlying pipeline for advanced customization.
     */
    public TransactionPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Builder for creating PipelineTransactionService with custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PipelineTransactionService.
     */
    public static class Builder {
        private List<TransactionProcessor> processors = new ArrayList<>();
        private TransactionRouter router;
        private DuplicateChecker duplicateChecker;
        private TransactionRepository repository;
        private TransactionQueryService queryService;
        private List<PipelineHandler> additionalHandlers = new ArrayList<>();
        private List<PipelineListener> listeners = new ArrayList<>();

        public Builder processors(List<TransactionProcessor> processors) {
            this.processors = processors;
            return this;
        }

        public Builder addProcessor(TransactionProcessor processor) {
            this.processors.add(processor);
            return this;
        }

        public Builder router(TransactionRouter router) {
            this.router = router;
            return this;
        }

        public Builder duplicateChecker(DuplicateChecker duplicateChecker) {
            this.duplicateChecker = duplicateChecker;
            return this;
        }

        public Builder repository(TransactionRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder queryService(TransactionQueryService queryService) {
            this.queryService = queryService;
            return this;
        }

        public Builder addHandler(PipelineHandler handler) {
            this.additionalHandlers.add(handler);
            return this;
        }

        public Builder addListener(PipelineListener listener) {
            this.listeners.add(listener);
            return this;
        }

        public PipelineTransactionService build() {
            PipelineTransactionService service = new PipelineTransactionService(
                    processors, router, duplicateChecker, repository, queryService);

            // Add any additional handlers
            for (PipelineHandler handler : additionalHandlers) {
                service.pipeline.addHandler(handler);
            }

            // Add any additional listeners
            for (PipelineListener listener : listeners) {
                service.pipeline.addListener(listener);
            }

            return service;
        }
    }
}
