package com.fep.integration.adapter;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;

/**
 * Interface for core system adapters.
 */
public interface CoreSystemAdapter {

    /**
     * Processes a transaction request.
     *
     * @param request the transaction request
     * @return the transaction response
     */
    TransactionResponse process(TransactionRequest request);

    /**
     * Tests connectivity to the core system.
     *
     * @return true if connection is healthy
     */
    boolean healthCheck();

    /**
     * Gets the adapter name/type.
     *
     * @return adapter name
     */
    String getAdapterName();
}
