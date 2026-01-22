package com.fep.transaction.db.service;

import com.fep.transaction.db.entity.FepTransactionEntity;
import com.fep.transaction.db.entity.FepTransactionEntity.ReversalStatus;
import com.fep.transaction.db.entity.FepTransactionEntity.TransactionStatus;
import com.fep.transaction.db.repository.FepTransactionRepository;
import com.fep.transaction.redis.dto.PendingTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pending Transaction DB 服務
 *
 * <p>純 DB 架構，取代 Redis 進行交易上下文管理。
 *
 * <p>提供與 PendingTransactionRedisService 相同的介面，
 * 使上層 Delegate 無需修改即可切換至 DB 方案。
 *
 * <p>效能注意事項：
 * <ul>
 *   <li>Timeout 掃描使用 FOR UPDATE SKIP LOCKED 避免重複處理</li>
 *   <li>確保 (STATUS, EXPIRE_TIME) 有複合索引</li>
 *   <li>建議分離 Scanner 專用連線池</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingTransactionDbService {

    private final FepTransactionRepository repository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== Save Operations ====================

    /**
     * 儲存 Pending 交易至 DB
     *
     * @param dto 交易資料
     */
    @Transactional
    public void savePendingTransaction(PendingTransactionDTO dto) {
        FepTransactionEntity entity = toEntity(dto);
        repository.save(entity);

        log.debug("Saved pending transaction to DB: txnId={}, stan={}",
                dto.getTransactionId(), dto.getStan());
    }

    /**
     * 直接儲存 Entity
     *
     * @param entity 交易實體
     * @return 儲存後的實體
     */
    @Transactional
    public FepTransactionEntity save(FepTransactionEntity entity) {
        return repository.save(entity);
    }

    // ==================== Load Operations ====================

    /**
     * 載入交易資料
     *
     * @param transactionId 交易 ID
     * @return 交易資料
     */
    @Transactional(readOnly = true)
    public Optional<PendingTransactionDTO> loadTransaction(String transactionId) {
        return repository.findByTransactionId(transactionId)
                .map(this::toDTO);
    }

    /**
     * 透過 STAN 載入交易資料 (狀態為 SENT)
     *
     * @param stan STAN
     * @return 交易資料
     */
    @Transactional(readOnly = true)
    public Optional<PendingTransactionDTO> loadTransactionByStan(String stan) {
        return repository.findByStanAndStatus(stan, TransactionStatus.SENT)
                .or(() -> repository.findFirstByStanOrderByCreatedAtDesc(stan))
                .map(this::toDTO);
    }

    /**
     * 透過 STAN 取得交易 ID
     *
     * @param stan STAN
     * @return 交易 ID
     */
    @Transactional(readOnly = true)
    public String getTransactionIdByStan(String stan) {
        return repository.findByStanAndStatus(stan, TransactionStatus.SENT)
                .or(() -> repository.findFirstByStanOrderByCreatedAtDesc(stan))
                .map(FepTransactionEntity::getTransactionId)
                .orElse(null);
    }

    /**
     * 載入交易 Entity
     *
     * @param transactionId 交易 ID
     * @return 交易實體
     */
    @Transactional(readOnly = true)
    public Optional<FepTransactionEntity> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    // ==================== Timeout Scanning ====================

    /**
     * 掃描超時交易
     *
     * <p>使用 FOR UPDATE SKIP LOCKED 避免多個 Scanner 重複處理
     *
     * @param limit 最大筆數
     * @return 超時交易 ID 列表
     */
    @Transactional
    public List<String> scanTimeoutTransactions(int limit) {
        LocalDateTime now = LocalDateTime.now();

        List<FepTransactionEntity> timeouts = repository.findTimeoutTransactionsForUpdate(
                TransactionStatus.SENT,
                now,
                PageRequest.of(0, limit)
        );

        if (timeouts.isEmpty()) {
            return List.of();
        }

        log.debug("Scanned timeout transactions from DB: count={}", timeouts.size());

        return timeouts.stream()
                .map(FepTransactionEntity::getTransactionId)
                .collect(Collectors.toList());
    }

    /**
     * 掃描超時交易 (返回 Entity，用於需要完整資料的場景)
     *
     * @param limit 最大筆數
     * @return 超時交易列表
     */
    @Transactional
    public List<FepTransactionEntity> scanTimeoutTransactionEntities(int limit) {
        LocalDateTime now = LocalDateTime.now();

        return repository.findTimeoutTransactionsForUpdate(
                TransactionStatus.SENT,
                now,
                PageRequest.of(0, limit)
        );
    }

    /**
     * 取得 Pending (SENT) 交易數量
     *
     * @return 數量
     */
    @Transactional(readOnly = true)
    public long getPendingCount() {
        return repository.countByStatus(TransactionStatus.SENT);
    }

    // ==================== Update Operations ====================

    /**
     * 更新交易資料
     *
     * @param dto 更新後的交易資料
     */
    @Transactional
    public void updateTransaction(PendingTransactionDTO dto) {
        repository.findByTransactionId(dto.getTransactionId())
                .ifPresent(entity -> {
                    updateEntityFromDTO(entity, dto);
                    repository.save(entity);
                    log.debug("Updated transaction: txnId={}, status={}",
                            dto.getTransactionId(), dto.getStatus());
                });
    }

    /**
     * 更新交易狀態
     *
     * @param transactionId 交易 ID
     * @param status 新狀態
     * @param responseCode 回應碼
     */
    @Transactional
    public void updateStatus(String transactionId, TransactionStatus status, String responseCode) {
        int updated = repository.updateStatus(
                transactionId,
                status,
                responseCode,
                LocalDateTime.now()
        );

        if (updated > 0) {
            log.debug("Updated status: txnId={}, status={}, rc={}",
                    transactionId, status, responseCode);
        } else {
            log.warn("Failed to update status, transaction not found: txnId={}", transactionId);
        }
    }

    /**
     * 更新狀態 (使用 DTO 狀態枚舉)
     *
     * @param transactionId 交易 ID
     * @param status DTO 狀態
     */
    @Transactional
    public void updateStatusAndSetTtl(String transactionId, PendingTransactionDTO.TransactionStatus status) {
        TransactionStatus entityStatus = convertStatus(status);
        updateStatus(transactionId, entityStatus, null);
    }

    /**
     * 更新為 SENT 狀態
     *
     * @param transactionId 交易 ID
     */
    @Transactional
    public void updateToSent(String transactionId) {
        int updated = repository.updateToSent(transactionId, LocalDateTime.now());
        if (updated > 0) {
            log.debug("Updated to SENT: txnId={}", transactionId);
        }
    }

    /**
     * 更新沖正狀態
     *
     * @param transactionId 交易 ID
     * @param reversalStatus 沖正狀態
     */
    @Transactional
    public void updateReversalStatus(String transactionId, ReversalStatus reversalStatus) {
        repository.updateReversalStatus(transactionId, reversalStatus);
        log.debug("Updated reversal status: txnId={}, reversalStatus={}",
                transactionId, reversalStatus);
    }

    /**
     * 更新原始回應電文
     *
     * @param transactionId 交易 ID
     * @param rawResponse 原始回應 (byte[])
     */
    @Transactional
    public void updateRawResponse(String transactionId, byte[] rawResponse) {
        String base64 = rawResponse != null ? Base64.getEncoder().encodeToString(rawResponse) : null;
        repository.updateRawResponse(transactionId, base64);
    }

    // ==================== Cleanup (No-op for DB) ====================

    /**
     * 清理交易資料 (DB 方案不需要，由資料保留政策處理)
     *
     * <p>此方法保留是為了與 Redis 介面相容，實際上不執行任何操作。
     *
     * @param transactionId 交易 ID
     * @param stan STAN
     */
    public void cleanup(String transactionId, String stan) {
        // DB 方案不需要顯式清理
        // 資料保留由 DB retention policy 或排程任務處理
        log.trace("Cleanup called (no-op for DB): txnId={}, stan={}", transactionId, stan);
    }

    /**
     * 從 Pending 移除 (更新狀態為 TIMEOUT 開始處理)
     *
     * @param transactionId 交易 ID
     */
    @Transactional
    public void removeFromPendingZset(String transactionId) {
        // DB 方案中，這個操作等同於將狀態從 SENT 改為 TIMEOUT
        // 但實際的狀態更新應該在 Timeout 流程中進行
        // 此方法主要是為了相容 Redis 介面
        log.trace("removeFromPendingZset called: txnId={}", transactionId);
    }

    // ==================== Utility Methods ====================

    /**
     * 檢查交易是否存在
     *
     * @param transactionId 交易 ID
     * @return true 如果存在
     */
    @Transactional(readOnly = true)
    public boolean exists(String transactionId) {
        return repository.existsByTransactionId(transactionId);
    }

    /**
     * 新增至 Pending (DB 方案中此操作為 no-op，因為 save 時已設定狀態)
     *
     * @param transactionId 交易 ID
     * @param expireTimeMs 過期時間
     */
    public void addToPendingZset(String transactionId, long expireTimeMs) {
        // DB 方案不需要額外操作，expireTime 在 save 時已設定
        log.trace("addToPendingZset called (no-op for DB): txnId={}", transactionId);
    }

    // ==================== Conversion Methods ====================

    /**
     * DTO 轉 Entity
     */
    private FepTransactionEntity toEntity(PendingTransactionDTO dto) {
        return FepTransactionEntity.builder()
                .transactionId(dto.getTransactionId())
                .stan(dto.getStan())
                .mti(dto.getMti())
                .processingCode(dto.getProcessingCode())
                .sourceAccount(dto.getSourceAccount())
                .targetAccount(dto.getTargetAccount())
                .amount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO)
                .sourceBankCode(dto.getSourceBankCode())
                .targetBankCode(dto.getTargetBankCode())
                .status(convertStatus(dto.getStatus()))
                .responseCode(dto.getResponseCode())
                .processId(dto.getProcessId())
                .channelId(dto.getChannelId())
                .callbackKey(dto.getCallbackKey())
                .requestTime(toLocalDateTime(dto.getCreatedAt()))
                .sentToFiscTime(toLocalDateTime(dto.getSentToFiscAt()))
                .fiscResponseTime(toLocalDateTime(dto.getFiscResponseAt()))
                .expireTime(toLocalDateTime(dto.getExpireAt()))
                .rawRequest(dto.getRawRequestBase64())
                .rawResponse(dto.getRawResponseBase64())
                .reversalStatus(convertReversalStatus(dto.getReversalStatus()))
                .transactionDate(dto.getTransactionDate())
                .createdAt(toLocalDateTime(dto.getCreatedAt()))
                .build();
    }

    /**
     * Entity 轉 DTO
     */
    private PendingTransactionDTO toDTO(FepTransactionEntity entity) {
        return PendingTransactionDTO.builder()
                .transactionId(entity.getTransactionId())
                .stan(entity.getStan())
                .mti(entity.getMti())
                .processingCode(entity.getProcessingCode())
                .sourceAccount(entity.getSourceAccount())
                .targetAccount(entity.getTargetAccount())
                .amount(entity.getAmount())
                .sourceBankCode(entity.getSourceBankCode())
                .targetBankCode(entity.getTargetBankCode())
                .status(convertStatus(entity.getStatus()))
                .responseCode(entity.getResponseCode())
                .processId(entity.getProcessId())
                .channelId(entity.getChannelId())
                .callbackKey(entity.getCallbackKey())
                .createdAt(toEpochMilli(entity.getCreatedAt()))
                .sentToFiscAt(toEpochMilli(entity.getSentToFiscTime()))
                .fiscResponseAt(toEpochMilli(entity.getFiscResponseTime()))
                .expireAt(toEpochMilli(entity.getExpireTime()))
                .rawRequestBase64(entity.getRawRequest())
                .rawResponseBase64(entity.getRawResponse())
                .reversalStatus(convertReversalStatus(entity.getReversalStatus()))
                .transactionDate(entity.getTransactionDate())
                .build();
    }

    /**
     * 更新 Entity
     */
    private void updateEntityFromDTO(FepTransactionEntity entity, PendingTransactionDTO dto) {
        entity.setStatus(convertStatus(dto.getStatus()));
        entity.setResponseCode(dto.getResponseCode());
        entity.setRawResponse(dto.getRawResponseBase64());
        entity.setFiscResponseTime(toLocalDateTime(dto.getFiscResponseAt()));
        entity.setReversalStatus(convertReversalStatus(dto.getReversalStatus()));
    }

    private TransactionStatus convertStatus(PendingTransactionDTO.TransactionStatus dtoStatus) {
        if (dtoStatus == null) return TransactionStatus.PENDING;
        return switch (dtoStatus) {
            case PENDING -> TransactionStatus.PENDING;
            case SENT -> TransactionStatus.SENT;
            case COMPLETED -> TransactionStatus.COMPLETED;
            case FAILED -> TransactionStatus.FAILED;
            case TIMEOUT -> TransactionStatus.TIMEOUT;
            case REVERSED -> TransactionStatus.REVERSED;
        };
    }

    private PendingTransactionDTO.TransactionStatus convertStatus(TransactionStatus entityStatus) {
        if (entityStatus == null) return PendingTransactionDTO.TransactionStatus.PENDING;
        return switch (entityStatus) {
            case PENDING -> PendingTransactionDTO.TransactionStatus.PENDING;
            case SENT -> PendingTransactionDTO.TransactionStatus.SENT;
            case COMPLETED -> PendingTransactionDTO.TransactionStatus.COMPLETED;
            case FAILED -> PendingTransactionDTO.TransactionStatus.FAILED;
            case TIMEOUT -> PendingTransactionDTO.TransactionStatus.TIMEOUT;
            case REVERSED -> PendingTransactionDTO.TransactionStatus.REVERSED;
        };
    }

    private ReversalStatus convertReversalStatus(PendingTransactionDTO.ReversalStatus dtoStatus) {
        if (dtoStatus == null) return ReversalStatus.NONE;
        return switch (dtoStatus) {
            case NONE -> ReversalStatus.NONE;
            case IN_PROGRESS -> ReversalStatus.IN_PROGRESS;
            case SUCCESS -> ReversalStatus.SUCCESS;
            case FAILED -> ReversalStatus.FAILED;
            case PENDING_ADJUSTMENT -> ReversalStatus.PENDING_ADJUSTMENT;
        };
    }

    private PendingTransactionDTO.ReversalStatus convertReversalStatus(ReversalStatus entityStatus) {
        if (entityStatus == null) return PendingTransactionDTO.ReversalStatus.NONE;
        return switch (entityStatus) {
            case NONE -> PendingTransactionDTO.ReversalStatus.NONE;
            case IN_PROGRESS -> PendingTransactionDTO.ReversalStatus.IN_PROGRESS;
            case SUCCESS -> PendingTransactionDTO.ReversalStatus.SUCCESS;
            case FAILED -> PendingTransactionDTO.ReversalStatus.FAILED;
            case PENDING_ADJUSTMENT -> PendingTransactionDTO.ReversalStatus.PENDING_ADJUSTMENT;
        };
    }

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
