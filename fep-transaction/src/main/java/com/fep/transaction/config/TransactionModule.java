package com.fep.transaction.config;

import com.fep.transaction.batch.BatchProcessor;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.service.ReversalService;
import com.fep.transaction.service.TransactionService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Container for all transaction module components.
 * Provides access to all services and repositories.
 */
@Getter
@RequiredArgsConstructor
public class TransactionModule {

    /** Main transaction service for processing transactions */
    private final TransactionService transactionService;

    /** Batch processor for bulk transactions */
    private final BatchProcessor batchProcessor;

    /** Reversal service for transaction reversals */
    private final ReversalService reversalService;

    /** Query service for transaction lookups */
    private final TransactionQueryService queryService;

    /** Transaction repository for persistence */
    private final TransactionRepository repository;

    /**
     * Shuts down all components that require cleanup.
     */
    public void shutdown() {
        if (batchProcessor != null) {
            batchProcessor.shutdown();
        }
    }
}
