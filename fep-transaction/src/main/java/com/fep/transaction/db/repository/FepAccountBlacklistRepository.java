package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepAccountBlacklistEntity;
import com.fep.transaction.db.entity.FepAccountBlacklistEntity.AccountBlacklistType;
import com.fep.transaction.db.entity.FepAccountBlacklistEntity.BlacklistStatus;
import com.fep.transaction.db.entity.FepAccountBlacklistEntity.RestrictionDirection;
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
 * FEP 帳號黑名單 Repository
 *
 * <p>提供帳號黑名單的 CRUD 操作及檢查功能。
 */
@Repository
public interface FepAccountBlacklistRepository extends JpaRepository<FepAccountBlacklistEntity, Long> {

    /**
     * 依帳號和銀行代碼查詢生效中的黑名單
     *
     * @param accountNumber 帳號
     * @param bankCode 銀行代碼
     * @param now 當前時間
     * @return 黑名單列表
     */
    @Query("""
        SELECT a FROM FepAccountBlacklistEntity a
        WHERE a.accountNumber = :accountNumber
          AND a.bankCode = :bankCode
          AND a.status = 'ACTIVE'
          AND a.effectiveDate <= :now
          AND (a.expiryDate IS NULL OR a.expiryDate > :now)
        """)
    List<FepAccountBlacklistEntity> findActiveByAccountAndBank(
            @Param("accountNumber") String accountNumber,
            @Param("bankCode") String bankCode,
            @Param("now") LocalDateTime now);

    /**
     * 檢查帳號是否在黑名單中 (指定方向)
     *
     * @param accountNumber 帳號
     * @param bankCode 銀行代碼
     * @param direction 限制方向
     * @param now 當前時間
     * @return true 如果在黑名單中
     */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM FepAccountBlacklistEntity a
        WHERE a.accountNumber = :accountNumber
          AND a.bankCode = :bankCode
          AND a.status = 'ACTIVE'
          AND a.effectiveDate <= :now
          AND (a.expiryDate IS NULL OR a.expiryDate > :now)
          AND (a.restrictionDirection = 'BOTH' OR a.restrictionDirection = :direction)
        """)
    boolean isBlacklisted(
            @Param("accountNumber") String accountNumber,
            @Param("bankCode") String bankCode,
            @Param("direction") RestrictionDirection direction,
            @Param("now") LocalDateTime now);

    /**
     * 檢查帳號是否為警示帳戶
     *
     * @param accountNumber 帳號
     * @param bankCode 銀行代碼
     * @param now 當前時間
     * @return true 如果是警示帳戶
     */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM FepAccountBlacklistEntity a
        WHERE a.accountNumber = :accountNumber
          AND a.bankCode = :bankCode
          AND a.status = 'ACTIVE'
          AND a.blacklistType IN ('ALERT_ACCOUNT', 'DERIVED_ALERT')
          AND a.effectiveDate <= :now
          AND (a.expiryDate IS NULL OR a.expiryDate > :now)
        """)
    boolean isAlertAccount(
            @Param("accountNumber") String accountNumber,
            @Param("bankCode") String bankCode,
            @Param("now") LocalDateTime now);

    /**
     * 依黑名單類型查詢
     *
     * @param blacklistType 黑名單類型
     * @param status 狀態
     * @param pageable 分頁
     * @return 黑名單頁面
     */
    Page<FepAccountBlacklistEntity> findByBlacklistTypeAndStatus(
            AccountBlacklistType blacklistType, BlacklistStatus status, Pageable pageable);

    /**
     * 依銀行代碼查詢
     *
     * @param bankCode 銀行代碼
     * @param status 狀態
     * @param pageable 分頁
     * @return 黑名單頁面
     */
    Page<FepAccountBlacklistEntity> findByBankCodeAndStatus(
            String bankCode, BlacklistStatus status, Pageable pageable);

    /**
     * 依通報文號查詢
     *
     * @param notificationNo 通報文號
     * @return 黑名單
     */
    Optional<FepAccountBlacklistEntity> findByNotificationNo(String notificationNo);

    /**
     * 依身分證字號查詢
     *
     * @param idNumber 身分證字號
     * @param status 狀態
     * @return 黑名單列表
     */
    List<FepAccountBlacklistEntity> findByIdNumberAndStatus(
            String idNumber, BlacklistStatus status);

    /**
     * 更新黑名單狀態
     *
     * @param accountNumber 帳號
     * @param bankCode 銀行代碼
     * @param status 狀態
     * @param updatedBy 更新人員
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepAccountBlacklistEntity a
        SET a.status = :status,
            a.updatedBy = :updatedBy,
            a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.accountNumber = :accountNumber
          AND a.bankCode = :bankCode
          AND a.status = 'ACTIVE'
        """)
    int updateStatus(
            @Param("accountNumber") String accountNumber,
            @Param("bankCode") String bankCode,
            @Param("status") BlacklistStatus status,
            @Param("updatedBy") String updatedBy);

    /**
     * 設定失效日期
     *
     * @param accountNumber 帳號
     * @param bankCode 銀行代碼
     * @param expiryDate 失效日期
     * @param updatedBy 更新人員
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepAccountBlacklistEntity a
        SET a.expiryDate = :expiryDate,
            a.updatedBy = :updatedBy,
            a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.accountNumber = :accountNumber
          AND a.bankCode = :bankCode
          AND a.status = 'ACTIVE'
        """)
    int setExpiryDate(
            @Param("accountNumber") String accountNumber,
            @Param("bankCode") String bankCode,
            @Param("expiryDate") LocalDateTime expiryDate,
            @Param("updatedBy") String updatedBy);

    /**
     * 查詢已過期但狀態仍為 ACTIVE 的記錄
     *
     * @param now 當前時間
     * @return 過期記錄列表
     */
    @Query("""
        SELECT a FROM FepAccountBlacklistEntity a
        WHERE a.status = 'ACTIVE'
          AND a.expiryDate IS NOT NULL
          AND a.expiryDate < :now
        """)
    List<FepAccountBlacklistEntity> findExpiredActiveRecords(@Param("now") LocalDateTime now);

    /**
     * 批次更新過期記錄狀態
     *
     * @param now 當前時間
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepAccountBlacklistEntity a
        SET a.status = 'INACTIVE',
            a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.status = 'ACTIVE'
          AND a.expiryDate IS NOT NULL
          AND a.expiryDate < :now
        """)
    int updateExpiredToInactive(@Param("now") LocalDateTime now);

    /**
     * 統計各類型黑名單數量
     *
     * @return 類型統計
     */
    @Query("""
        SELECT a.blacklistType, COUNT(a)
        FROM FepAccountBlacklistEntity a
        WHERE a.status = 'ACTIVE'
        GROUP BY a.blacklistType
        """)
    List<Object[]> countByType();

    /**
     * 統計各銀行黑名單數量
     *
     * @return 銀行統計
     */
    @Query("""
        SELECT a.bankCode, COUNT(a)
        FROM FepAccountBlacklistEntity a
        WHERE a.status = 'ACTIVE'
        GROUP BY a.bankCode
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countByBank();
}
