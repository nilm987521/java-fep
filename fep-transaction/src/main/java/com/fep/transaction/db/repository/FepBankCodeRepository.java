package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepBankCodeEntity;
import com.fep.transaction.db.entity.FepBankCodeEntity.BankStatus;
import com.fep.transaction.db.entity.FepBankCodeEntity.InstitutionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FEP 銀行代碼 Repository
 *
 * <p>提供銀行代碼的查詢功能。
 */
@Repository
public interface FepBankCodeRepository extends JpaRepository<FepBankCodeEntity, Long> {

    /**
     * 依銀行代碼查詢
     *
     * @param bankCode 銀行代碼
     * @return 銀行資訊
     */
    Optional<FepBankCodeEntity> findByBankCode(String bankCode);

    /**
     * 依總行代碼查詢所有分行
     *
     * @param headOfficeCode 總行代碼
     * @return 分行列表
     */
    List<FepBankCodeEntity> findByHeadOfficeCode(String headOfficeCode);

    /**
     * 依 SWIFT 代碼查詢
     *
     * @param swiftCode SWIFT 代碼
     * @return 銀行資訊
     */
    Optional<FepBankCodeEntity> findBySwiftCode(String swiftCode);

    /**
     * 依機構類型查詢
     *
     * @param institutionType 機構類型
     * @param status 狀態
     * @return 銀行列表
     */
    List<FepBankCodeEntity> findByInstitutionTypeAndStatus(
            InstitutionType institutionType, BankStatus status);

    /**
     * 依狀態查詢
     *
     * @param status 狀態
     * @param pageable 分頁
     * @return 銀行頁面
     */
    Page<FepBankCodeEntity> findByStatus(BankStatus status, Pageable pageable);

    /**
     * 查詢所有有效銀行
     *
     * @param now 當前時間
     * @return 有效銀行列表
     */
    @Query("""
        SELECT b FROM FepBankCodeEntity b
        WHERE b.status = 'ACTIVE'
          AND b.effectiveDate <= :now
          AND (b.expiryDate IS NULL OR b.expiryDate > :now)
        ORDER BY b.bankCode
        """)
    List<FepBankCodeEntity> findAllActive(@Param("now") LocalDateTime now);

    /**
     * 檢查銀行是否有效
     *
     * @param bankCode 銀行代碼
     * @param now 當前時間
     * @return true 如果有效
     */
    @Query("""
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
        FROM FepBankCodeEntity b
        WHERE b.bankCode = :bankCode
          AND b.status = 'ACTIVE'
          AND b.effectiveDate <= :now
          AND (b.expiryDate IS NULL OR b.expiryDate > :now)
        """)
    boolean isActive(
            @Param("bankCode") String bankCode,
            @Param("now") LocalDateTime now);

    /**
     * 查詢支援跨行轉帳的銀行
     *
     * @param now 當前時間
     * @return 銀行列表
     */
    @Query("""
        SELECT b FROM FepBankCodeEntity b
        WHERE b.supportTransfer = true
          AND b.status = 'ACTIVE'
          AND b.effectiveDate <= :now
          AND (b.expiryDate IS NULL OR b.expiryDate > :now)
        ORDER BY b.bankCode
        """)
    List<FepBankCodeEntity> findAllSupportTransfer(@Param("now") LocalDateTime now);

    /**
     * 查詢支援跨行提款的銀行
     *
     * @param now 當前時間
     * @return 銀行列表
     */
    @Query("""
        SELECT b FROM FepBankCodeEntity b
        WHERE b.supportWithdrawal = true
          AND b.status = 'ACTIVE'
          AND b.effectiveDate <= :now
          AND (b.expiryDate IS NULL OR b.expiryDate > :now)
        ORDER BY b.bankCode
        """)
    List<FepBankCodeEntity> findAllSupportWithdrawal(@Param("now") LocalDateTime now);

    /**
     * 查詢支援台灣 Pay 的銀行
     *
     * @param now 當前時間
     * @return 銀行列表
     */
    @Query("""
        SELECT b FROM FepBankCodeEntity b
        WHERE b.supportTaiwanPay = true
          AND b.status = 'ACTIVE'
          AND b.effectiveDate <= :now
          AND (b.expiryDate IS NULL OR b.expiryDate > :now)
        ORDER BY b.bankCode
        """)
    List<FepBankCodeEntity> findAllSupportTaiwanPay(@Param("now") LocalDateTime now);

    /**
     * 依銀行名稱模糊查詢
     *
     * @param keyword 關鍵字
     * @param status 狀態
     * @param pageable 分頁
     * @return 銀行頁面
     */
    @Query("""
        SELECT b FROM FepBankCodeEntity b
        WHERE (b.bankNameZh LIKE %:keyword%
           OR b.bankNameEn LIKE %:keyword%
           OR b.shortName LIKE %:keyword%)
          AND b.status = :status
        """)
    Page<FepBankCodeEntity> findByNameContaining(
            @Param("keyword") String keyword,
            @Param("status") BankStatus status,
            Pageable pageable);

    /**
     * 統計各機構類型數量
     *
     * @return 機構類型統計
     */
    @Query("""
        SELECT b.institutionType, COUNT(b)
        FROM FepBankCodeEntity b
        WHERE b.status = 'ACTIVE'
        GROUP BY b.institutionType
        """)
    List<Object[]> countByInstitutionType();

    /**
     * 檢查銀行代碼是否存在
     *
     * @param bankCode 銀行代碼
     * @return true 如果存在
     */
    boolean existsByBankCode(String bankCode);
}
