package com.fep.transaction.scheduled;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing scheduled transfers (預約轉帳).
 */
public class ScheduledTransferService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferService.class);

    /** Maximum scheduled transfers per customer */
    private static final int MAX_SCHEDULED_TRANSFERS_PER_CUSTOMER = 50;

    /** Maximum scheduling days in advance */
    private static final int MAX_SCHEDULE_DAYS_AHEAD = 365;

    /** Maximum single transfer amount */
    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("2000000");

    private final ScheduledTransferRepository repository;
    private final TransactionService transactionService;

    public ScheduledTransferService(ScheduledTransferRepository repository,
                                    TransactionService transactionService) {
        this.repository = repository;
        this.transactionService = transactionService;
    }

    /**
     * Creates a new scheduled transfer.
     *
     * @param request the scheduled transfer request
     * @return the created scheduled transfer
     */
    public ScheduledTransfer createScheduledTransfer(ScheduledTransfer request) {
        log.info("Creating scheduled transfer for customer: {}", request.getCustomerId());

        // Validate the request
        validateScheduledTransfer(request);

        // Check customer limits
        checkCustomerLimits(request.getCustomerId());

        // Set initial status
        request.setStatus(ScheduledTransferStatus.ACTIVE);
        request.setCreatedAt(LocalDateTime.now());

        // Save and return
        ScheduledTransfer saved = repository.save(request);
        log.info("Created scheduled transfer: {} for date: {}",
                saved.getScheduleId(), saved.getScheduledDate());

        return saved;
    }

    /**
     * Validates a scheduled transfer request.
     */
    private void validateScheduledTransfer(ScheduledTransfer request) {
        // Validate source account
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required");
        }

        // Validate destination account
        if (request.getDestinationAccount() == null || request.getDestinationAccount().isBlank()) {
            throw TransactionException.invalidRequest("Destination account is required");
        }

        // Validate destination bank code
        if (request.getDestinationBankCode() == null || request.getDestinationBankCode().isBlank()) {
            throw TransactionException.invalidRequest("Destination bank code is required");
        }

        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw TransactionException.invalidAmount();
        }

        if (request.getAmount().compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("transfer");
        }

        // Validate scheduled date
        if (request.getScheduledDate() == null) {
            throw TransactionException.invalidRequest("Scheduled date is required");
        }

        LocalDate today = LocalDate.now();
        if (request.getScheduledDate().isBefore(today)) {
            throw TransactionException.invalidRequest("Scheduled date cannot be in the past");
        }

        if (request.getScheduledDate().isAfter(today.plusDays(MAX_SCHEDULE_DAYS_AHEAD))) {
            throw TransactionException.invalidRequest(
                    "Scheduled date cannot be more than " + MAX_SCHEDULE_DAYS_AHEAD + " days ahead");
        }

        // Validate end date for recurring transfers
        if (request.getRecurrenceType() != RecurrenceType.ONE_TIME) {
            if (request.getEndDate() != null && request.getEndDate().isBefore(request.getScheduledDate())) {
                throw TransactionException.invalidRequest("End date cannot be before scheduled date");
            }
        }

        // Validate customer ID
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            throw TransactionException.invalidRequest("Customer ID is required");
        }
    }

    /**
     * Checks customer limits for scheduled transfers.
     */
    private void checkCustomerLimits(String customerId) {
        long count = repository.countByCustomerId(customerId);
        if (count >= MAX_SCHEDULED_TRANSFERS_PER_CUSTOMER) {
            throw TransactionException.invalidRequest(
                    "Maximum scheduled transfers limit reached (" + MAX_SCHEDULED_TRANSFERS_PER_CUSTOMER + ")");
        }
    }

    /**
     * Gets a scheduled transfer by ID.
     */
    public Optional<ScheduledTransfer> getScheduledTransfer(String scheduleId) {
        return repository.findById(scheduleId);
    }

    /**
     * Gets all scheduled transfers for a customer.
     */
    public List<ScheduledTransfer> getCustomerScheduledTransfers(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    /**
     * Cancels a scheduled transfer.
     */
    public boolean cancelScheduledTransfer(String scheduleId, String customerId) {
        Optional<ScheduledTransfer> transferOpt = repository.findById(scheduleId);
        if (transferOpt.isEmpty()) {
            return false;
        }

        ScheduledTransfer transfer = transferOpt.get();

        // Verify ownership
        if (!transfer.getCustomerId().equals(customerId)) {
            throw TransactionException.invalidRequest("Not authorized to cancel this transfer");
        }

        // Can only cancel active or pending transfers
        if (transfer.getStatus() != ScheduledTransferStatus.ACTIVE &&
            transfer.getStatus() != ScheduledTransferStatus.PENDING) {
            throw TransactionException.invalidRequest("Cannot cancel transfer in status: " + transfer.getStatus());
        }

        boolean cancelled = repository.cancel(scheduleId);
        if (cancelled) {
            log.info("Cancelled scheduled transfer: {}", scheduleId);
        }
        return cancelled;
    }

    /**
     * Executes all scheduled transfers ready for the given date.
     *
     * @param date the execution date
     * @return list of execution results
     */
    public List<ScheduledTransferResult> executeScheduledTransfers(LocalDate date) {
        log.info("Executing scheduled transfers for date: {}", date);

        List<ScheduledTransfer> readyTransfers = repository.findReadyForExecution(date);
        log.info("Found {} transfers ready for execution", readyTransfers.size());

        List<ScheduledTransferResult> results = new ArrayList<>();

        for (ScheduledTransfer transfer : readyTransfers) {
            ScheduledTransferResult result = executeTransfer(transfer);
            results.add(result);

            // Update the transfer status
            repository.update(transfer);
        }

        log.info("Executed {} scheduled transfers", results.size());
        return results;
    }

    /**
     * Executes a single scheduled transfer.
     */
    private ScheduledTransferResult executeTransfer(ScheduledTransfer transfer) {
        log.info("[{}] Executing scheduled transfer: {} -> {} (Bank: {}), Amount: {}",
                transfer.getScheduleId(),
                transfer.getSourceAccount(),
                transfer.getDestinationAccount(),
                transfer.getDestinationBankCode(),
                transfer.getAmount());

        try {
            // Create transaction request
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId(generateTransactionId(transfer.getScheduleId()))
                    .transactionType(TransactionType.TRANSFER)
                    .processingCode("400000")
                    .sourceAccount(transfer.getSourceAccount())
                    .destinationAccount(transfer.getDestinationAccount())
                    .destinationBankCode(transfer.getDestinationBankCode())
                    .amount(transfer.getAmount())
                    .currencyCode(transfer.getCurrencyCode())
                    .channel("SCHEDULED")
                    .additionalData("ScheduleId:" + transfer.getScheduleId())
                    .build();

            // Execute the transfer
            TransactionResponse response = transactionService.process(request);

            if (response.isApproved()) {
                transfer.markExecuted(response.getAuthorizationCode());
                log.info("[{}] Scheduled transfer executed successfully: auth={}",
                        transfer.getScheduleId(), response.getAuthorizationCode());

                return ScheduledTransferResult.builder()
                        .scheduleId(transfer.getScheduleId())
                        .transactionId(request.getTransactionId())
                        .success(true)
                        .responseCode(response.getResponseCode())
                        .authorizationCode(response.getAuthorizationCode())
                        .executedAt(LocalDateTime.now())
                        .build();
            } else {
                transfer.markFailed(response.getResponseCode() + ": " + response.getResponseDescription());
                log.warn("[{}] Scheduled transfer declined: {}",
                        transfer.getScheduleId(), response.getResponseCode());

                return ScheduledTransferResult.builder()
                        .scheduleId(transfer.getScheduleId())
                        .transactionId(request.getTransactionId())
                        .success(false)
                        .responseCode(response.getResponseCode())
                        .errorMessage(response.getResponseDescription())
                        .executedAt(LocalDateTime.now())
                        .build();
            }

        } catch (Exception e) {
            transfer.markFailed(e.getMessage());
            log.error("[{}] Error executing scheduled transfer: {}",
                    transfer.getScheduleId(), e.getMessage(), e);

            return ScheduledTransferResult.builder()
                    .scheduleId(transfer.getScheduleId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Generates a unique transaction ID for a scheduled transfer execution.
     */
    private String generateTransactionId(String scheduleId) {
        return "SCH-" + scheduleId + "-" + System.currentTimeMillis();
    }

    /**
     * Gets all active scheduled transfers.
     */
    public List<ScheduledTransfer> getActiveScheduledTransfers() {
        return repository.findActiveTransfers();
    }

    /**
     * Suspends a scheduled transfer.
     */
    public boolean suspendScheduledTransfer(String scheduleId, String customerId) {
        Optional<ScheduledTransfer> transferOpt = repository.findById(scheduleId);
        if (transferOpt.isEmpty()) {
            return false;
        }

        ScheduledTransfer transfer = transferOpt.get();

        // Verify ownership
        if (!transfer.getCustomerId().equals(customerId)) {
            throw TransactionException.invalidRequest("Not authorized to suspend this transfer");
        }

        if (transfer.getStatus() != ScheduledTransferStatus.ACTIVE) {
            throw TransactionException.invalidRequest("Can only suspend active transfers");
        }

        transfer.setStatus(ScheduledTransferStatus.SUSPENDED);
        repository.update(transfer);
        log.info("Suspended scheduled transfer: {}", scheduleId);
        return true;
    }

    /**
     * Resumes a suspended scheduled transfer.
     */
    public boolean resumeScheduledTransfer(String scheduleId, String customerId) {
        Optional<ScheduledTransfer> transferOpt = repository.findById(scheduleId);
        if (transferOpt.isEmpty()) {
            return false;
        }

        ScheduledTransfer transfer = transferOpt.get();

        // Verify ownership
        if (!transfer.getCustomerId().equals(customerId)) {
            throw TransactionException.invalidRequest("Not authorized to resume this transfer");
        }

        if (transfer.getStatus() != ScheduledTransferStatus.SUSPENDED) {
            throw TransactionException.invalidRequest("Can only resume suspended transfers");
        }

        transfer.setStatus(ScheduledTransferStatus.ACTIVE);
        repository.update(transfer);
        log.info("Resumed scheduled transfer: {}", scheduleId);
        return true;
    }
}
