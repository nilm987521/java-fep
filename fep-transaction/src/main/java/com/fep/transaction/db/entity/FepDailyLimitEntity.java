package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FEP 日累計限額 Entity
 *
 * <p>記錄每個帳戶/卡片的日累計交易金額和次數，
 * 用於即時限額控管。每日結算後重置。
 *
 * <p>唯一鍵: (accountNumber, transactionType, transactionDate)
 */
@Entity
@Table(name = "FEP_DAILY_LIMIT", indexes = {
    @Index(name = "IDX_FEP_DAILY_LIMIT_ACCT", columnList = "accountNumber"),
    @Index(name = "IDX_FEP_DAILY_LIMIT_CARD", columnList = "cardNumber"),
    @Index(name = "IDX_FEP_DAILY_LIMIT_DATE", columnList = "transactionDate")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_FEP_DAILY_LIMIT",
        columnNames = {"accountNumber", "transactionType", "transactionDate"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepDailyLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_daily_limit_seq")
    @SequenceGenerator(name = "fep_daily_limit_seq", sequenceName = "SEQ_FEP_DAILY_LIMIT", allocationSize = 50)
    private Long id;

    /**
     * 帳號
     */
    @Column(name = "account_number", nullable = false, length = 32)
    private String accountNumber;

    /**
     * 卡號 (可選)
     */
    @Column(name = "card_number", length = 19)
    private String cardNumber;

    /**
     * 交易類型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    /**
     * 交易日期 (yyyyMMdd)
     */
    @Column(name = "transaction_date", nullable = false, length = 10)
    private String transactionDate;

    /**
     * 日累計金額
     */
    @Column(name = "daily_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyAmount;

    /**
     * 日累計次數
     */
    @Column(name = "daily_count", nullable = false)
    private Integer dailyCount;

    /**
     * 日限額 (金額)
     */
    @Column(name = "daily_limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyLimitAmount;

    /**
     * 日限額 (次數)
     */
    @Column(name = "daily_limit_count", nullable = false)
    private Integer dailyLimitCount;

    /**
     * 銀行代碼
     */
    @Column(name = "bank_code", length = 10)
    private String bankCode;

    /**
     * 客戶類型 (個人/企業)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 20)
    private CustomerType customerType;

    /**
     * 是否為約定帳戶
     */
    @Column(name = "is_designated", nullable = false)
    private Boolean isDesignated;

    /**
     * 建立時間
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新時間
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 版本號 (樂觀鎖)
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 交易類型枚舉
     */
    public enum TransactionType {
        /**
         * 跨行轉帳
         */
        INTERBANK_TRANSFER,

        /**
         * 跨行提款
         */
        INTERBANK_WITHDRAWAL,

        /**
         * 跨行存款
         */
        INTERBANK_DEPOSIT,

        /**
         * 約定轉帳
         */
        DESIGNATED_TRANSFER,

        /**
         * 非約定轉帳
         */
        NON_DESIGNATED_TRANSFER,

        /**
         * 代繳
         */
        BILL_PAYMENT,

        /**
         * 行動支付
         */
        MOBILE_PAYMENT,

        /**
         * 所有交易 (總累計)
         */
        ALL
    }

    /**
     * 客戶類型枚舉
     */
    public enum CustomerType {
        /**
         * 個人
         */
        PERSONAL,

        /**
         * 企業
         */
        CORPORATE
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (dailyAmount == null) {
            dailyAmount = BigDecimal.ZERO;
        }
        if (dailyCount == null) {
            dailyCount = 0;
        }
        if (isDesignated == null) {
            isDesignated = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 檢查是否已達金額限額
     */
    public boolean isAmountLimitExceeded(BigDecimal additionalAmount) {
        return dailyAmount.add(additionalAmount).compareTo(dailyLimitAmount) > 0;
    }

    /**
     * 檢查是否已達次數限額
     */
    public boolean isCountLimitExceeded() {
        return dailyCount >= dailyLimitCount;
    }

    /**
     * 累加交易
     */
    public void accumulate(BigDecimal amount) {
        this.dailyAmount = this.dailyAmount.add(amount);
        this.dailyCount = this.dailyCount + 1;
    }

    /**
     * 扣減交易 (沖正時使用)
     */
    public void deduct(BigDecimal amount) {
        this.dailyAmount = this.dailyAmount.subtract(amount);
        if (this.dailyAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.dailyAmount = BigDecimal.ZERO;
        }
        this.dailyCount = this.dailyCount - 1;
        if (this.dailyCount < 0) {
            this.dailyCount = 0;
        }
    }
}
