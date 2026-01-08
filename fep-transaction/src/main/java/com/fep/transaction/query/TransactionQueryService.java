package com.fep.transaction.query;

import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for querying transaction history.
 */
public class TransactionQueryService {

    private static final Logger log = LoggerFactory.getLogger(TransactionQueryService.class);

    private final TransactionRepository repository;

    public TransactionQueryService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Finds a transaction by ID.
     */
    public Optional<TransactionRecord> findById(String transactionId) {
        log.debug("Finding transaction by ID: {}", transactionId);
        return repository.findById(transactionId);
    }

    /**
     * Finds a transaction by RRN.
     */
    public Optional<TransactionRecord> findByRrn(String rrn) {
        log.debug("Finding transaction by RRN: {}", rrn);
        return repository.findByRrn(rrn);
    }

    /**
     * Finds a transaction by RRN, STAN, and Terminal ID.
     */
    public Optional<TransactionRecord> findByRrnStanTerminal(String rrn, String stan, String terminalId) {
        log.debug("Finding transaction by RRN={}, STAN={}, Terminal={}", rrn, stan, terminalId);
        return repository.findByRrnStanTerminal(rrn, stan, terminalId);
    }

    /**
     * Searches transactions by query criteria.
     */
    public TransactionQueryResult search(TransactionQuery query) {
        log.debug("Searching transactions with query: {}", query);
        query.normalize();

        // Get all records and filter (in a real implementation, this would be database query)
        List<TransactionRecord> allRecords = repository.findAll();

        List<TransactionRecord> filtered = allRecords.stream()
                .filter(record -> matchesQuery(record, query))
                .collect(Collectors.toList());

        long totalCount = filtered.size();

        // Apply pagination
        int skip = query.getPage() * query.getPageSize();
        List<TransactionRecord> paged = filtered.stream()
                .skip(skip)
                .limit(query.getPageSize())
                .collect(Collectors.toList());

        return TransactionQueryResult.of(paged, totalCount, query.getPage(), query.getPageSize());
    }

    /**
     * Gets transaction statistics for a time period.
     */
    public TransactionStatistics getStatistics(TransactionQuery query) {
        log.debug("Getting statistics for query: {}", query);

        List<TransactionRecord> records = repository.findAll().stream()
                .filter(record -> matchesQuery(record, query))
                .collect(Collectors.toList());

        return TransactionStatistics.calculate(records);
    }

    /**
     * Checks if a transaction can be reversed.
     */
    public ReversalEligibility checkReversalEligibility(String transactionId) {
        log.debug("Checking reversal eligibility for: {}", transactionId);

        Optional<TransactionRecord> recordOpt = repository.findById(transactionId);

        if (recordOpt.isEmpty()) {
            return ReversalEligibility.notFound(transactionId);
        }

        TransactionRecord record = recordOpt.get();

        // Check if already reversed
        if (record.getStatus() == TransactionStatus.REVERSED) {
            return ReversalEligibility.alreadyReversed(transactionId);
        }

        // Check if in reversible status
        if (record.getStatus() != TransactionStatus.APPROVED &&
            record.getStatus() != TransactionStatus.PENDING) {
            return ReversalEligibility.notReversible(transactionId, record.getStatus());
        }

        // Check reversal time window (typically within same business day)
        // In a real implementation, this would check against business rules

        return ReversalEligibility.eligible(record);
    }

    /**
     * Checks if a record matches the query criteria.
     */
    private boolean matchesQuery(TransactionRecord record, TransactionQuery query) {
        if (query.getTransactionId() != null && !query.getTransactionId().equals(record.getTransactionId())) {
            return false;
        }

        if (query.getRrn() != null && !query.getRrn().equals(record.getRrn())) {
            return false;
        }

        if (query.getStan() != null && !query.getStan().equals(record.getStan())) {
            return false;
        }

        if (query.getTerminalId() != null && !query.getTerminalId().equals(record.getTerminalId())) {
            return false;
        }

        if (query.getPan() != null && !matchesPan(record.getPan(), query.getPan())) {
            return false;
        }

        if (query.getTransactionType() != null && query.getTransactionType() != record.getTransactionType()) {
            return false;
        }

        if (query.getStatus() != null && query.getStatus() != record.getStatus()) {
            return false;
        }

        if (query.getAcquiringBankCode() != null &&
            !query.getAcquiringBankCode().equals(record.getAcquiringBankCode())) {
            return false;
        }

        if (query.getChannel() != null && !query.getChannel().equals(record.getChannel())) {
            return false;
        }

        if (query.getMinAmount() != null && record.getAmount() != null &&
            record.getAmount().compareTo(query.getMinAmount()) < 0) {
            return false;
        }

        if (query.getMaxAmount() != null && record.getAmount() != null &&
            record.getAmount().compareTo(query.getMaxAmount()) > 0) {
            return false;
        }

        if (query.getStartTime() != null && record.getTransactionTime() != null &&
            record.getTransactionTime().isBefore(query.getStartTime())) {
            return false;
        }

        if (query.getEndTime() != null && record.getTransactionTime() != null &&
            record.getTransactionTime().isAfter(query.getEndTime())) {
            return false;
        }

        return true;
    }

    /**
     * Checks if PAN matches (supports masked comparison).
     */
    private boolean matchesPan(String recordPan, String queryPan) {
        if (recordPan == null || queryPan == null) {
            return false;
        }

        // If query PAN contains asterisks, do partial match
        if (queryPan.contains("*")) {
            String prefix = queryPan.substring(0, 6);
            String suffix = queryPan.substring(queryPan.length() - 4);
            return recordPan.startsWith(prefix) && recordPan.endsWith(suffix);
        }

        return recordPan.equals(queryPan);
    }
}
