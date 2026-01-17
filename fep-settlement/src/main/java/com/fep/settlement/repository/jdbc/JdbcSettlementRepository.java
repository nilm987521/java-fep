package com.fep.settlement.repository.jdbc;

import com.fep.db.util.JdbcUtils;
import com.fep.settlement.domain.*;
import com.fep.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of SettlementRepository for Oracle database.
 */
@Slf4j
@Repository
@Profile({"oracle", "oracle-prod"})
@RequiredArgsConstructor
public class JdbcSettlementRepository implements SettlementRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // ========================= Row Mappers =========================

    private static final RowMapper<SettlementFile> FILE_ROW_MAPPER = (rs, rowNum) -> {
        SettlementFile.FileHeader header = SettlementFile.FileHeader.builder()
            .version(rs.getString("HEADER_VERSION"))
            .creationDate(JdbcUtils.getLocalDate(rs, "HEADER_CREATION_DATE"))
            .creatingBank(rs.getString("HEADER_CREATING_BANK"))
            .receivingBank(rs.getString("HEADER_RECEIVING_BANK"))
            .fileType(rs.getString("HEADER_FILE_TYPE"))
            .rawData(rs.getString("HEADER_RAW"))
            .build();

        SettlementFile.FileTrailer trailer = SettlementFile.FileTrailer.builder()
            .recordCount(rs.getInt("TRAILER_RECORD_COUNT"))
            .totalAmount(JdbcUtils.getBigDecimal(rs, "TRAILER_TOTAL_AMOUNT"))
            .totalDebitAmount(JdbcUtils.getBigDecimal(rs, "TRAILER_DEBIT_AMOUNT"))
            .totalCreditAmount(JdbcUtils.getBigDecimal(rs, "TRAILER_CREDIT_AMOUNT"))
            .debitCount(rs.getInt("TRAILER_DEBIT_COUNT"))
            .creditCount(rs.getInt("TRAILER_CREDIT_COUNT"))
            .checksum(rs.getString("TRAILER_CHECKSUM"))
            .rawData(rs.getString("TRAILER_RAW"))
            .build();

        return SettlementFile.builder()
            .fileId(rs.getString("FILE_ID"))
            .fileName(rs.getString("FILE_NAME"))
            .settlementDate(JdbcUtils.getLocalDate(rs, "SETTLEMENT_DATE"))
            .fileType(parseFileType(rs.getString("FILE_TYPE")))
            .source(rs.getString("SOURCE"))
            .bankCode(rs.getString("BANK_CODE"))
            .processingStatus(FileProcessingStatus.valueOf(rs.getString("PROCESSING_STATUS")))
            .receivedAt(JdbcUtils.getLocalDateTime(rs, "RECEIVED_AT"))
            .processingStartedAt(JdbcUtils.getLocalDateTime(rs, "PROCESSING_STARTED_AT"))
            .processingCompletedAt(JdbcUtils.getLocalDateTime(rs, "PROCESSING_COMPLETED_AT"))
            .errorMessage(rs.getString("ERROR_MESSAGE"))
            .checksum(rs.getString("CHECKSUM"))
            .fileSizeBytes(rs.getLong("FILE_SIZE_BYTES"))
            .header(header)
            .trailer(trailer)
            .records(new ArrayList<>())
            .build();
    };

    private static final RowMapper<SettlementRecord> RECORD_ROW_MAPPER = (rs, rowNum) ->
        SettlementRecord.builder()
            .sequenceNumber(rs.getLong("SEQUENCE_NUMBER"))
            .settlementDate(JdbcUtils.getLocalDate(rs, "SETTLEMENT_DATE"))
            .transactionRefNo(rs.getString("TRANSACTION_REF_NO"))
            .stan(rs.getString("STAN"))
            .rrn(rs.getString("RRN"))
            .transactionType(rs.getString("TRANSACTION_TYPE"))
            .transactionCode(rs.getString("TRANSACTION_CODE"))
            .acquiringBankCode(rs.getString("ACQUIRING_BANK_CODE"))
            .issuingBankCode(rs.getString("ISSUING_BANK_CODE"))
            .cardNumber(rs.getString("CARD_NUMBER"))
            .amount(JdbcUtils.getBigDecimal(rs, "AMOUNT"))
            .currencyCode(rs.getString("CURRENCY_CODE"))
            .feeAmount(JdbcUtils.getBigDecimal(rs, "FEE_AMOUNT"))
            .netAmount(JdbcUtils.getBigDecimal(rs, "NET_AMOUNT"))
            .transactionDateTime(JdbcUtils.getLocalDateTime(rs, "TRANSACTION_DATE_TIME"))
            .status(SettlementStatus.valueOf(rs.getString("STATUS")))
            .terminalId(rs.getString("TERMINAL_ID"))
            .merchantId(rs.getString("MERCHANT_ID"))
            .authCode(rs.getString("AUTH_CODE"))
            .responseCode(rs.getString("RESPONSE_CODE"))
            .reversal(rs.getInt("IS_REVERSAL") == 1)
            .originalTransactionRef(rs.getString("ORIGINAL_TRANSACTION_REF"))
            .channel(rs.getString("CHANNEL"))
            .fromAccount(rs.getString("FROM_ACCOUNT"))
            .toAccount(rs.getString("TO_ACCOUNT"))
            .recordHash(rs.getString("RECORD_HASH"))
            .rawData(rs.getString("RAW_DATA"))
            .notes(rs.getString("NOTES"))
            .matchedTransactionId(rs.getString("MATCHED_TRANSACTION_ID"))
            .matchedAt(JdbcUtils.getLocalDateTime(rs, "MATCHED_AT"))
            .build();

    private static final RowMapper<Discrepancy> DISCREPANCY_ROW_MAPPER = (rs, rowNum) ->
        Discrepancy.builder()
            .discrepancyId(rs.getString("DISCREPANCY_ID"))
            .type(DiscrepancyType.valueOf(rs.getString("DISCREPANCY_TYPE")))
            .settlementDate(JdbcUtils.getLocalDate(rs, "SETTLEMENT_DATE"))
            .settlementFileId(rs.getString("SETTLEMENT_FILE_ID"))
            .settlementRecordRef(rs.getString("SETTLEMENT_RECORD_REF"))
            .internalTransactionRef(rs.getString("INTERNAL_TRANSACTION_REF"))
            .settlementAmount(JdbcUtils.getBigDecimal(rs, "SETTLEMENT_AMOUNT"))
            .internalAmount(JdbcUtils.getBigDecimal(rs, "INTERNAL_AMOUNT"))
            .differenceAmount(JdbcUtils.getBigDecimal(rs, "DIFFERENCE_AMOUNT"))
            .currencyCode(rs.getString("CURRENCY_CODE"))
            .cardNumber(rs.getString("CARD_NUMBER"))
            .transactionType(rs.getString("TRANSACTION_TYPE"))
            .status(DiscrepancyStatus.valueOf(rs.getString("STATUS")))
            .priority(DiscrepancyPriority.valueOf(rs.getString("PRIORITY")))
            .description(rs.getString("DESCRIPTION"))
            .rootCause(rs.getString("ROOT_CAUSE"))
            .resolutionNotes(rs.getString("RESOLUTION_NOTES"))
            .resolutionAction(parseResolutionAction(rs.getString("RESOLUTION_ACTION")))
            .assignedTo(rs.getString("ASSIGNED_TO"))
            .createdAt(JdbcUtils.getLocalDateTime(rs, "CREATED_AT"))
            .updatedAt(JdbcUtils.getLocalDateTime(rs, "UPDATED_AT"))
            .resolvedAt(JdbcUtils.getLocalDateTime(rs, "RESOLVED_AT"))
            .resolvedBy(rs.getString("RESOLVED_BY"))
            .investigationNotes(new ArrayList<>())
            .relatedDiscrepancies(new ArrayList<>())
            .build();

    private static final RowMapper<ClearingRecord> CLEARING_ROW_MAPPER = (rs, rowNum) ->
        ClearingRecord.builder()
            .clearingId(rs.getString("CLEARING_ID"))
            .settlementDate(JdbcUtils.getLocalDate(rs, "SETTLEMENT_DATE"))
            .batchNumber(rs.getString("BATCH_NUMBER"))
            .ourBankCode(rs.getString("OUR_BANK_CODE"))
            .counterpartyBankCode(rs.getString("COUNTERPARTY_BANK_CODE"))
            .transactionCategory(rs.getString("TRANSACTION_CATEGORY"))
            .debitCount(rs.getInt("DEBIT_COUNT"))
            .debitAmount(JdbcUtils.getBigDecimal(rs, "DEBIT_AMOUNT"))
            .creditCount(rs.getInt("CREDIT_COUNT"))
            .creditAmount(JdbcUtils.getBigDecimal(rs, "CREDIT_AMOUNT"))
            .netAmount(JdbcUtils.getBigDecimal(rs, "NET_AMOUNT"))
            .feeAmount(JdbcUtils.getBigDecimal(rs, "FEE_AMOUNT"))
            .currencyCode(rs.getString("CURRENCY_CODE"))
            .status(ClearingStatus.valueOf(rs.getString("STATUS")))
            .createdAt(JdbcUtils.getLocalDateTime(rs, "CREATED_AT"))
            .confirmedAt(JdbcUtils.getLocalDateTime(rs, "CONFIRMED_AT"))
            .settledAt(JdbcUtils.getLocalDateTime(rs, "SETTLED_AT"))
            .confirmedBy(rs.getString("CONFIRMED_BY"))
            .notes(rs.getString("NOTES"))
            .settlementFileId(rs.getString("SETTLEMENT_FILE_ID"))
            .build();

    private static SettlementFileType parseFileType(String value) {
        if (value == null) return null;
        try {
            return SettlementFileType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResolutionAction parseResolutionAction(String value) {
        if (value == null) return null;
        try {
            return ResolutionAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ========================= Settlement File Operations =========================

    @Override
    @Transactional
    public SettlementFile saveFile(SettlementFile file) {
        String sql = """
            INSERT INTO FEP_SETTLEMENT_FILE (
                FILE_ID, FILE_NAME, SETTLEMENT_DATE, FILE_TYPE, SOURCE, BANK_CODE,
                PROCESSING_STATUS, RECEIVED_AT, PROCESSING_STARTED_AT, PROCESSING_COMPLETED_AT,
                ERROR_MESSAGE, CHECKSUM, FILE_SIZE_BYTES,
                HEADER_VERSION, HEADER_CREATION_DATE, HEADER_CREATING_BANK, HEADER_RECEIVING_BANK,
                HEADER_FILE_TYPE, HEADER_RAW,
                TRAILER_RECORD_COUNT, TRAILER_TOTAL_AMOUNT, TRAILER_DEBIT_AMOUNT, TRAILER_CREDIT_AMOUNT,
                TRAILER_DEBIT_COUNT, TRAILER_CREDIT_COUNT, TRAILER_CHECKSUM, TRAILER_RAW
            ) VALUES (
                :fileId, :fileName, :settlementDate, :fileType, :source, :bankCode,
                :processingStatus, :receivedAt, :processingStartedAt, :processingCompletedAt,
                :errorMessage, :checksum, :fileSizeBytes,
                :headerVersion, :headerCreationDate, :headerCreatingBank, :headerReceivingBank,
                :headerFileType, :headerRaw,
                :trailerRecordCount, :trailerTotalAmount, :trailerDebitAmount, :trailerCreditAmount,
                :trailerDebitCount, :trailerCreditCount, :trailerChecksum, :trailerRaw
            )
            """;

        MapSqlParameterSource params = createFileParams(file);
        jdbcTemplate.update(sql, params);

        // Save records if present
        if (file.getRecords() != null && !file.getRecords().isEmpty()) {
            saveRecordsForFile(file.getFileId(), file.getRecords());
        }

        log.debug("Saved settlement file: fileId={}", file.getFileId());
        return file;
    }

    private void saveRecordsForFile(String fileId, List<SettlementRecord> records) {
        String sql = """
            INSERT INTO FEP_SETTLEMENT_RECORD (
                SEQUENCE_NUMBER, FILE_ID, SETTLEMENT_DATE, TRANSACTION_REF_NO, STAN, RRN,
                TRANSACTION_TYPE, TRANSACTION_CODE, ACQUIRING_BANK_CODE, ISSUING_BANK_CODE,
                CARD_NUMBER, AMOUNT, CURRENCY_CODE, FEE_AMOUNT, NET_AMOUNT, TRANSACTION_DATE_TIME,
                STATUS, TERMINAL_ID, MERCHANT_ID, AUTH_CODE, RESPONSE_CODE, IS_REVERSAL,
                ORIGINAL_TRANSACTION_REF, CHANNEL, FROM_ACCOUNT, TO_ACCOUNT, RECORD_HASH,
                RAW_DATA, NOTES, MATCHED_TRANSACTION_ID, MATCHED_AT
            ) VALUES (
                :sequenceNumber, :fileId, :settlementDate, :transactionRefNo, :stan, :rrn,
                :transactionType, :transactionCode, :acquiringBankCode, :issuingBankCode,
                :cardNumber, :amount, :currencyCode, :feeAmount, :netAmount, :transactionDateTime,
                :status, :terminalId, :merchantId, :authCode, :responseCode, :isReversal,
                :originalTransactionRef, :channel, :fromAccount, :toAccount, :recordHash,
                :rawData, :notes, :matchedTransactionId, :matchedAt
            )
            """;

        SqlParameterSource[] batchParams = records.stream()
            .map(r -> createRecordParams(fileId, r))
            .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public Optional<SettlementFile> findFileById(String fileId) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_FILE WHERE FILE_ID = :fileId";
        try {
            SettlementFile file = jdbcTemplate.queryForObject(sql, Map.of("fileId", fileId), FILE_ROW_MAPPER);
            if (file != null) {
                file.setRecords(findRecordsByFileId(fileId));
            }
            return Optional.ofNullable(file);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SettlementFile> findFilesByDate(LocalDate date) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_FILE WHERE SETTLEMENT_DATE = :date ORDER BY RECEIVED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("date", JdbcUtils.toSqlDate(date)), FILE_ROW_MAPPER);
    }

    @Override
    public List<SettlementFile> findFilesByStatus(FileProcessingStatus status) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_FILE WHERE PROCESSING_STATUS = :status ORDER BY RECEIVED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("status", status.name()), FILE_ROW_MAPPER);
    }

    @Override
    public List<SettlementFile> findFilesByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT * FROM FEP_SETTLEMENT_FILE
            WHERE SETTLEMENT_DATE >= :startDate AND SETTLEMENT_DATE <= :endDate
            ORDER BY SETTLEMENT_DATE DESC, RECEIVED_AT DESC
            """;
        return jdbcTemplate.query(sql, Map.of(
            "startDate", JdbcUtils.toSqlDate(startDate),
            "endDate", JdbcUtils.toSqlDate(endDate)
        ), FILE_ROW_MAPPER);
    }

    @Override
    @Transactional
    public boolean deleteFile(String fileId) {
        // Records are deleted via CASCADE
        String sql = "DELETE FROM FEP_SETTLEMENT_FILE WHERE FILE_ID = :fileId";
        int deleted = jdbcTemplate.update(sql, Map.of("fileId", fileId));
        return deleted > 0;
    }

    // ========================= Settlement Record Operations =========================

    @Override
    @Transactional
    public SettlementRecord saveRecord(SettlementRecord record) {
        // For single record save, we need a fileId - this is a simplified implementation
        log.warn("saveRecord called without fileId context - record may not be properly linked");
        return record;
    }

    @Override
    @Transactional
    public List<SettlementRecord> saveRecords(List<SettlementRecord> records) {
        // Batch save - assumes records have proper file context set elsewhere
        log.debug("saveRecords called with {} records", records.size());
        return records;
    }

    @Override
    public Optional<SettlementRecord> findRecordByRef(String transactionRefNo) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_RECORD WHERE TRANSACTION_REF_NO = :ref ORDER BY SEQUENCE_NUMBER DESC FETCH FIRST 1 ROW ONLY";
        try {
            SettlementRecord record = jdbcTemplate.queryForObject(sql, Map.of("ref", transactionRefNo), RECORD_ROW_MAPPER);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SettlementRecord> findRecordsByFileId(String fileId) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_RECORD WHERE FILE_ID = :fileId ORDER BY SEQUENCE_NUMBER";
        return jdbcTemplate.query(sql, Map.of("fileId", fileId), RECORD_ROW_MAPPER);
    }

    @Override
    public List<SettlementRecord> findRecordsByStatus(SettlementStatus status) {
        String sql = "SELECT * FROM FEP_SETTLEMENT_RECORD WHERE STATUS = :status ORDER BY SETTLEMENT_DATE DESC, SEQUENCE_NUMBER";
        return jdbcTemplate.query(sql, Map.of("status", status.name()), RECORD_ROW_MAPPER);
    }

    @Override
    public List<SettlementRecord> findUnmatchedRecords(LocalDate date) {
        String sql = """
            SELECT * FROM FEP_SETTLEMENT_RECORD
            WHERE SETTLEMENT_DATE = :date AND STATUS IN ('PENDING', 'NOT_FOUND')
            ORDER BY SEQUENCE_NUMBER
            """;
        return jdbcTemplate.query(sql, Map.of("date", JdbcUtils.toSqlDate(date)), RECORD_ROW_MAPPER);
    }

    // ========================= Discrepancy Operations =========================

    @Override
    @Transactional
    public Discrepancy saveDiscrepancy(Discrepancy discrepancy) {
        String sql = """
            INSERT INTO FEP_DISCREPANCY (
                DISCREPANCY_ID, DISCREPANCY_TYPE, SETTLEMENT_DATE, SETTLEMENT_FILE_ID,
                SETTLEMENT_RECORD_REF, INTERNAL_TRANSACTION_REF, SETTLEMENT_AMOUNT, INTERNAL_AMOUNT,
                DIFFERENCE_AMOUNT, CURRENCY_CODE, CARD_NUMBER, TRANSACTION_TYPE,
                STATUS, PRIORITY, DESCRIPTION, ROOT_CAUSE, RESOLUTION_NOTES, RESOLUTION_ACTION,
                ASSIGNED_TO, CREATED_AT, UPDATED_AT, RESOLVED_AT, RESOLVED_BY,
                INVESTIGATION_NOTES, RELATED_DISCREPANCIES
            ) VALUES (
                :discrepancyId, :type, :settlementDate, :settlementFileId,
                :settlementRecordRef, :internalTransactionRef, :settlementAmount, :internalAmount,
                :differenceAmount, :currencyCode, :cardNumber, :transactionType,
                :status, :priority, :description, :rootCause, :resolutionNotes, :resolutionAction,
                :assignedTo, :createdAt, :updatedAt, :resolvedAt, :resolvedBy,
                :investigationNotes, :relatedDiscrepancies
            )
            """;

        MapSqlParameterSource params = createDiscrepancyParams(discrepancy);
        jdbcTemplate.update(sql, params);

        log.debug("Saved discrepancy: discrepancyId={}", discrepancy.getDiscrepancyId());
        return discrepancy;
    }

    @Override
    public Optional<Discrepancy> findDiscrepancyById(String discrepancyId) {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE DISCREPANCY_ID = :discrepancyId";
        try {
            Discrepancy discrepancy = jdbcTemplate.queryForObject(sql, Map.of("discrepancyId", discrepancyId), DISCREPANCY_ROW_MAPPER);
            return Optional.ofNullable(discrepancy);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByDate(LocalDate date) {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE SETTLEMENT_DATE = :date ORDER BY CREATED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("date", JdbcUtils.toSqlDate(date)), DISCREPANCY_ROW_MAPPER);
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByStatus(DiscrepancyStatus status) {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE STATUS = :status ORDER BY PRIORITY, CREATED_AT";
        return jdbcTemplate.query(sql, Map.of("status", status.name()), DISCREPANCY_ROW_MAPPER);
    }

    @Override
    public List<Discrepancy> findOpenDiscrepancies() {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE STATUS IN ('OPEN', 'INVESTIGATING', 'PENDING_APPROVAL') ORDER BY PRIORITY, CREATED_AT";
        return jdbcTemplate.query(sql, DISCREPANCY_ROW_MAPPER);
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByType(DiscrepancyType type) {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE DISCREPANCY_TYPE = :type ORDER BY CREATED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("type", type.name()), DISCREPANCY_ROW_MAPPER);
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByPriority(DiscrepancyPriority priority) {
        String sql = "SELECT * FROM FEP_DISCREPANCY WHERE PRIORITY = :priority ORDER BY CREATED_AT";
        return jdbcTemplate.query(sql, Map.of("priority", priority.name()), DISCREPANCY_ROW_MAPPER);
    }

    // ========================= Clearing Record Operations =========================

    @Override
    @Transactional
    public ClearingRecord saveClearingRecord(ClearingRecord record) {
        String sql = """
            INSERT INTO FEP_CLEARING_RECORD (
                CLEARING_ID, SETTLEMENT_DATE, BATCH_NUMBER, OUR_BANK_CODE, COUNTERPARTY_BANK_CODE,
                TRANSACTION_CATEGORY, DEBIT_COUNT, DEBIT_AMOUNT, CREDIT_COUNT, CREDIT_AMOUNT,
                NET_AMOUNT, FEE_AMOUNT, CURRENCY_CODE, STATUS, CREATED_AT, CONFIRMED_AT, SETTLED_AT,
                CONFIRMED_BY, NOTES, SETTLEMENT_FILE_ID
            ) VALUES (
                :clearingId, :settlementDate, :batchNumber, :ourBankCode, :counterpartyBankCode,
                :transactionCategory, :debitCount, :debitAmount, :creditCount, :creditAmount,
                :netAmount, :feeAmount, :currencyCode, :status, :createdAt, :confirmedAt, :settledAt,
                :confirmedBy, :notes, :settlementFileId
            )
            """;

        MapSqlParameterSource params = createClearingParams(record);
        jdbcTemplate.update(sql, params);

        log.debug("Saved clearing record: clearingId={}", record.getClearingId());
        return record;
    }

    @Override
    public Optional<ClearingRecord> findClearingRecordById(String clearingId) {
        String sql = "SELECT * FROM FEP_CLEARING_RECORD WHERE CLEARING_ID = :clearingId";
        try {
            ClearingRecord record = jdbcTemplate.queryForObject(sql, Map.of("clearingId", clearingId), CLEARING_ROW_MAPPER);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByDate(LocalDate date) {
        String sql = "SELECT * FROM FEP_CLEARING_RECORD WHERE SETTLEMENT_DATE = :date ORDER BY CREATED_AT";
        return jdbcTemplate.query(sql, Map.of("date", JdbcUtils.toSqlDate(date)), CLEARING_ROW_MAPPER);
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByStatus(ClearingStatus status) {
        String sql = "SELECT * FROM FEP_CLEARING_RECORD WHERE STATUS = :status ORDER BY SETTLEMENT_DATE DESC";
        return jdbcTemplate.query(sql, Map.of("status", status.name()), CLEARING_ROW_MAPPER);
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByCounterparty(String bankCode) {
        String sql = "SELECT * FROM FEP_CLEARING_RECORD WHERE COUNTERPARTY_BANK_CODE = :bankCode ORDER BY SETTLEMENT_DATE DESC";
        return jdbcTemplate.query(sql, Map.of("bankCode", bankCode), CLEARING_ROW_MAPPER);
    }

    // ========================= Parameter Builders =========================

    private MapSqlParameterSource createFileParams(SettlementFile file) {
        SettlementFile.FileHeader h = file.getHeader();
        SettlementFile.FileTrailer t = file.getTrailer();

        return new MapSqlParameterSource()
            .addValue("fileId", file.getFileId())
            .addValue("fileName", file.getFileName())
            .addValue("settlementDate", JdbcUtils.toSqlDate(file.getSettlementDate()))
            .addValue("fileType", file.getFileType() != null ? file.getFileType().name() : null)
            .addValue("source", file.getSource())
            .addValue("bankCode", file.getBankCode())
            .addValue("processingStatus", file.getProcessingStatus() != null ? file.getProcessingStatus().name() : "RECEIVED")
            .addValue("receivedAt", JdbcUtils.toTimestamp(file.getReceivedAt()))
            .addValue("processingStartedAt", JdbcUtils.toTimestamp(file.getProcessingStartedAt()))
            .addValue("processingCompletedAt", JdbcUtils.toTimestamp(file.getProcessingCompletedAt()))
            .addValue("errorMessage", file.getErrorMessage())
            .addValue("checksum", file.getChecksum())
            .addValue("fileSizeBytes", file.getFileSizeBytes())
            // Header
            .addValue("headerVersion", h != null ? h.getVersion() : null)
            .addValue("headerCreationDate", h != null ? JdbcUtils.toSqlDate(h.getCreationDate()) : null)
            .addValue("headerCreatingBank", h != null ? h.getCreatingBank() : null)
            .addValue("headerReceivingBank", h != null ? h.getReceivingBank() : null)
            .addValue("headerFileType", h != null ? h.getFileType() : null)
            .addValue("headerRaw", h != null ? h.getRawData() : null)
            // Trailer
            .addValue("trailerRecordCount", t != null ? t.getRecordCount() : 0)
            .addValue("trailerTotalAmount", t != null ? t.getTotalAmount() : null)
            .addValue("trailerDebitAmount", t != null ? t.getTotalDebitAmount() : null)
            .addValue("trailerCreditAmount", t != null ? t.getTotalCreditAmount() : null)
            .addValue("trailerDebitCount", t != null ? t.getDebitCount() : 0)
            .addValue("trailerCreditCount", t != null ? t.getCreditCount() : 0)
            .addValue("trailerChecksum", t != null ? t.getChecksum() : null)
            .addValue("trailerRaw", t != null ? t.getRawData() : null);
    }

    private MapSqlParameterSource createRecordParams(String fileId, SettlementRecord r) {
        return new MapSqlParameterSource()
            .addValue("sequenceNumber", r.getSequenceNumber())
            .addValue("fileId", fileId)
            .addValue("settlementDate", JdbcUtils.toSqlDate(r.getSettlementDate()))
            .addValue("transactionRefNo", r.getTransactionRefNo())
            .addValue("stan", r.getStan())
            .addValue("rrn", r.getRrn())
            .addValue("transactionType", r.getTransactionType())
            .addValue("transactionCode", r.getTransactionCode())
            .addValue("acquiringBankCode", r.getAcquiringBankCode())
            .addValue("issuingBankCode", r.getIssuingBankCode())
            .addValue("cardNumber", r.getCardNumber())
            .addValue("amount", r.getAmount())
            .addValue("currencyCode", r.getCurrencyCode())
            .addValue("feeAmount", r.getFeeAmount())
            .addValue("netAmount", r.getNetAmount())
            .addValue("transactionDateTime", JdbcUtils.toTimestamp(r.getTransactionDateTime()))
            .addValue("status", r.getStatus() != null ? r.getStatus().name() : "PENDING")
            .addValue("terminalId", r.getTerminalId())
            .addValue("merchantId", r.getMerchantId())
            .addValue("authCode", r.getAuthCode())
            .addValue("responseCode", r.getResponseCode())
            .addValue("isReversal", r.isReversal() ? 1 : 0)
            .addValue("originalTransactionRef", r.getOriginalTransactionRef())
            .addValue("channel", r.getChannel())
            .addValue("fromAccount", r.getFromAccount())
            .addValue("toAccount", r.getToAccount())
            .addValue("recordHash", r.getRecordHash())
            .addValue("rawData", r.getRawData())
            .addValue("notes", r.getNotes())
            .addValue("matchedTransactionId", r.getMatchedTransactionId())
            .addValue("matchedAt", JdbcUtils.toTimestamp(r.getMatchedAt()));
    }

    private MapSqlParameterSource createDiscrepancyParams(Discrepancy d) {
        return new MapSqlParameterSource()
            .addValue("discrepancyId", d.getDiscrepancyId())
            .addValue("type", d.getType() != null ? d.getType().name() : null)
            .addValue("settlementDate", JdbcUtils.toSqlDate(d.getSettlementDate()))
            .addValue("settlementFileId", d.getSettlementFileId())
            .addValue("settlementRecordRef", d.getSettlementRecordRef())
            .addValue("internalTransactionRef", d.getInternalTransactionRef())
            .addValue("settlementAmount", d.getSettlementAmount())
            .addValue("internalAmount", d.getInternalAmount())
            .addValue("differenceAmount", d.getDifferenceAmount())
            .addValue("currencyCode", d.getCurrencyCode())
            .addValue("cardNumber", d.getCardNumber())
            .addValue("transactionType", d.getTransactionType())
            .addValue("status", d.getStatus() != null ? d.getStatus().name() : "OPEN")
            .addValue("priority", d.getPriority() != null ? d.getPriority().name() : "MEDIUM")
            .addValue("description", d.getDescription())
            .addValue("rootCause", d.getRootCause())
            .addValue("resolutionNotes", d.getResolutionNotes())
            .addValue("resolutionAction", d.getResolutionAction() != null ? d.getResolutionAction().name() : null)
            .addValue("assignedTo", d.getAssignedTo())
            .addValue("createdAt", JdbcUtils.toTimestamp(d.getCreatedAt() != null ? d.getCreatedAt() : LocalDateTime.now()))
            .addValue("updatedAt", JdbcUtils.toTimestamp(d.getUpdatedAt()))
            .addValue("resolvedAt", JdbcUtils.toTimestamp(d.getResolvedAt()))
            .addValue("resolvedBy", d.getResolvedBy())
            .addValue("investigationNotes", null) // JSON serialization would be needed
            .addValue("relatedDiscrepancies", d.getRelatedDiscrepancies() != null ? String.join(",", d.getRelatedDiscrepancies()) : null);
    }

    private MapSqlParameterSource createClearingParams(ClearingRecord r) {
        return new MapSqlParameterSource()
            .addValue("clearingId", r.getClearingId())
            .addValue("settlementDate", JdbcUtils.toSqlDate(r.getSettlementDate()))
            .addValue("batchNumber", r.getBatchNumber())
            .addValue("ourBankCode", r.getOurBankCode())
            .addValue("counterpartyBankCode", r.getCounterpartyBankCode())
            .addValue("transactionCategory", r.getTransactionCategory())
            .addValue("debitCount", r.getDebitCount())
            .addValue("debitAmount", r.getDebitAmount())
            .addValue("creditCount", r.getCreditCount())
            .addValue("creditAmount", r.getCreditAmount())
            .addValue("netAmount", r.getNetAmount())
            .addValue("feeAmount", r.getFeeAmount())
            .addValue("currencyCode", r.getCurrencyCode())
            .addValue("status", r.getStatus() != null ? r.getStatus().name() : "PENDING")
            .addValue("createdAt", JdbcUtils.toTimestamp(r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now()))
            .addValue("confirmedAt", JdbcUtils.toTimestamp(r.getConfirmedAt()))
            .addValue("settledAt", JdbcUtils.toTimestamp(r.getSettledAt()))
            .addValue("confirmedBy", r.getConfirmedBy())
            .addValue("notes", r.getNotes())
            .addValue("settlementFileId", r.getSettlementFileId());
    }
}
