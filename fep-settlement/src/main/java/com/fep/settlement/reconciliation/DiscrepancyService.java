package com.fep.settlement.reconciliation;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and resolving discrepancies.
 */
public class DiscrepancyService {

    private static final Logger log = LoggerFactory.getLogger(DiscrepancyService.class);

    private final SettlementRepository repository;

    public DiscrepancyService(SettlementRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new discrepancy.
     */
    public Discrepancy createDiscrepancy(DiscrepancyType type,
                                         LocalDate settlementDate,
                                         String settlementFileId,
                                         String settlementRecordRef,
                                         String internalTransactionRef,
                                         BigDecimal settlementAmount,
                                         BigDecimal internalAmount,
                                         String description) {
        BigDecimal difference = BigDecimal.ZERO;
        if (settlementAmount != null && internalAmount != null) {
            difference = settlementAmount.subtract(internalAmount);
        } else if (settlementAmount != null) {
            difference = settlementAmount;
        } else if (internalAmount != null) {
            difference = internalAmount.negate();
        }

        Discrepancy discrepancy = Discrepancy.builder()
                .discrepancyId(generateDiscrepancyId())
                .type(type)
                .settlementDate(settlementDate)
                .settlementFileId(settlementFileId)
                .settlementRecordRef(settlementRecordRef)
                .internalTransactionRef(internalTransactionRef)
                .settlementAmount(settlementAmount)
                .internalAmount(internalAmount)
                .differenceAmount(difference)
                .status(DiscrepancyStatus.OPEN)
                .priority(type.getDefaultPriority())
                .description(description)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Get discrepancy by ID.
     */
    public Optional<Discrepancy> getDiscrepancy(String discrepancyId) {
        return repository.findDiscrepancyById(discrepancyId);
    }

    /**
     * Get all open discrepancies.
     */
    public List<Discrepancy> getOpenDiscrepancies() {
        return repository.findOpenDiscrepancies();
    }

    /**
     * Get discrepancies by date.
     */
    public List<Discrepancy> getDiscrepanciesByDate(LocalDate date) {
        return repository.findDiscrepanciesByDate(date);
    }

    /**
     * Get discrepancies by status.
     */
    public List<Discrepancy> getDiscrepanciesByStatus(DiscrepancyStatus status) {
        return repository.findDiscrepanciesByStatus(status);
    }

    /**
     * Get discrepancies by type.
     */
    public List<Discrepancy> getDiscrepanciesByType(DiscrepancyType type) {
        return repository.findDiscrepanciesByType(type);
    }

    /**
     * Get discrepancies by priority.
     */
    public List<Discrepancy> getDiscrepanciesByPriority(DiscrepancyPriority priority) {
        return repository.findDiscrepanciesByPriority(priority);
    }

    /**
     * Assign discrepancy to investigator.
     */
    public Discrepancy assign(String discrepancyId, String assignee) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setAssignedTo(assignee);
        discrepancy.setStatus(DiscrepancyStatus.INVESTIGATING);
        discrepancy.setUpdatedAt(LocalDateTime.now());
        discrepancy.addInvestigationNote("system", "Assigned to " + assignee);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Add investigation note.
     */
    public Discrepancy addNote(String discrepancyId, String userId, String note) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.addInvestigationNote(userId, note);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Set root cause.
     */
    public Discrepancy setRootCause(String discrepancyId, String rootCause) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setRootCause(rootCause);
        discrepancy.setUpdatedAt(LocalDateTime.now());

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Update priority.
     */
    public Discrepancy updatePriority(String discrepancyId, DiscrepancyPriority priority) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setPriority(priority);
        discrepancy.setUpdatedAt(LocalDateTime.now());
        discrepancy.addInvestigationNote("system", "Priority changed to " + priority);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Escalate discrepancy.
     */
    public Discrepancy escalate(String discrepancyId, String reason) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setStatus(DiscrepancyStatus.ESCALATED);
        discrepancy.setPriority(DiscrepancyPriority.CRITICAL);
        discrepancy.setUpdatedAt(LocalDateTime.now());
        discrepancy.addInvestigationNote("system", "Escalated: " + reason);

        log.warn("Discrepancy {} escalated: {}", discrepancyId, reason);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Request resolution approval.
     */
    public Discrepancy requestApproval(String discrepancyId, ResolutionAction proposedAction, String notes) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setStatus(DiscrepancyStatus.PENDING_APPROVAL);
        discrepancy.setResolutionAction(proposedAction);
        discrepancy.setResolutionNotes(notes);
        discrepancy.setUpdatedAt(LocalDateTime.now());
        discrepancy.addInvestigationNote("system",
                "Approval requested for action: " + proposedAction.getChineseName());

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Resolve discrepancy.
     */
    public Discrepancy resolve(String discrepancyId, String userId, ResolutionAction action, String notes) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.resolve(userId, action, notes);

        log.info("Discrepancy {} resolved by {} with action {}",
                discrepancyId, userId, action);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Write off discrepancy.
     */
    public Discrepancy writeOff(String discrepancyId, String userId, String reason) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setStatus(DiscrepancyStatus.WRITTEN_OFF);
        discrepancy.setResolutionAction(ResolutionAction.WRITE_OFF);
        discrepancy.setResolutionNotes(reason);
        discrepancy.setResolvedBy(userId);
        discrepancy.setResolvedAt(LocalDateTime.now());
        discrepancy.setUpdatedAt(LocalDateTime.now());
        discrepancy.addInvestigationNote(userId, "Written off: " + reason);

        log.info("Discrepancy {} written off by {}: {}", discrepancyId, userId, reason);

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Close discrepancy.
     */
    public Discrepancy close(String discrepancyId, String reason) {
        Discrepancy discrepancy = repository.findDiscrepancyById(discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId));

        discrepancy.setStatus(DiscrepancyStatus.CLOSED);
        discrepancy.setResolutionNotes(reason);
        discrepancy.setResolvedAt(LocalDateTime.now());
        discrepancy.setUpdatedAt(LocalDateTime.now());

        return repository.saveDiscrepancy(discrepancy);
    }

    /**
     * Link related discrepancies.
     */
    public void linkDiscrepancies(String discrepancyId1, String discrepancyId2) {
        Discrepancy d1 = repository.findDiscrepancyById(discrepancyId1)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId1));
        Discrepancy d2 = repository.findDiscrepancyById(discrepancyId2)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found: " + discrepancyId2));

        if (d1.getRelatedDiscrepancies() == null) {
            d1.setRelatedDiscrepancies(new ArrayList<>());
        }
        if (d2.getRelatedDiscrepancies() == null) {
            d2.setRelatedDiscrepancies(new ArrayList<>());
        }

        if (!d1.getRelatedDiscrepancies().contains(discrepancyId2)) {
            d1.getRelatedDiscrepancies().add(discrepancyId2);
        }
        if (!d2.getRelatedDiscrepancies().contains(discrepancyId1)) {
            d2.getRelatedDiscrepancies().add(discrepancyId1);
        }

        repository.saveDiscrepancy(d1);
        repository.saveDiscrepancy(d2);
    }

    /**
     * Get discrepancy statistics.
     */
    public Map<String, Object> getStatistics() {
        List<Discrepancy> all = repository.findOpenDiscrepancies();
        all.addAll(repository.findDiscrepanciesByStatus(DiscrepancyStatus.RESOLVED));
        all.addAll(repository.findDiscrepanciesByStatus(DiscrepancyStatus.WRITTEN_OFF));
        all.addAll(repository.findDiscrepanciesByStatus(DiscrepancyStatus.CLOSED));

        Map<String, Object> stats = new HashMap<>();

        // Count by status
        Map<DiscrepancyStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Discrepancy::getStatus, Collectors.counting()));
        stats.put("byStatus", byStatus);

        // Count by type
        Map<DiscrepancyType, Long> byType = all.stream()
                .collect(Collectors.groupingBy(Discrepancy::getType, Collectors.counting()));
        stats.put("byType", byType);

        // Count by priority
        Map<DiscrepancyPriority, Long> byPriority = all.stream()
                .collect(Collectors.groupingBy(Discrepancy::getPriority, Collectors.counting()));
        stats.put("byPriority", byPriority);

        // Total amounts
        BigDecimal totalAmount = all.stream()
                .map(Discrepancy::getAbsoluteDifference)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalDiscrepancyAmount", totalAmount);

        // Open count
        long openCount = all.stream().filter(Discrepancy::isOpen).count();
        stats.put("openCount", openCount);

        // Resolution rate
        long resolved = all.stream()
                .filter(d -> d.getStatus() == DiscrepancyStatus.RESOLVED ||
                            d.getStatus() == DiscrepancyStatus.WRITTEN_OFF ||
                            d.getStatus() == DiscrepancyStatus.CLOSED)
                .count();
        double resolutionRate = all.isEmpty() ? 0 : (resolved * 100.0) / all.size();
        stats.put("resolutionRate", String.format("%.2f%%", resolutionRate));

        return stats;
    }

    /**
     * Get aging report for open discrepancies.
     */
    public Map<String, List<Discrepancy>> getAgingReport() {
        List<Discrepancy> open = repository.findOpenDiscrepancies();
        LocalDateTime now = LocalDateTime.now();

        Map<String, List<Discrepancy>> aging = new LinkedHashMap<>();
        aging.put("0-24 hours", new ArrayList<>());
        aging.put("1-3 days", new ArrayList<>());
        aging.put("3-7 days", new ArrayList<>());
        aging.put("7-30 days", new ArrayList<>());
        aging.put(">30 days", new ArrayList<>());

        for (Discrepancy d : open) {
            if (d.getCreatedAt() == null) continue;

            long hours = java.time.Duration.between(d.getCreatedAt(), now).toHours();

            if (hours <= 24) {
                aging.get("0-24 hours").add(d);
            } else if (hours <= 72) {
                aging.get("1-3 days").add(d);
            } else if (hours <= 168) {
                aging.get("3-7 days").add(d);
            } else if (hours <= 720) {
                aging.get("7-30 days").add(d);
            } else {
                aging.get(">30 days").add(d);
            }
        }

        return aging;
    }

    private String generateDiscrepancyId() {
        return "DISC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
