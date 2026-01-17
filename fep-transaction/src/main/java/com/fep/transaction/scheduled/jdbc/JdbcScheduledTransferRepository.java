package com.fep.transaction.scheduled.jdbc;

import com.fep.db.util.JdbcUtils;
import com.fep.transaction.scheduled.RecurrenceType;
import com.fep.transaction.scheduled.ScheduledTransfer;
import com.fep.transaction.scheduled.ScheduledTransferRepository;
import com.fep.transaction.scheduled.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of ScheduledTransferRepository for Oracle database.
 */
@Slf4j
@Repository
@Profile({"oracle", "oracle-prod"})
@RequiredArgsConstructor
public class JdbcScheduledTransferRepository implements ScheduledTransferRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<ScheduledTransfer> ROW_MAPPER = (rs, rowNum) ->
        ScheduledTransfer.builder()
            .scheduleId(rs.getString("SCHEDULE_ID"))
            .sourceAccount(rs.getString("SOURCE_ACCOUNT"))
            .destinationAccount(rs.getString("DESTINATION_ACCOUNT"))
            .destinationBankCode(rs.getString("DESTINATION_BANK_CODE"))
            .amount(JdbcUtils.getBigDecimal(rs, "AMOUNT"))
            .currencyCode(rs.getString("CURRENCY_CODE"))
            .scheduledDate(JdbcUtils.getLocalDate(rs, "SCHEDULED_DATE"))
            .recurrenceType(parseRecurrenceType(rs.getString("RECURRENCE_TYPE")))
            .endDate(JdbcUtils.getLocalDate(rs, "END_DATE"))
            .remainingExecutions(JdbcUtils.getInteger(rs, "REMAINING_EXECUTIONS"))
            .memo(rs.getString("MEMO"))
            .status(ScheduledTransferStatus.valueOf(rs.getString("STATUS")))
            .customerId(rs.getString("CUSTOMER_ID"))
            .channel(rs.getString("CHANNEL"))
            .createdAt(JdbcUtils.getLocalDateTime(rs, "CREATED_AT"))
            .updatedAt(JdbcUtils.getLocalDateTime(rs, "UPDATED_AT"))
            .lastExecutedAt(JdbcUtils.getLocalDateTime(rs, "LAST_EXECUTED_AT"))
            .lastExecutionResult(rs.getString("LAST_EXECUTION_RESULT"))
            .successfulExecutions(rs.getInt("SUCCESSFUL_EXECUTIONS"))
            .failedExecutions(rs.getInt("FAILED_EXECUTIONS"))
            .build();

    private static RecurrenceType parseRecurrenceType(String value) {
        if (value == null) return RecurrenceType.ONE_TIME;
        try {
            return RecurrenceType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return RecurrenceType.ONE_TIME;
        }
    }

    @Override
    @Transactional
    public ScheduledTransfer save(ScheduledTransfer transfer) {
        String sql = """
            INSERT INTO FEP_SCHEDULED_TRANSFER (
                SCHEDULE_ID, SOURCE_ACCOUNT, DESTINATION_ACCOUNT, DESTINATION_BANK_CODE,
                AMOUNT, CURRENCY_CODE, SCHEDULED_DATE, RECURRENCE_TYPE, END_DATE,
                REMAINING_EXECUTIONS, MEMO, STATUS, CUSTOMER_ID, CHANNEL,
                CREATED_AT, UPDATED_AT, LAST_EXECUTED_AT, LAST_EXECUTION_RESULT,
                SUCCESSFUL_EXECUTIONS, FAILED_EXECUTIONS
            ) VALUES (
                :scheduleId, :sourceAccount, :destinationAccount, :destinationBankCode,
                :amount, :currencyCode, :scheduledDate, :recurrenceType, :endDate,
                :remainingExecutions, :memo, :status, :customerId, :channel,
                :createdAt, :updatedAt, :lastExecutedAt, :lastExecutionResult,
                :successfulExecutions, :failedExecutions
            )
            """;

        MapSqlParameterSource params = createParams(transfer);
        jdbcTemplate.update(sql, params);

        log.debug("Saved scheduled transfer: scheduleId={}", transfer.getScheduleId());
        return transfer;
    }

    @Override
    public Optional<ScheduledTransfer> findById(String scheduleId) {
        String sql = "SELECT * FROM FEP_SCHEDULED_TRANSFER WHERE SCHEDULE_ID = :scheduleId";
        try {
            ScheduledTransfer transfer = jdbcTemplate.queryForObject(sql, Map.of("scheduleId", scheduleId), ROW_MAPPER);
            return Optional.ofNullable(transfer);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ScheduledTransfer> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM FEP_SCHEDULED_TRANSFER WHERE CUSTOMER_ID = :customerId ORDER BY CREATED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("customerId", customerId), ROW_MAPPER);
    }

    @Override
    public List<ScheduledTransfer> findReadyForExecution(LocalDate date) {
        String sql = """
            SELECT * FROM FEP_SCHEDULED_TRANSFER
            WHERE SCHEDULED_DATE <= :date
              AND STATUS IN ('PENDING', 'ACTIVE')
              AND (END_DATE IS NULL OR END_DATE >= :date)
            ORDER BY SCHEDULED_DATE, CREATED_AT
            """;
        return jdbcTemplate.query(sql, Map.of("date", JdbcUtils.toSqlDate(date)), ROW_MAPPER);
    }

    @Override
    public List<ScheduledTransfer> findActiveTransfers() {
        String sql = "SELECT * FROM FEP_SCHEDULED_TRANSFER WHERE STATUS IN ('PENDING', 'ACTIVE') ORDER BY SCHEDULED_DATE";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    @Override
    @Transactional
    public ScheduledTransfer update(ScheduledTransfer transfer) {
        String sql = """
            UPDATE FEP_SCHEDULED_TRANSFER SET
                SOURCE_ACCOUNT = :sourceAccount,
                DESTINATION_ACCOUNT = :destinationAccount,
                DESTINATION_BANK_CODE = :destinationBankCode,
                AMOUNT = :amount,
                CURRENCY_CODE = :currencyCode,
                SCHEDULED_DATE = :scheduledDate,
                RECURRENCE_TYPE = :recurrenceType,
                END_DATE = :endDate,
                REMAINING_EXECUTIONS = :remainingExecutions,
                MEMO = :memo,
                STATUS = :status,
                CHANNEL = :channel,
                UPDATED_AT = :updatedAt,
                LAST_EXECUTED_AT = :lastExecutedAt,
                LAST_EXECUTION_RESULT = :lastExecutionResult,
                SUCCESSFUL_EXECUTIONS = :successfulExecutions,
                FAILED_EXECUTIONS = :failedExecutions
            WHERE SCHEDULE_ID = :scheduleId
            """;

        transfer.setUpdatedAt(LocalDateTime.now());
        MapSqlParameterSource params = createParams(transfer);
        jdbcTemplate.update(sql, params);

        log.debug("Updated scheduled transfer: scheduleId={}", transfer.getScheduleId());
        return transfer;
    }

    @Override
    @Transactional
    public boolean cancel(String scheduleId) {
        String sql = """
            UPDATE FEP_SCHEDULED_TRANSFER SET
                STATUS = 'CANCELLED',
                UPDATED_AT = :updatedAt
            WHERE SCHEDULE_ID = :scheduleId
              AND STATUS IN ('PENDING', 'ACTIVE', 'SUSPENDED')
            """;
        int updated = jdbcTemplate.update(sql, Map.of(
            "scheduleId", scheduleId,
            "updatedAt", JdbcUtils.toTimestamp(LocalDateTime.now())
        ));
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean delete(String scheduleId) {
        String sql = "DELETE FROM FEP_SCHEDULED_TRANSFER WHERE SCHEDULE_ID = :scheduleId";
        int deleted = jdbcTemplate.update(sql, Map.of("scheduleId", scheduleId));
        return deleted > 0;
    }

    @Override
    public long countByCustomerId(String customerId) {
        String sql = "SELECT COUNT(1) FROM FEP_SCHEDULED_TRANSFER WHERE CUSTOMER_ID = :customerId AND STATUS NOT IN ('COMPLETED', 'CANCELLED')";
        Long count = jdbcTemplate.queryForObject(sql, Map.of("customerId", customerId), Long.class);
        return count != null ? count : 0L;
    }

    private MapSqlParameterSource createParams(ScheduledTransfer transfer) {
        return new MapSqlParameterSource()
            .addValue("scheduleId", transfer.getScheduleId())
            .addValue("sourceAccount", transfer.getSourceAccount())
            .addValue("destinationAccount", transfer.getDestinationAccount())
            .addValue("destinationBankCode", transfer.getDestinationBankCode())
            .addValue("amount", transfer.getAmount())
            .addValue("currencyCode", transfer.getCurrencyCode())
            .addValue("scheduledDate", JdbcUtils.toSqlDate(transfer.getScheduledDate()))
            .addValue("recurrenceType", transfer.getRecurrenceType() != null ? transfer.getRecurrenceType().name() : "ONE_TIME")
            .addValue("endDate", JdbcUtils.toSqlDate(transfer.getEndDate()))
            .addValue("remainingExecutions", transfer.getRemainingExecutions())
            .addValue("memo", transfer.getMemo())
            .addValue("status", transfer.getStatus() != null ? transfer.getStatus().name() : "PENDING")
            .addValue("customerId", transfer.getCustomerId())
            .addValue("channel", transfer.getChannel())
            .addValue("createdAt", JdbcUtils.toTimestamp(transfer.getCreatedAt() != null ? transfer.getCreatedAt() : LocalDateTime.now()))
            .addValue("updatedAt", JdbcUtils.toTimestamp(transfer.getUpdatedAt()))
            .addValue("lastExecutedAt", JdbcUtils.toTimestamp(transfer.getLastExecutedAt()))
            .addValue("lastExecutionResult", transfer.getLastExecutionResult())
            .addValue("successfulExecutions", transfer.getSuccessfulExecutions())
            .addValue("failedExecutions", transfer.getFailedExecutions());
    }
}
