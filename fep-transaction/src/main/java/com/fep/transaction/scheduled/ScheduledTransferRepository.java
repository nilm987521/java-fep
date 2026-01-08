package com.fep.transaction.scheduled;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for scheduled transfer persistence.
 */
public interface ScheduledTransferRepository {

    /**
     * Saves a scheduled transfer.
     */
    ScheduledTransfer save(ScheduledTransfer transfer);

    /**
     * Finds a scheduled transfer by ID.
     */
    Optional<ScheduledTransfer> findById(String scheduleId);

    /**
     * Finds all scheduled transfers for a customer.
     */
    List<ScheduledTransfer> findByCustomerId(String customerId);

    /**
     * Finds all transfers ready for execution on a given date.
     */
    List<ScheduledTransfer> findReadyForExecution(LocalDate date);

    /**
     * Finds all active or pending transfers.
     */
    List<ScheduledTransfer> findActiveTransfers();

    /**
     * Updates a scheduled transfer.
     */
    ScheduledTransfer update(ScheduledTransfer transfer);

    /**
     * Cancels a scheduled transfer.
     */
    boolean cancel(String scheduleId);

    /**
     * Deletes a scheduled transfer.
     */
    boolean delete(String scheduleId);

    /**
     * Counts total scheduled transfers for a customer.
     */
    long countByCustomerId(String customerId);
}
