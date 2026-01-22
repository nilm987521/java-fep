package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FEP 卡片黑名單 Entity
 *
 * <p>記錄被列入黑名單的卡片資訊，包括掛失卡、偽卡、
 * 風險卡等。交易驗證時會檢查此表。
 */
@Entity
@Table(name = "FEP_CARD_BLACKLIST", indexes = {
    @Index(name = "IDX_FEP_CARD_BL_CARD_NUM", columnList = "cardNumber"),
    @Index(name = "IDX_FEP_CARD_BL_STATUS", columnList = "status"),
    @Index(name = "IDX_FEP_CARD_BL_TYPE", columnList = "blacklistType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepCardBlacklistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_card_bl_seq")
    @SequenceGenerator(name = "fep_card_bl_seq", sequenceName = "SEQ_FEP_CARD_BLACKLIST", allocationSize = 50)
    private Long id;

    /**
     * 卡號
     */
    @Column(name = "card_number", nullable = false, length = 19)
    private String cardNumber;

    /**
     * 卡號遮罩 (顯示用)
     */
    @Column(name = "card_number_masked", length = 19)
    private String cardNumberMasked;

    /**
     * 黑名單類型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "blacklist_type", nullable = false, length = 30)
    private BlacklistType blacklistType;

    /**
     * 狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BlacklistStatus status;

    /**
     * 發卡銀行代碼
     */
    @Column(name = "issuer_bank_code", length = 10)
    private String issuerBankCode;

    /**
     * 原因說明
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * 來源 (如：財金通報、銀行通報、系統偵測)
     */
    @Column(name = "source", length = 50)
    private String source;

    /**
     * 財金通報序號 (如有)
     */
    @Column(name = "fisc_notification_no", length = 30)
    private String fiscNotificationNo;

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
     * 黑名單類型枚舉
     */
    public enum BlacklistType {
        /**
         * 掛失卡
         */
        LOST,

        /**
         * 被竊卡
         */
        STOLEN,

        /**
         * 偽卡
         */
        COUNTERFEIT,

        /**
         * 詐欺
         */
        FRAUD,

        /**
         * 逾期未繳
         */
        OVERDUE,

        /**
         * 其他風險
         */
        OTHER_RISK,

        /**
         * 持卡人已歿
         */
        DECEASED,

        /**
         * 系統偵測異常
         */
        SYSTEM_DETECTED
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BlacklistStatus.ACTIVE;
        }
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
        // 產生遮罩卡號
        if (cardNumber != null && cardNumberMasked == null) {
            cardNumberMasked = maskCardNumber(cardNumber);
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
     * 遮罩卡號
     */
    private static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 10) {
            return cardNumber;
        }
        int len = cardNumber.length();
        return cardNumber.substring(0, 6) +
               "*".repeat(len - 10) +
               cardNumber.substring(len - 4);
    }
}
