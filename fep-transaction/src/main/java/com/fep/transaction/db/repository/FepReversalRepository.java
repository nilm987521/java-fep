package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepReversalEntity;
import com.fep.transaction.db.entity.FepReversalEntity.ReversalReason;
import com.fep.transaction.db.entity.FepReversalEntity.ReversalStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FEP 沖正記錄 Repository
 *
 * <p>提供沖正記錄的 CRUD 操作及重試掃描查詢。
 */
@Repository
public interface FepReversalRepository extends JpaRepository<FepReversalEntity, Long> {

    /**
     * 依沖正 ID 查詢
     *
     * @param reversalId 沖正 ID
     * @return 沖正記錄
     */
    Optional<FepReversalEntity> findByReversalId(String reversalId);

    /**
     * 依原始交易 ID 查詢
     *
     * @param originalTransactionId 原始交易 ID
     * @return 沖正記錄列表
     */
    List<FepReversalEntity> findByOriginalTransactionIdOrderByCreatedAtDesc(String originalTransactionId);

    /**
     * 依原始 STAN 查詢
     *
     * @param originalStan 原始 STAN
     * @return 沖正記錄列表
     */
    List<FepReversalEntity> findByOriginalStanOrderByCreatedAtDesc(String originalStan);

    /**
     * 依狀態查詢
     *
     * @param status 狀態
     * @param pageable 分頁
     * @return 沖正記錄頁面
     */
    Page<FepReversalEntity> findByStatus(ReversalStatus status, Pageable pageable);

    /**
     * 掃描待重試的沖正 (FOR UPDATE SKIP LOCKED)
     *
     * <p>查詢狀態為 PENDING 或 FAILED 且重試次數未達上限的沖正記錄。
     *
     * @param now 當前時間
     * @param pageable 分頁
     * @return 待重試沖正列表
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // SKIP LOCKED
    @Query("""
        SELECT r FROM FepReversalEntity r
        WHERE r.status IN ('PENDING', 'FAILED')
          AND r.retryCount < r.maxRetry
          AND (r.nextRetryTime IS NULL OR r.nextRetryTime <= :now)
        ORDER BY r.createdAt ASC
        """)
    List<FepReversalEntity> findPendingReversalsForUpdate(
            @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 查詢待人工處理的沖正
     *
     * @param pageable 分頁
     * @return 待人工處理沖正列表
     */
    Page<FepReversalEntity> findByStatusAndManualProcessing(
            ReversalStatus status, Boolean manualProcessing, Pageable pageable);

    /**
     * 依沖正原因和日期查詢
     *
     * @param reason 沖正原因
     * @param reversalDate 沖正日期
     * @return 沖正記錄列表
     */
    List<FepReversalEntity> findByReasonAndReversalDate(ReversalReason reason, String reversalDate);

    /**
     * 更新沖正狀態
     *
     * @param reversalId 沖正 ID
     * @param status 狀態
     * @param responseCode 回應碼
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepReversalEntity r
        SET r.status = :status,
            r.responseCode = :responseCode,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.reversalId = :reversalId
        """)
    int updateStatus(
            @Param("reversalId") String reversalId,
            @Param("status") ReversalStatus status,
            @Param("responseCode") String responseCode);

    /**
     * 更新重試資訊
     *
     * @param reversalId 沖正 ID
     * @param nextRetryTime 下次重試時間
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepReversalEntity r
        SET r.retryCount = r.retryCount + 1,
            r.nextRetryTime = :nextRetryTime,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.reversalId = :reversalId
        """)
    int incrementRetryCount(
            @Param("reversalId") String reversalId,
            @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 標記為待人工處理
     *
     * @param reversalId 沖正 ID
     * @param remark 備註
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepReversalEntity r
        SET r.status = 'PENDING_MANUAL',
            r.manualProcessing = true,
            r.remark = :remark,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.reversalId = :reversalId
        """)
    int markAsPendingManual(
            @Param("reversalId") String reversalId,
            @Param("remark") String remark);

    /**
     * 統計各狀態的沖正數量
     *
     * @param reversalDate 沖正日期
     * @return 狀態統計
     */
    @Query("""
        SELECT r.status, COUNT(r)
        FROM FepReversalEntity r
        WHERE r.reversalDate = :reversalDate
        GROUP BY r.status
        """)
    List<Object[]> countByStatusAndDate(@Param("reversalDate") String reversalDate);

    /**
     * 檢查原始交易是否已有沖正記錄
     *
     * @param originalTransactionId 原始交易 ID
     * @return true 如果存在
     */
    boolean existsByOriginalTransactionId(String originalTransactionId);
}
