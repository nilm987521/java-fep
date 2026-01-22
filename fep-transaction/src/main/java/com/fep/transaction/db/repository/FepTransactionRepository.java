package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepTransactionEntity;
import com.fep.transaction.db.entity.FepTransactionEntity.TransactionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
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
 * FEP 交易 Repository
 *
 * <p>提供交易資料的 CRUD 操作及 Timeout 掃描查詢。
 *
 * <p>Timeout 掃描使用 PESSIMISTIC_WRITE + SKIP LOCKED 避免重複處理：
 * <ul>
 *   <li>PESSIMISTIC_WRITE: 取得排他鎖</li>
 *   <li>SKIP LOCKED: 跳過已被其他 Scanner 鎖定的記錄</li>
 * </ul>
 */
@Repository
public interface FepTransactionRepository extends JpaRepository<FepTransactionEntity, Long> {

    /**
     * 依交易 ID 查詢
     *
     * @param transactionId 交易 ID
     * @return 交易實體
     */
    Optional<FepTransactionEntity> findByTransactionId(String transactionId);

    /**
     * 依 STAN 查詢 (取最新一筆)
     *
     * @param stan STAN
     * @return 交易實體
     */
    Optional<FepTransactionEntity> findFirstByStanOrderByCreatedAtDesc(String stan);

    /**
     * 依 STAN 和狀態查詢
     *
     * @param stan STAN
     * @param status 狀態
     * @return 交易實體
     */
    Optional<FepTransactionEntity> findByStanAndStatus(String stan, TransactionStatus status);

    /**
     * 掃描超時交易 (FOR UPDATE SKIP LOCKED)
     *
     * <p>用於 TimeoutScanner，查詢狀態為 SENT 且已超時的交易。
     * 使用悲觀鎖 + SKIP LOCKED 避免多個 Scanner 重複處理。
     *
     * @param status 狀態 (應傳入 SENT)
     * @param expireTime 過期時間 (應傳入 now)
     * @param limit 最大筆數
     * @return 超時交易列表
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // SKIP LOCKED
    @Query("""
        SELECT t FROM FepTransactionEntity t
        WHERE t.status = :status
          AND t.expireTime <= :expireTime
        ORDER BY t.expireTime ASC
        """)
    List<FepTransactionEntity> findTimeoutTransactionsForUpdate(
            @Param("status") TransactionStatus status,
            @Param("expireTime") LocalDateTime expireTime,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 掃描超時交易 (不加鎖，僅查詢)
     *
     * @param status 狀態
     * @param expireTime 過期時間
     * @param limit 最大筆數
     * @return 超時交易列表
     */
    @Query("""
        SELECT t FROM FepTransactionEntity t
        WHERE t.status = :status
          AND t.expireTime <= :expireTime
        ORDER BY t.expireTime ASC
        """)
    List<FepTransactionEntity> findTimeoutTransactions(
            @Param("status") TransactionStatus status,
            @Param("expireTime") LocalDateTime expireTime,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 計算指定狀態的交易數量
     *
     * @param status 狀態
     * @return 數量
     */
    long countByStatus(TransactionStatus status);

    /**
     * 更新交易狀態
     *
     * @param transactionId 交易 ID
     * @param newStatus 新狀態
     * @param responseCode 回應碼
     * @param fiscResponseTime FISC 回應時間
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepTransactionEntity t
        SET t.status = :newStatus,
            t.responseCode = :responseCode,
            t.fiscResponseTime = :fiscResponseTime,
            t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.transactionId = :transactionId
        """)
    int updateStatus(
            @Param("transactionId") String transactionId,
            @Param("newStatus") TransactionStatus newStatus,
            @Param("responseCode") String responseCode,
            @Param("fiscResponseTime") LocalDateTime fiscResponseTime);

    /**
     * 更新為 SENT 狀態 (發送至 FISC 後)
     *
     * @param transactionId 交易 ID
     * @param sentTime 發送時間
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepTransactionEntity t
        SET t.status = 'SENT',
            t.sentToFiscTime = :sentTime,
            t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.transactionId = :transactionId
          AND t.status = 'PENDING'
        """)
    int updateToSent(
            @Param("transactionId") String transactionId,
            @Param("sentTime") LocalDateTime sentTime);

    /**
     * 更新沖正狀態
     *
     * @param transactionId 交易 ID
     * @param reversalStatus 沖正狀態
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepTransactionEntity t
        SET t.reversalStatus = :reversalStatus,
            t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.transactionId = :transactionId
        """)
    int updateReversalStatus(
            @Param("transactionId") String transactionId,
            @Param("reversalStatus") FepTransactionEntity.ReversalStatus reversalStatus);

    /**
     * 更新原始回應電文
     *
     * @param transactionId 交易 ID
     * @param rawResponse 原始回應 (Base64)
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepTransactionEntity t
        SET t.rawResponse = :rawResponse,
            t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.transactionId = :transactionId
        """)
    int updateRawResponse(
            @Param("transactionId") String transactionId,
            @Param("rawResponse") String rawResponse);

    /**
     * 查詢指定日期範圍的交易
     *
     * @param startDate 開始日期
     * @param endDate 結束日期
     * @return 交易列表
     */
    List<FepTransactionEntity> findByTransactionDateBetween(String startDate, String endDate);

    /**
     * 依通道和狀態查詢
     *
     * @param channelId 通道 ID
     * @param status 狀態
     * @return 交易列表
     */
    List<FepTransactionEntity> findByChannelIdAndStatus(String channelId, TransactionStatus status);

    /**
     * 檢查交易是否存在
     *
     * @param transactionId 交易 ID
     * @return true 如果存在
     */
    boolean existsByTransactionId(String transactionId);
}
