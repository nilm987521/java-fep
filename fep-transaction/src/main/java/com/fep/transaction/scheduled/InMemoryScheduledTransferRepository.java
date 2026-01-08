package com.fep.transaction.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ScheduledTransferRepository.
 */
public class InMemoryScheduledTransferRepository implements ScheduledTransferRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryScheduledTransferRepository.class);

    private final Map<String, ScheduledTransfer> transfers = new ConcurrentHashMap<>();

    @Override
    public ScheduledTransfer save(ScheduledTransfer transfer) {
        if (transfer.getScheduleId() == null) {
            transfer.setScheduleId(generateScheduleId());
        }
        transfer.setCreatedAt(LocalDateTime.now());
        transfers.put(transfer.getScheduleId(), transfer);
        log.debug("Saved scheduled transfer: {}", transfer.getScheduleId());
        return transfer;
    }

    @Override
    public Optional<ScheduledTransfer> findById(String scheduleId) {
        return Optional.ofNullable(transfers.get(scheduleId));
    }

    @Override
    public List<ScheduledTransfer> findByCustomerId(String customerId) {
        return transfers.values().stream()
                .filter(t -> customerId.equals(t.getCustomerId()))
                .sorted(Comparator.comparing(ScheduledTransfer::getScheduledDate))
                .collect(Collectors.toList());
    }

    @Override
    public List<ScheduledTransfer> findReadyForExecution(LocalDate date) {
        return transfers.values().stream()
                .filter(t -> t.isReadyForExecution(date))
                .sorted(Comparator.comparing(ScheduledTransfer::getScheduledDate))
                .collect(Collectors.toList());
    }

    @Override
    public List<ScheduledTransfer> findActiveTransfers() {
        return transfers.values().stream()
                .filter(t -> t.getStatus() == ScheduledTransferStatus.ACTIVE ||
                             t.getStatus() == ScheduledTransferStatus.PENDING)
                .sorted(Comparator.comparing(ScheduledTransfer::getScheduledDate))
                .collect(Collectors.toList());
    }

    @Override
    public ScheduledTransfer update(ScheduledTransfer transfer) {
        if (transfers.containsKey(transfer.getScheduleId())) {
            transfer.setUpdatedAt(LocalDateTime.now());
            transfers.put(transfer.getScheduleId(), transfer);
            log.debug("Updated scheduled transfer: {}", transfer.getScheduleId());
            return transfer;
        }
        throw new IllegalArgumentException("Scheduled transfer not found: " + transfer.getScheduleId());
    }

    @Override
    public boolean cancel(String scheduleId) {
        ScheduledTransfer transfer = transfers.get(scheduleId);
        if (transfer != null) {
            transfer.setStatus(ScheduledTransferStatus.CANCELLED);
            transfer.setUpdatedAt(LocalDateTime.now());
            log.debug("Cancelled scheduled transfer: {}", scheduleId);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(String scheduleId) {
        ScheduledTransfer removed = transfers.remove(scheduleId);
        if (removed != null) {
            log.debug("Deleted scheduled transfer: {}", scheduleId);
            return true;
        }
        return false;
    }

    @Override
    public long countByCustomerId(String customerId) {
        return transfers.values().stream()
                .filter(t -> customerId.equals(t.getCustomerId()))
                .count();
    }

    /**
     * Generates a unique schedule ID.
     */
    private String generateScheduleId() {
        return "SCH" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Clears all scheduled transfers (for testing).
     */
    public void clear() {
        transfers.clear();
        log.info("Scheduled transfer repository cleared");
    }
}
