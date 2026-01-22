package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepCardBlacklistEntity;
import com.fep.transaction.db.entity.FepCardBlacklistEntity.BlacklistStatus;
import com.fep.transaction.db.entity.FepCardBlacklistEntity.BlacklistType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FEP 卡片黑名單 Repository
 *
 * <p>提供卡片黑名單的 CRUD 操作及檢查功能。
 */
@Repository
public interface FepCardBlacklistRepository extends JpaRepository<FepCardBlacklistEntity, Long> {

    /**
     * 依卡號查詢生效中的黑名單
     *
     * @param cardNumber 卡號
     * @param now 當前時間
     * @return 黑名單列表
     */
    @Query("""
        SELECT c FROM FepCardBlacklistEntity c
        WHERE c.cardNumber = :cardNumber
          AND c.status = 'ACTIVE'
          AND c.effectiveDate <= :now
          AND (c.expiryDate IS NULL OR c.expiryDate > :now)
        """)
    List<FepCardBlacklistEntity> findActiveByCardNumber(
            @Param("cardNumber") String cardNumber,
            @Param("now") LocalDateTime now);

    /**
     * 檢查卡號是否在黑名單中
     *
     * @param cardNumber 卡號
     * @param now 當前時間
     * @return true 如果在黑名單中
     */
    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM FepCardBlacklistEntity c
        WHERE c.cardNumber = :cardNumber
          AND c.status = 'ACTIVE'
          AND c.effectiveDate <= :now
          AND (c.expiryDate IS NULL OR c.expiryDate > :now)
        """)
    boolean isBlacklisted(
            @Param("cardNumber") String cardNumber,
            @Param("now") LocalDateTime now);

    /**
     * 依黑名單類型查詢
     *
     * @param blacklistType 黑名單類型
     * @param status 狀態
     * @param pageable 分頁
     * @return 黑名單頁面
     */
    Page<FepCardBlacklistEntity> findByBlacklistTypeAndStatus(
            BlacklistType blacklistType, BlacklistStatus status, Pageable pageable);

    /**
     * 依發卡銀行查詢
     *
     * @param issuerBankCode 發卡銀行代碼
     * @param status 狀態
     * @return 黑名單列表
     */
    List<FepCardBlacklistEntity> findByIssuerBankCodeAndStatus(
            String issuerBankCode, BlacklistStatus status);

    /**
     * 依財金通報序號查詢
     *
     * @param fiscNotificationNo 財金通報序號
     * @return 黑名單
     */
    Optional<FepCardBlacklistEntity> findByFiscNotificationNo(String fiscNotificationNo);

    /**
     * 更新黑名單狀態
     *
     * @param cardNumber 卡號
     * @param status 狀態
     * @param updatedBy 更新人員
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepCardBlacklistEntity c
        SET c.status = :status,
            c.updatedBy = :updatedBy,
            c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.cardNumber = :cardNumber
          AND c.status = 'ACTIVE'
        """)
    int updateStatus(
            @Param("cardNumber") String cardNumber,
            @Param("status") BlacklistStatus status,
            @Param("updatedBy") String updatedBy);

    /**
     * 設定失效日期
     *
     * @param cardNumber 卡號
     * @param expiryDate 失效日期
     * @param updatedBy 更新人員
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepCardBlacklistEntity c
        SET c.expiryDate = :expiryDate,
            c.updatedBy = :updatedBy,
            c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.cardNumber = :cardNumber
          AND c.status = 'ACTIVE'
        """)
    int setExpiryDate(
            @Param("cardNumber") String cardNumber,
            @Param("expiryDate") LocalDateTime expiryDate,
            @Param("updatedBy") String updatedBy);

    /**
     * 查詢已過期但狀態仍為 ACTIVE 的記錄 (定期清理用)
     *
     * @param now 當前時間
     * @return 過期記錄列表
     */
    @Query("""
        SELECT c FROM FepCardBlacklistEntity c
        WHERE c.status = 'ACTIVE'
          AND c.expiryDate IS NOT NULL
          AND c.expiryDate < :now
        """)
    List<FepCardBlacklistEntity> findExpiredActiveRecords(@Param("now") LocalDateTime now);

    /**
     * 批次更新過期記錄狀態
     *
     * @param now 當前時間
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepCardBlacklistEntity c
        SET c.status = 'INACTIVE',
            c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.status = 'ACTIVE'
          AND c.expiryDate IS NOT NULL
          AND c.expiryDate < :now
        """)
    int updateExpiredToInactive(@Param("now") LocalDateTime now);

    /**
     * 統計各類型黑名單數量
     *
     * @return 類型統計
     */
    @Query("""
        SELECT c.blacklistType, COUNT(c)
        FROM FepCardBlacklistEntity c
        WHERE c.status = 'ACTIVE'
        GROUP BY c.blacklistType
        """)
    List<Object[]> countByType();

    /**
     * 依來源和狀態查詢
     *
     * @param source 來源
     * @param status 狀態
     * @param pageable 分頁
     * @return 黑名單頁面
     */
    Page<FepCardBlacklistEntity> findBySourceAndStatus(
            String source, BlacklistStatus status, Pageable pageable);
}
