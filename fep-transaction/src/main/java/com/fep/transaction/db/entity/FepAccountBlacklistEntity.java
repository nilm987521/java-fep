package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FEP 帳號黑名單 Entity
 *
 * <p>記錄被列入黑名單的帳號資訊，包括警示帳戶、
 * 凍結帳戶、詐欺帳戶等。轉帳交易時會檢查收款帳戶。
 */
@Entity
@Table(name = "FEP_ACCOUNT_BLACKLIST", indexes = {
    @Index(name = "IDX_FEP_ACCT_BL_ACCT_NUM", columnList = "accountNumber"),
    @Index(name = "IDX_FEP_ACCT_BL_BANK_CODE", columnList = "bankCode"),
    @Index(name = "IDX_FEP_ACCT_BL_STATUS", columnList = "status"),
    @Index(name = "IDX_FEP_ACCT_BL_TYPE", columnList = "blacklistType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepAccountBlacklistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_acct_bl_seq")
    @SequenceGenerator(name = "fep_acct_bl_seq", sequenceName = "SEQ_FEP_ACCOUNT_BLACKLIST", allocationSize = 50)
    private Long id;

    /**
     * 帳號
     */
    @Column(name = "account_number", nullable = false, length = 32)
    private String accountNumber;

    /**
     * 銀行代碼
     */
    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    /**
     * 黑名單類型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "blacklist_type", nullable = false, length = 30)
    private AccountBlacklistType blacklistType;

    /**
     * 狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlacklistStatus status;

    /**
     * 限制方向
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_direction", nullable = false, length = 20)
    private RestrictionDirection restrictionDirection;

    /**
     * 戶名 (可選)
     */
    @Column(name = "account_name", length = 100)
    private String accountName;

    /**
     * 身分證字號/統編 (可選)
     */
    @Column(name = "id_number", length = 20)
    private String idNumber;

    /**
     * 原因說明
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * 來源 (如：警示帳戶通報、法院命令、銀行內部)
     */
    @Column(name = "source", length = 50)
    private String source;

    /**
     * 通報文號 (如：警示帳戶通報文號)
     */
    @Column(name = "notification_no", length = 50)
    private String notificationNo;

    /**
     * 生效日期
     */
    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    /**
     * 失效日期 (null 表示永久有效)
     */
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    /**
     * 建立人員
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * 建立時間
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新人員
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

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
     * 帳號黑名單類型枚舉
     */
    public enum AccountBlacklistType {
        /**
         * 警示帳戶 (165 通報)
         */
        ALERT_ACCOUNT,

        /**
         * 衍生警示帳戶
         */
        DERIVED_ALERT,

        /**
         * 法院凍結
         */
        COURT_FROZEN,

        /**
         * 詐欺帳戶
         */
        FRAUD,

        /**
         * 洗錢可疑帳戶
         */
        AML_SUSPICIOUS,

        /**
         * 銀行內部凍結
         */
        BANK_FROZEN,

        /**
         * 戶況異常
         */
        ABNORMAL_STATUS,

        /**
         * 其他風險
         */
        OTHER_RISK
    }

    /**
     * 黑名單狀態枚舉
     */
    public enum BlacklistStatus {
        /**
         * 生效中
         */
        ACTIVE,

        /**
         * 已失效
         */
        INACTIVE,

        /**
         * 待審核
         */
        PENDING_REVIEW,

        /**
         * 已解除
         */
        RELEASED
    }

    /**
     * 限制方向枚舉
     */
    public enum RestrictionDirection {
        /**
         * 限制轉入
         */
        INBOUND,

        /**
         * 限制轉出
         */
        OUTBOUND,

        /**
         * 雙向限制
         */
        BOTH
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BlacklistStatus.ACTIVE;
        }
        if (restrictionDirection == null) {
            restrictionDirection = RestrictionDirection.BOTH;
        }
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 檢查黑名單是否生效中
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        if (status != BlacklistStatus.ACTIVE) {
            return false;
        }
        if (effectiveDate.isAfter(now)) {
            return false;
        }
        if (expiryDate != null && expiryDate.isBefore(now)) {
            return false;
        }
        return true;
    }

    /**
     * 檢查是否限制指定方向的交易
     */
    public boolean isRestricted(RestrictionDirection direction) {
        if (!isEffective()) {
            return false;
        }
        if (restrictionDirection == RestrictionDirection.BOTH) {
            return true;
        }
        return restrictionDirection == direction;
    }
}
