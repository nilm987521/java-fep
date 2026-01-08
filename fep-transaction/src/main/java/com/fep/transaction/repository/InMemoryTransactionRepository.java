package com.fep.transaction.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory implementation of TransactionRepository.
 * Suitable for testing and development.
 */
public class InMemoryTransactionRepository implements TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransactionRepository.class);

    private final Map<String, TransactionRecord> byTransactionId = new ConcurrentHashMap<>();
    private final Map<Long, TransactionRecord> byId = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public TransactionRecord save(TransactionRecord record) {
        if (record.getId() == null) {
            record.setId(idGenerator.getAndIncrement());
        }
        record.setUpdatedAt(LocalDateTime.now());

        byId.put(record.getId(), record);
        if (record.getTransactionId() != null) {
            byTransactionId.put(record.getTransactionId(), record);
        }

        log.debug("Saved transaction record: {}", record.getTransactionId());
        return record;
    }

    @Override
    public Optional<TransactionRecord> findByTransactionId(String transactionId) {
        return Optional.ofNullable(byTransactionId.get(transactionId));
    }

    @Override
    public Optional<TransactionRecord> findByRrnAndStan(String rrn, String stan) {
        return byTransactionId.values().stream()
                .filter(r -> rrn.equals(r.getRrn()) && stan.equals(r.getStan()))
                .findFirst();
    }

    @Override
    public List<TransactionRecord> findByMaskedPanAndDateRange(String maskedPan,
                                                               LocalDateTime startDate,
                                                               LocalDateTime endDate) {
        return byTransactionId.values().stream()
                .filter(r -> maskedPan.equals(r.getMaskedPan()))
                .filter(r -> isWithinDateRange(r.getRequestTime(), startDate, endDate))
                .sorted(Comparator.comparing(TransactionRecord::getRequestTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionRecord> findByTerminalIdAndDateRange(String terminalId,
                                                                LocalDateTime startDate,
                                                                LocalDateTime endDate) {
        return byTransactionId.values().stream()
                .filter(r -> terminalId.equals(r.getTerminalId()))
                .filter(r -> isWithinDateRange(r.getRequestTime(), startDate, endDate))
                .sorted(Comparator.comparing(TransactionRecord::getRequestTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionRecord> findByStatus(TransactionStatus status) {
        return byTransactionId.values().stream()
                .filter(r -> status.equals(r.getStatus()))
                .sorted(Comparator.comparing(TransactionRecord::getRequestTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateStatus(String transactionId, TransactionStatus newStatus) {
        TransactionRecord record = byTransactionId.get(transactionId);
        if (record == null) {
            return false;
        }
        boolean updated = record.updateStatus(newStatus);
        if (updated) {
            log.debug("Updated transaction {} status to {}", transactionId, newStatus);
        }
        return updated;
    }

    @Override
    public boolean updateResponse(String transactionId, String responseCode,
                                  String authorizationCode, TransactionStatus status) {
        TransactionRecord record = byTransactionId.get(transactionId);
        if (record == null) {
            return false;
        }

        record.setResponseCode(responseCode);
        record.setAuthorizationCode(authorizationCode);
        record.setResponseTime(LocalDateTime.now());
        record.updateStatus(status);

        log.debug("Updated transaction {} response: {} {}", transactionId, responseCode, status);
        return true;
    }

    @Override
    public boolean existsByTransactionId(String transactionId) {
        return byTransactionId.containsKey(transactionId);
    }

    @Override
    public boolean isDuplicate(String rrn, String stan, String terminalId, int windowMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minus(windowMinutes, ChronoUnit.MINUTES);

        return byTransactionId.values().stream()
                .filter(r -> rrn.equals(r.getRrn()))
                .filter(r -> stan.equals(r.getStan()))
                .filter(r -> terminalId.equals(r.getTerminalId()))
                .filter(r -> r.getRequestTime() != null && r.getRequestTime().isAfter(cutoff))
                .anyMatch(r -> r.getStatus() != null && r.getStatus().isSuccessful());
    }

    @Override
    public long countByStatusAndDate(TransactionStatus status, String transactionDate) {
        return byTransactionId.values().stream()
                .filter(r -> status.equals(r.getStatus()))
                .filter(r -> transactionDate.equals(r.getTransactionDate()))
                .count();
    }

    @Override
    public Optional<TransactionRecord> findByRrn(String rrn) {
        return byTransactionId.values().stream()
                .filter(r -> rrn.equals(r.getRrn()))
                .findFirst();
    }

    @Override
    public Optional<TransactionRecord> findByRrnStanTerminal(String rrn, String stan, String terminalId) {
        return byTransactionId.values().stream()
                .filter(r -> rrn.equals(r.getRrn()))
                .filter(r -> stan.equals(r.getStan()))
                .filter(r -> terminalId.equals(r.getTerminalId()))
                .findFirst();
    }

    @Override
    public List<TransactionRecord> findAll() {
        return new ArrayList<>(byTransactionId.values());
    }

    @Override
    public Optional<TransactionRecord> findOriginalForReversal(String originalTransactionId) {
        return findByTransactionId(originalTransactionId)
                .filter(r -> r.getStatus() == TransactionStatus.APPROVED ||
                             r.getStatus() == TransactionStatus.COMPLETED ||
                             r.getStatus() == TransactionStatus.PENDING);
    }

    @Override
    public boolean markAsReversed(String transactionId, String reversalTransactionId) {
        TransactionRecord record = byTransactionId.get(transactionId);
        if (record == null) {
            return false;
        }

        boolean updated = record.updateStatus(TransactionStatus.REVERSED);
        if (updated) {
            record.setOriginalTransactionId(reversalTransactionId);
            log.debug("Marked transaction {} as reversed by {}", transactionId, reversalTransactionId);
        }
        return updated;
    }

    /**
     * Clears all records (for testing).
     */
    public void clear() {
        byTransactionId.clear();
        byId.clear();
        idGenerator.set(1);
        log.info("Repository cleared");
    }

    /**
     * Gets total record count.
     */
    public int size() {
        return byTransactionId.size();
    }

    private boolean isWithinDateRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null) {
            return false;
        }
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
