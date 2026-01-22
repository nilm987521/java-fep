package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepDailyLimitEntity;
import com.fep.transaction.db.entity.FepDailyLimitEntity.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * FEP 日累計限額 Repository
 *
 * <p>提供日累計限額的 CRUD 操作及限額檢查。
 */
@Repository
public interface FepDailyLimitRepository extends JpaRepository<FepDailyLimitEntity, Long> {

    /**
     * 依帳號、交易類型、交易日期查詢 (加鎖)
     *
     * @param accountNumber 帳號
     * @param transactionType 交易類型
     * @param transactionDate 交易日期
     * @return 日累計限額
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FepDailyLimitEntity> findByAccountNumberAndTransactionTypeAndTransactionDate(
            String accountNumber, TransactionType transactionType, String transactionDate);

    /**
     * 依帳號和交易日期查詢所有類型
     *
     * @param accountNumber 帳號
     * @param transactionDate 交易日期
     * @return 日累計限額列表
     */
    List<FepDailyLimitEntity> findByAccountNumberAndTransactionDate(
            String accountNumber, String transactionDate);

    /**
     * 依卡號和交易日期查詢
     *
     * @param cardNumber 卡號
     * @param transactionDate 交易日期
     * @return 日累計限額列表
     */
    List<FepDailyLimitEntity> findByCardNumberAndTransactionDate(
            String cardNumber, String transactionDate);

    /**
     * 累加交易金額和次數
     *
     * @param accountNumber 帳號
     * @param transactionType 交易類型
     * @param transactionDate 交易日期
     * @param amount 交易金額
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepDailyLimitEntity d
        SET d.dailyAmount = d.dailyAmount + :amount,
            d.dailyCount = d.dailyCount + 1,
            d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.accountNumber = :accountNumber
          AND d.transactionType = :transactionType
          AND d.transactionDate = :transactionDate
        """)
    int accumulate(
            @Param("accountNumber") String accountNumber,
            @Param("transactionType") TransactionType transactionType,
            @Param("transactionDate") String transactionDate,
            @Param("amount") BigDecimal amount);

    /**
     * 扣減交易金額和次數 (沖正時使用)
     *
     * @param accountNumber 帳號
     * @param transactionType 交易類型
     * @param transactionDate 交易日期
     * @param amount 交易金額
     * @return 更新筆數
     */
    @Modifying
    @Query("""
        UPDATE FepDailyLimitEntity d
        SET d.dailyAmount = CASE WHEN d.dailyAmount - :amount < 0 THEN 0 ELSE d.dailyAmount - :amount END,
            d.dailyCount = CASE WHEN d.dailyCount - 1 < 0 THEN 0 ELSE d.dailyCount - 1 END,
            d.updatedAt = CURRENT_TIMESTAMP
        WHERE d.accountNumber = :accountNumber
          AND d.transactionType = :transactionType
          AND d.transactionDate = :transactionDate
        """)
    int deduct(
            @Param("accountNumber") String accountNumber,
            @Param("transactionType") TransactionType transactionType,
            @Param("transactionDate") String transactionDate,
            @Param("amount") BigDecimal amount);

    /**
     * 檢查是否已達限額
     *
     * @param accountNumber 帳號
     * @param transactionType 交易類型
     * @param transactionDate 交易日期
     * @param additionalAmount 本次交易金額
     * @return true 如果將超過限額
     */
    @Query("""
        SELECT CASE WHEN d.dailyAmount + :additionalAmount > d.dailyLimitAmount THEN true ELSE false END
        FROM FepDailyLimitEntity d
        WHERE d.accountNumber = :accountNumber
          AND d.transactionType = :transactionType
          AND d.transactionDate = :transactionDate
        """)
    Optional<Boolean> isAmountLimitExceeded(
            @Param("accountNumber") String accountNumber,
            @Param("transactionType") TransactionType transactionType,
            @Param("transactionDate") String transactionDate,
            @Param("additionalAmount") BigDecimal additionalAmount);

    /**
     * 檢查是否已達次數限額
     *
     * @param accountNumber 帳號
     * @param transactionType 交易類型
     * @param transactionDate 交易日期
     * @return true 如果已達次數限額
     */
    @Query("""
        SELECT CASE WHEN d.dailyCount >= d.dailyLimitCount THEN true ELSE false END
        FROM FepDailyLimitEntity d
        WHERE d.accountNumber = :accountNumber
          AND d.transactionType = :transactionType
          AND d.transactionDate = :transactionDate
        """)
    Optional<Boolean> isCountLimitExceeded(
            @Param("accountNumber") String accountNumber,
            @Param("transactionType") TransactionType transactionType,
            @Param("transactionDate") String transactionDate);

    /**
     * 刪除指定日期之前的資料 (日結清理用)
     *
     * @param transactionDate 交易日期
     * @return 刪除筆數
     */
    long deleteByTransactionDateLessThan(String transactionDate);

    /**
     * 查詢指定日期的所有限額記錄 (日結報表用)
     *
     * @param transactionDate 交易日期
     * @return 限額記錄列表
     */
    List<FepDailyLimitEntity> findByTransactionDate(String transactionDate);
}
