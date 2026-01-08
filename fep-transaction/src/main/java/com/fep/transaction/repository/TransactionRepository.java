package com.fep.transaction.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for transaction records.
 */
public interface TransactionRepository {

    /**
     * Saves a transaction record.
     *
     * @param record the record to save
     * @return the saved record with generated ID
     */
    TransactionRecord save(TransactionRecord record);

    /**
     * Finds a transaction by transaction ID.
     *
     * @param transactionId the transaction ID
     * @return the transaction record, if found
     */
    Optional<TransactionRecord> findByTransactionId(String transactionId);

    /**
     * Finds a transaction by RRN and STAN.
     *
     * @param rrn the retrieval reference number
     * @param stan the system trace audit number
     * @return the transaction record, if found
     */
    Optional<TransactionRecord> findByRrnAndStan(String rrn, String stan);

    /**
     * Finds transactions by masked PAN within a date range.
     *
     * @param maskedPan the masked PAN
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of matching transactions
     */
    List<TransactionRecord> findByMaskedPanAndDateRange(String maskedPan,
                                                        LocalDateTime startDate,
                                                        LocalDateTime endDate);

    /**
     * Finds transactions by terminal ID within a date range.
     *
     * @param terminalId the terminal ID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of matching transactions
     */
    List<TransactionRecord> findByTerminalIdAndDateRange(String terminalId,
                                                         LocalDateTime startDate,
                                                         LocalDateTime endDate);

    /**
     * Finds transactions by status.
     *
     * @param status the transaction status
     * @return list of matching transactions
     */
    List<TransactionRecord> findByStatus(TransactionStatus status);

    /**
     * Updates a transaction's status.
     *
     * @param transactionId the transaction ID
     * @param newStatus the new status
     * @return true if updated successfully
     */
    boolean updateStatus(String transactionId, TransactionStatus newStatus);

    /**
     * Updates a transaction's response.
     *
     * @param transactionId the transaction ID
     * @param responseCode the response code
     * @param authorizationCode the authorization code (may be null)
     * @param status the new status
     * @return true if updated successfully
     */
    boolean updateResponse(String transactionId, String responseCode,
                           String authorizationCode, TransactionStatus status);

    /**
     * Checks if a transaction ID already exists.
     *
     * @param transactionId the transaction ID to check
     * @return true if exists
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Checks for duplicate transaction (same RRN, STAN, terminal within time window).
     *
     * @param rrn the RRN
     * @param stan the STAN
     * @param terminalId the terminal ID
     * @param windowMinutes the time window in minutes
     * @return true if duplicate exists
     */
    boolean isDuplicate(String rrn, String stan, String terminalId, int windowMinutes);

    /**
     * Counts transactions by status for a date.
     *
     * @param status the status to count
     * @param transactionDate the date (YYYY-MM-DD)
     * @return the count
     */
    long countByStatusAndDate(TransactionStatus status, String transactionDate);

    /**
     * Finds a transaction by ID (alias for findByTransactionId).
     *
     * @param transactionId the transaction ID
     * @return the transaction record, if found
     */
    default Optional<TransactionRecord> findById(String transactionId) {
        return findByTransactionId(transactionId);
    }

    /**
     * Finds a transaction by RRN.
     *
     * @param rrn the retrieval reference number
     * @return the transaction record, if found
     */
    Optional<TransactionRecord> findByRrn(String rrn);

    /**
     * Finds a transaction by RRN, STAN, and Terminal ID.
     *
     * @param rrn the retrieval reference number
     * @param stan the system trace audit number
     * @param terminalId the terminal ID
     * @return the transaction record, if found
     */
    Optional<TransactionRecord> findByRrnStanTerminal(String rrn, String stan, String terminalId);

    /**
     * Finds all transactions.
     *
     * @return list of all transactions
     */
    List<TransactionRecord> findAll();

    /**
     * Finds the original transaction for a reversal and marks it as reversed.
     *
     * @param originalTransactionId the original transaction ID
     * @return the original transaction record, if found
     */
    Optional<TransactionRecord> findOriginalForReversal(String originalTransactionId);

    /**
     * Marks a transaction as reversed.
     *
     * @param transactionId the transaction ID to mark
     * @param reversalTransactionId the reversal transaction ID
     * @return true if marked successfully
     */
    boolean markAsReversed(String transactionId, String reversalTransactionId);
}
