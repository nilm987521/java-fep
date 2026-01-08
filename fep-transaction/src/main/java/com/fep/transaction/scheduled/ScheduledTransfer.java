package com.fep.transaction.scheduled;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a scheduled transfer request (預約轉帳).
 */
@Data
@Builder
public class ScheduledTransfer {

    /** Unique identifier for the scheduled transfer */
    private String scheduleId;

    /** Source account number */
    private String sourceAccount;

    /** Destination account number */
    private String destinationAccount;

    /** Destination bank code */
    private String destinationBankCode;

    /** Transfer amount */
    private BigDecimal amount;

    /** Currency code */
    @Builder.Default
    private String currencyCode = "901"; // TWD

    /** Scheduled execution date */
    private LocalDate scheduledDate;

    /** Recurrence type (ONE_TIME, DAILY, WEEKLY, MONTHLY) */
    @Builder.Default
    private RecurrenceType recurrenceType = RecurrenceType.ONE_TIME;

    /** End date for recurring transfers (null for ONE_TIME) */
    private LocalDate endDate;

    /** Number of remaining executions (null for unlimited) */
    private Integer remainingExecutions;

    /** Transfer memo/description */
    private String memo;

    /** Status of the scheduled transfer */
    @Builder.Default
    private ScheduledTransferStatus status = ScheduledTransferStatus.PENDING;

    /** Customer ID who created the transfer */
    private String customerId;

    /** Channel used to create the transfer */
    private String channel;

    /** Creation timestamp */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Last modification timestamp */
    private LocalDateTime updatedAt;

    /** Last execution timestamp */
    private LocalDateTime lastExecutedAt;

    /** Last execution result */
    private String lastExecutionResult;

    /** Total successful executions */
    @Builder.Default
    private int successfulExecutions = 0;

    /** Total failed executions */
    @Builder.Default
    private int failedExecutions = 0;

    /**
     * Checks if this scheduled transfer is ready for execution.
     */
    public boolean isReadyForExecution(LocalDate today) {
        if (status != ScheduledTransferStatus.ACTIVE && 
            status != ScheduledTransferStatus.PENDING) {
            return false;
        }
        
        if (scheduledDate.isAfter(today)) {
            return false;
        }

        // Check end date for recurring transfers
        if (endDate != null && today.isAfter(endDate)) {
            return false;
        }

        // Check remaining executions
        if (remainingExecutions != null && remainingExecutions <= 0) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the next execution date based on recurrence type.
     */
    public LocalDate getNextExecutionDate() {
        if (recurrenceType == RecurrenceType.ONE_TIME) {
            return null;
        }

        LocalDate nextDate = switch (recurrenceType) {
            case DAILY -> scheduledDate.plusDays(1);
            case WEEKLY -> scheduledDate.plusWeeks(1);
            case MONTHLY -> scheduledDate.plusMonths(1);
            default -> null;
        };

        // Check if next date is within valid range
        if (nextDate != null && endDate != null && nextDate.isAfter(endDate)) {
            return null;
        }

        return nextDate;
    }

    /**
     * Marks the transfer as executed successfully.
     */
    public void markExecuted(String result) {
        this.lastExecutedAt = LocalDateTime.now();
        this.lastExecutionResult = result;
        this.successfulExecutions++;
        this.updatedAt = LocalDateTime.now();

        if (recurrenceType == RecurrenceType.ONE_TIME) {
            this.status = ScheduledTransferStatus.COMPLETED;
        } else {
            LocalDate nextDate = getNextExecutionDate();
            if (nextDate != null) {
                this.scheduledDate = nextDate;
            } else {
                this.status = ScheduledTransferStatus.COMPLETED;
            }

            if (remainingExecutions != null) {
                this.remainingExecutions--;
                if (remainingExecutions <= 0) {
                    this.status = ScheduledTransferStatus.COMPLETED;
                }
            }
        }
    }

    /**
     * Marks the transfer as failed.
     */
    public void markFailed(String errorMessage) {
        this.lastExecutedAt = LocalDateTime.now();
        this.lastExecutionResult = "FAILED: " + errorMessage;
        this.failedExecutions++;
        this.updatedAt = LocalDateTime.now();

        // Don't change status for recurring transfers on single failure
        if (recurrenceType == RecurrenceType.ONE_TIME) {
            this.status = ScheduledTransferStatus.FAILED;
        }
    }
}
