package com.fep.transaction.db.repository;

import com.fep.transaction.db.entity.FepTransactionLogEntity;
import com.fep.transaction.db.entity.FepTransactionLogEntity.LogLevel;
import com.fep.transaction.db.entity.FepTransactionLogEntity.LogStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FEP 交易日誌 Repository
 *
 * <p>提供交易日誌的查詢功能，用於稽核追蹤和問題分析。
 */
@Repository
public interface FepTransactionLogRepository extends JpaRepository<FepTransactionLogEntity, Long> {

    /**
     * 依交易 ID 查詢所有日誌
     *
     * @param transactionId 交易 ID
     * @return 日誌列表 (依時間排序)
     */
    List<FepTransactionLogEntity> findByTransactionIdOrderByLogTimeAsc(String transactionId);

    /**
     * 依 STAN 查詢所有日誌
     *
     * @param stan STAN
     * @return 日誌列表
     */
    List<FepTransactionLogEntity> findByStanOrderByLogTimeAsc(String stan);

    /**
     * 依日期範圍查詢
     *
     * @param startTime 開始時間
     * @param endTime 結束時間
     * @param pageable 分頁
     * @return 日誌頁面
     */
    Page<FepTransactionLogEntity> findByLogTimeBetween(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 依日誌等級查詢
     *
     * @param logLevel 日誌等級
     * @param logDate 日誌日期 (yyyyMMdd)
     * @param pageable 分頁
     * @return 日誌頁面
     */
    Page<FepTransactionLogEntity> findByLogLevelAndLogDate(
            LogLevel logLevel, String logDate, Pageable pageable);

    /**
     * 依日誌階段查詢
     *
     * @param logStage 日誌階段
     * @param logDate 日誌日期 (yyyyMMdd)
     * @return 日誌列表
     */
    List<FepTransactionLogEntity> findByLogStageAndLogDate(LogStage logStage, String logDate);

    /**
     * 查詢錯誤日誌
     *
     * @param startTime 開始時間
     * @param endTime 結束時間
     * @param pageable 分頁
     * @return 錯誤日誌頁面
     */
    @Query("""
        SELECT l FROM FepTransactionLogEntity l
        WHERE l.logLevel = 'ERROR'
          AND l.logTime BETWEEN :startTime AND :endTime
        ORDER BY l.logTime DESC
        """)
    Page<FepTransactionLogEntity> findErrorLogs(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 依元件名稱查詢
     *
     * @param component 元件名稱
     * @param logDate 日誌日期
     * @return 日誌列表
     */
    List<FepTransactionLogEntity> findByComponentAndLogDate(String component, String logDate);

    /**
     * 統計各階段的處理時間
     *
     * @param transactionId 交易 ID
     * @return 各階段處理時間
     */
    @Query("""
        SELECT l.logStage, AVG(l.durationMs)
        FROM FepTransactionLogEntity l
        WHERE l.transactionId = :transactionId
          AND l.durationMs IS NOT NULL
        GROUP BY l.logStage
        """)
    List<Object[]> getStageProcessingTimes(@Param("transactionId") String transactionId);

    /**
     * 計算指定日期的錯誤數量
     *
     * @param logDate 日誌日期
     * @return 錯誤數量
     */
    long countByLogLevelAndLogDate(LogLevel logLevel, String logDate);

    /**
     * 刪除指定日期之前的日誌 (日誌清理用)
     *
     * @param logDate 日誌日期
     * @return 刪除筆數
     */
    long deleteByLogDateLessThan(String logDate);
}
