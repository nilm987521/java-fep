package com.fep.transaction.repository.jdbc;

import com.fep.db.util.JdbcUtils;
import com.fep.security.pan.PanEncryptionService;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of TransactionRepository for Oracle database.
 * PAN (Primary Account Number) is encrypted at rest using PanEncryptionService.
 */
@Slf4j
@Repository
@Profile({"oracle", "oracle-prod"})
public class JdbcTransactionRepository implements TransactionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PanEncryptionService panEncryptionService;
    private final RowMapper<TransactionRecord> rowMapper;

    public JdbcTransactionRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                      PanEncryptionService panEncryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.panEncryptionService = panEncryptionService;
        this.rowMapper = createRowMapper();
    }

    private RowMapper<TransactionRecord> createRowMapper() {
        return (rs, rowNum) -> {
            String encryptedPan = rs.getString("PAN");
            String decryptedPan = decryptPanSafely(encryptedPan);

            return TransactionRecord.builder()
                .id(JdbcUtils.getLong(rs, "ID"))
                .transactionId(rs.getString("TRANSACTION_ID"))
                .transactionType(parseTransactionType(rs.getString("TRANSACTION_TYPE")))
                .processingCode(rs.getString("PROCESSING_CODE"))
                .maskedPan(rs.getString("MASKED_PAN"))
                .pan(decryptedPan)
                .panHash(rs.getString("PAN_HASH"))
                .amount(JdbcUtils.getBigDecimal(rs, "AMOUNT"))
                .currencyCode(rs.getString("CURRENCY_CODE"))
                .sourceAccount(rs.getString("SOURCE_ACCOUNT"))
                .destinationAccount(rs.getString("DESTINATION_ACCOUNT"))
                .destinationBankCode(rs.getString("DESTINATION_BANK_CODE"))
                .terminalId(rs.getString("TERMINAL_ID"))
                .merchantId(rs.getString("MERCHANT_ID"))
                .acquiringBankCode(rs.getString("ACQUIRING_BANK_CODE"))
                .stan(rs.getString("STAN"))
                .rrn(rs.getString("RRN"))
                .channel(rs.getString("CHANNEL"))
                .status(TransactionStatus.valueOf(rs.getString("STATUS")))
                .responseCode(rs.getString("RESPONSE_CODE"))
                .authorizationCode(rs.getString("AUTHORIZATION_CODE"))
                .hostReferenceNumber(rs.getString("HOST_REFERENCE_NUMBER"))
                .originalTransactionId(rs.getString("ORIGINAL_TRANSACTION_ID"))
                .requestTime(JdbcUtils.getLocalDateTime(rs, "REQUEST_TIME"))
                .transactionTime(JdbcUtils.getLocalDateTime(rs, "TRANSACTION_TIME"))
                .responseTime(JdbcUtils.getLocalDateTime(rs, "RESPONSE_TIME"))
                .processingTimeMs(JdbcUtils.getLong(rs, "PROCESSING_TIME_MS"))
                .errorDetails(rs.getString("ERROR_DETAILS"))
                .createdAt(JdbcUtils.getLocalDateTime(rs, "CREATED_AT"))
                .updatedAt(JdbcUtils.getLocalDateTime(rs, "UPDATED_AT"))
                .transactionDate(rs.getString("TRANSACTION_DATE"))
                .build();
        };
    }

    /**
     * Safely decrypts PAN, returning null if decryption fails.
     */
    private String decryptPanSafely(String encryptedPan) {
        if (encryptedPan == null || encryptedPan.isEmpty()) {
            return null;
        }
        try {
            if (panEncryptionService.isEncrypted(encryptedPan)) {
                return panEncryptionService.decrypt(encryptedPan);
            }
            // Legacy data: return as-is (should be migrated)
            log.warn("Found unencrypted PAN in database, consider data migration");
            return encryptedPan;
        } catch (Exception e) {
            log.error("Failed to decrypt PAN: {}", e.getMessage());
            return null;
        }
    }

    private static TransactionType parseTransactionType(String value) {
        if (value == null) return null;
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @Transactional
    public TransactionRecord save(TransactionRecord record) {
        if (record.getId() == null) {
            return insert(record);
        }
        return update(record);
    }

    private TransactionRecord insert(TransactionRecord record) {
        String sql = """
            INSERT INTO FEP_TRANSACTION (
                TRANSACTION_ID, TRANSACTION_TYPE, PROCESSING_CODE, MASKED_PAN, PAN, PAN_HASH,
                AMOUNT, CURRENCY_CODE, SOURCE_ACCOUNT, DESTINATION_ACCOUNT, DESTINATION_BANK_CODE,
                TERMINAL_ID, MERCHANT_ID, ACQUIRING_BANK_CODE, STAN, RRN, CHANNEL,
                STATUS, RESPONSE_CODE, AUTHORIZATION_CODE, HOST_REFERENCE_NUMBER,
                ORIGINAL_TRANSACTION_ID, REQUEST_TIME, TRANSACTION_TIME, RESPONSE_TIME,
                PROCESSING_TIME_MS, ERROR_DETAILS, CREATED_AT, UPDATED_AT, TRANSACTION_DATE
            ) VALUES (
                :transactionId, :transactionType, :processingCode, :maskedPan, :pan, :panHash,
                :amount, :currencyCode, :sourceAccount, :destinationAccount, :destinationBankCode,
                :terminalId, :merchantId, :acquiringBankCode, :stan, :rrn, :channel,
                :status, :responseCode, :authorizationCode, :hostReferenceNumber,
                :originalTransactionId, :requestTime, :transactionTime, :responseTime,
                :processingTimeMs, :errorDetails, :createdAt, :updatedAt, :transactionDate
            )
            """;

        MapSqlParameterSource params = createParams(record);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID"});

        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }

        log.debug("Inserted transaction: id={}, transactionId={}", record.getId(), record.getTransactionId());
        return record;
    }

    private TransactionRecord update(TransactionRecord record) {
        String sql = """
            UPDATE FEP_TRANSACTION SET
                TRANSACTION_TYPE = :transactionType,
                PROCESSING_CODE = :processingCode,
                MASKED_PAN = :maskedPan,
                PAN = :pan,
                PAN_HASH = :panHash,
                AMOUNT = :amount,
                CURRENCY_CODE = :currencyCode,
                SOURCE_ACCOUNT = :sourceAccount,
                DESTINATION_ACCOUNT = :destinationAccount,
                DESTINATION_BANK_CODE = :destinationBankCode,
                TERMINAL_ID = :terminalId,
                MERCHANT_ID = :merchantId,
                ACQUIRING_BANK_CODE = :acquiringBankCode,
                STAN = :stan,
                RRN = :rrn,
                CHANNEL = :channel,
                STATUS = :status,
                RESPONSE_CODE = :responseCode,
                AUTHORIZATION_CODE = :authorizationCode,
                HOST_REFERENCE_NUMBER = :hostReferenceNumber,
                ORIGINAL_TRANSACTION_ID = :originalTransactionId,
                REQUEST_TIME = :requestTime,
                TRANSACTION_TIME = :transactionTime,
                RESPONSE_TIME = :responseTime,
                PROCESSING_TIME_MS = :processingTimeMs,
                ERROR_DETAILS = :errorDetails,
                UPDATED_AT = :updatedAt
            WHERE ID = :id
            """;

        record.setUpdatedAt(LocalDateTime.now());
        MapSqlParameterSource params = createParams(record);
        params.addValue("id", record.getId());

        jdbcTemplate.update(sql, params);
        log.debug("Updated transaction: id={}, transactionId={}", record.getId(), record.getTransactionId());
        return record;
    }

    private MapSqlParameterSource createParams(TransactionRecord record) {
        // Encrypt PAN before storing
        String encryptedPan = encryptPan(record.getPan());
        // Generate masked PAN and hash if not already set
        String maskedPan = record.getMaskedPan();
        String panHash = record.getPanHash();
        if (record.getPan() != null && !record.getPan().isEmpty()) {
            if (maskedPan == null || maskedPan.isEmpty()) {
                maskedPan = panEncryptionService.mask(record.getPan());
            }
            if (panHash == null || panHash.isEmpty()) {
                panHash = panEncryptionService.hash(record.getPan());
            }
        }

        return new MapSqlParameterSource()
            .addValue("transactionId", record.getTransactionId())
            .addValue("transactionType", record.getTransactionType() != null ? record.getTransactionType().name() : null)
            .addValue("processingCode", record.getProcessingCode())
            .addValue("maskedPan", maskedPan)
            .addValue("pan", encryptedPan)
            .addValue("panHash", panHash)
            .addValue("amount", record.getAmount())
            .addValue("currencyCode", record.getCurrencyCode())
            .addValue("sourceAccount", record.getSourceAccount())
            .addValue("destinationAccount", record.getDestinationAccount())
            .addValue("destinationBankCode", record.getDestinationBankCode())
            .addValue("terminalId", record.getTerminalId())
            .addValue("merchantId", record.getMerchantId())
            .addValue("acquiringBankCode", record.getAcquiringBankCode())
            .addValue("stan", record.getStan())
            .addValue("rrn", record.getRrn())
            .addValue("channel", record.getChannel())
            .addValue("status", record.getStatus() != null ? record.getStatus().name() : null)
            .addValue("responseCode", record.getResponseCode())
            .addValue("authorizationCode", record.getAuthorizationCode())
            .addValue("hostReferenceNumber", record.getHostReferenceNumber())
            .addValue("originalTransactionId", record.getOriginalTransactionId())
            .addValue("requestTime", JdbcUtils.toTimestamp(record.getRequestTime()))
            .addValue("transactionTime", JdbcUtils.toTimestamp(record.getTransactionTime()))
            .addValue("responseTime", JdbcUtils.toTimestamp(record.getResponseTime()))
            .addValue("processingTimeMs", record.getProcessingTimeMs())
            .addValue("errorDetails", record.getErrorDetails())
            .addValue("createdAt", JdbcUtils.toTimestamp(record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now()))
            .addValue("updatedAt", JdbcUtils.toTimestamp(record.getUpdatedAt() != null ? record.getUpdatedAt() : LocalDateTime.now()))
            .addValue("transactionDate", record.getTransactionDate());
    }

    /**
     * Encrypts PAN for storage.
     */
    private String encryptPan(String pan) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }
        try {
            return panEncryptionService.encrypt(pan);
        } catch (Exception e) {
            log.error("Failed to encrypt PAN: {}", e.getMessage());
            throw new RuntimeException("PAN encryption failed", e);
        }
    }

    @Override
    public Optional<TransactionRecord> findByTransactionId(String transactionId) {
        String sql = "SELECT * FROM FEP_TRANSACTION WHERE TRANSACTION_ID = :transactionId";
        try {
            TransactionRecord record = jdbcTemplate.queryForObject(sql, Map.of("transactionId", transactionId), rowMapper);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TransactionRecord> findByRrnAndStan(String rrn, String stan) {
        String sql = "SELECT * FROM FEP_TRANSACTION WHERE RRN = :rrn AND STAN = :stan ORDER BY CREATED_AT DESC FETCH FIRST 1 ROW ONLY";
        try {
            TransactionRecord record = jdbcTemplate.queryForObject(sql, Map.of("rrn", rrn, "stan", stan), rowMapper);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TransactionRecord> findByMaskedPanAndDateRange(String maskedPan, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT * FROM FEP_TRANSACTION
            WHERE MASKED_PAN = :maskedPan
              AND REQUEST_TIME >= :startDate
              AND REQUEST_TIME <= :endDate
            ORDER BY REQUEST_TIME DESC
            """;
        return jdbcTemplate.query(sql, Map.of(
            "maskedPan", maskedPan,
            "startDate", JdbcUtils.toTimestamp(startDate),
            "endDate", JdbcUtils.toTimestamp(endDate)
        ), rowMapper);
    }

    @Override
    public List<TransactionRecord> findByTerminalIdAndDateRange(String terminalId, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT * FROM FEP_TRANSACTION
            WHERE TERMINAL_ID = :terminalId
              AND REQUEST_TIME >= :startDate
              AND REQUEST_TIME <= :endDate
            ORDER BY REQUEST_TIME DESC
            """;
        return jdbcTemplate.query(sql, Map.of(
            "terminalId", terminalId,
            "startDate", JdbcUtils.toTimestamp(startDate),
            "endDate", JdbcUtils.toTimestamp(endDate)
        ), rowMapper);
    }

    @Override
    public List<TransactionRecord> findByStatus(TransactionStatus status) {
        String sql = "SELECT * FROM FEP_TRANSACTION WHERE STATUS = :status ORDER BY CREATED_AT DESC";
        return jdbcTemplate.query(sql, Map.of("status", status.name()), rowMapper);
    }

    @Override
    @Transactional
    public boolean updateStatus(String transactionId, TransactionStatus newStatus) {
        String sql = "UPDATE FEP_TRANSACTION SET STATUS = :status, UPDATED_AT = :updatedAt WHERE TRANSACTION_ID = :transactionId";
        int updated = jdbcTemplate.update(sql, Map.of(
            "transactionId", transactionId,
            "status", newStatus.name(),
            "updatedAt", JdbcUtils.toTimestamp(LocalDateTime.now())
        ));
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean updateResponse(String transactionId, String responseCode, String authorizationCode, TransactionStatus status) {
        String sql = """
            UPDATE FEP_TRANSACTION SET
                RESPONSE_CODE = :responseCode,
                AUTHORIZATION_CODE = :authorizationCode,
                STATUS = :status,
                RESPONSE_TIME = :responseTime,
                UPDATED_AT = :updatedAt
            WHERE TRANSACTION_ID = :transactionId
            """;
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(sql, Map.of(
            "transactionId", transactionId,
            "responseCode", responseCode,
            "authorizationCode", authorizationCode != null ? authorizationCode : "",
            "status", status.name(),
            "responseTime", JdbcUtils.toTimestamp(now),
            "updatedAt", JdbcUtils.toTimestamp(now)
        ));
        return updated > 0;
    }

    @Override
    public boolean existsByTransactionId(String transactionId) {
        String sql = "SELECT COUNT(1) FROM FEP_TRANSACTION WHERE TRANSACTION_ID = :transactionId";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("transactionId", transactionId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean isDuplicate(String rrn, String stan, String terminalId, int windowMinutes) {
        String sql = """
            SELECT COUNT(1) FROM FEP_TRANSACTION
            WHERE RRN = :rrn
              AND STAN = :stan
              AND TERMINAL_ID = :terminalId
              AND REQUEST_TIME >= :cutoff
              AND STATUS IN ('COMPLETED', 'APPROVED')
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Map.of(
            "rrn", rrn,
            "stan", stan,
            "terminalId", terminalId,
            "cutoff", JdbcUtils.toTimestamp(LocalDateTime.now().minusMinutes(windowMinutes))
        ), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public long countByStatusAndDate(TransactionStatus status, String transactionDate) {
        String sql = "SELECT COUNT(1) FROM FEP_TRANSACTION WHERE STATUS = :status AND TRANSACTION_DATE = :transactionDate";
        Long count = jdbcTemplate.queryForObject(sql, Map.of(
            "status", status.name(),
            "transactionDate", transactionDate
        ), Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<TransactionRecord> findByRrn(String rrn) {
        String sql = "SELECT * FROM FEP_TRANSACTION WHERE RRN = :rrn ORDER BY CREATED_AT DESC FETCH FIRST 1 ROW ONLY";
        try {
            TransactionRecord record = jdbcTemplate.queryForObject(sql, Map.of("rrn", rrn), rowMapper);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TransactionRecord> findByRrnStanTerminal(String rrn, String stan, String terminalId) {
        String sql = """
            SELECT * FROM FEP_TRANSACTION
            WHERE RRN = :rrn AND STAN = :stan AND TERMINAL_ID = :terminalId
            ORDER BY CREATED_AT DESC FETCH FIRST 1 ROW ONLY
            """;
        try {
            TransactionRecord record = jdbcTemplate.queryForObject(sql, Map.of(
                "rrn", rrn,
                "stan", stan,
                "terminalId", terminalId
            ), rowMapper);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TransactionRecord> findAll() {
        String sql = "SELECT * FROM FEP_TRANSACTION ORDER BY CREATED_AT DESC FETCH FIRST 1000 ROWS ONLY";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Optional<TransactionRecord> findOriginalForReversal(String originalTransactionId) {
        String sql = """
            SELECT * FROM FEP_TRANSACTION
            WHERE TRANSACTION_ID = :originalTransactionId
              AND STATUS IN ('COMPLETED', 'APPROVED')
            """;
        try {
            TransactionRecord record = jdbcTemplate.queryForObject(sql, Map.of("originalTransactionId", originalTransactionId), rowMapper);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public boolean markAsReversed(String transactionId, String reversalTransactionId) {
        String sql = """
            UPDATE FEP_TRANSACTION SET
                STATUS = 'REVERSED',
                ORIGINAL_TRANSACTION_ID = :reversalTransactionId,
                UPDATED_AT = :updatedAt
            WHERE TRANSACTION_ID = :transactionId
              AND STATUS IN ('COMPLETED', 'APPROVED')
            """;
        int updated = jdbcTemplate.update(sql, Map.of(
            "transactionId", transactionId,
            "reversalTransactionId", reversalTransactionId,
            "updatedAt", JdbcUtils.toTimestamp(LocalDateTime.now())
        ));
        return updated > 0;
    }
}
