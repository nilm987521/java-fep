package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FEP 銀行代碼 Entity
 *
 * <p>記錄財金公司核定的銀行代碼資訊，包括銀行名稱、
 * BIC、是否支援特定服務等。用於交易驗證和路由。
 */
@Entity
@Table(name = "FEP_BANK_CODE", indexes = {
    @Index(name = "IDX_FEP_BANK_CODE_STATUS", columnList = "status"),
    @Index(name = "IDX_FEP_BANK_CODE_TYPE", columnList = "institutionType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepBankCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_bank_code_seq")
    @SequenceGenerator(name = "fep_bank_code_seq", sequenceName = "SEQ_FEP_BANK_CODE", allocationSize = 50)
    private Long id;

    /**
     * 銀行代碼 (3 碼或 7 碼)
     */
    @Column(name = "bank_code", nullable = false, unique = true, length = 10)
    private String bankCode;

    /**
     * 總行代碼 (3 碼)
     */
    @Column(name = "head_office_code", length = 3)
    private String headOfficeCode;

    /**
     * 分行代碼 (4 碼，如有)
     */
    @Column(name = "branch_code", length = 4)
    private String branchCode;

    /**
     * 銀行中文名稱
     */
    @Column(name = "bank_name_zh", nullable = false, length = 100)
    private String bankNameZh;

    /**
     * 銀行英文名稱
     */
    @Column(name = "bank_name_en", length = 100)
    private String bankNameEn;

    /**
     * 銀行簡稱
     */
    @Column(name = "short_name", length = 50)
    private String shortName;

    /**
     * SWIFT/BIC 代碼
     */
    @Column(name = "swift_code", length = 11)
    private String swiftCode;

    /**
     * 機構類型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false, length = 30)
    private InstitutionType institutionType;

    /**
     * 狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BankStatus status;

    /**
     * 是否支援跨行轉帳
     */
    @Column(name = "support_transfer", nullable = false)
    private Boolean supportTransfer;

    /**
     * 是否支援跨行提款
     */
    @Column(name = "support_withdrawal", nullable = false)
    private Boolean supportWithdrawal;

    /**
     * 是否支援跨行存款
     */
    @Column(name = "support_deposit", nullable = false)
    private Boolean supportDeposit;

    /**
     * 是否支援餘額查詢
     */
    @Column(name = "support_balance_inquiry", nullable = false)
    private Boolean supportBalanceInquiry;

    /**
     * 是否支援台灣 Pay
     */
    @Column(name = "support_taiwan_pay", nullable = false)
    private Boolean supportTaiwanPay;

    /**
     * 聯絡電話
     */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /**
     * 聯絡地址
     */
    @Column(name = "contact_address", length = 200)
    private String contactAddress;

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
     * 機構類型枚舉
     */
    public enum InstitutionType {
        /**
         * 銀行
         */
        BANK,

        /**
         * 農漁會
         */
        CREDIT_UNION,

        /**
         * 信用合作社
         */
        CREDIT_COOPERATIVE,

        /**
         * 郵局
         */
        POST_OFFICE,

        /**
         * 證券商
         */
        SECURITIES,

        /**
         * 電子支付機構
         */
        E_PAYMENT,

        /**
         * 其他
         */
        OTHER
    }

    /**
     * 銀行狀態枚舉
     */
    public enum BankStatus {
        /**
         * 正常營運
         */
        ACTIVE,

        /**
         * 暫停服務
         */
        SUSPENDED,

        /**
         * 已結束營業
         */
        CLOSED,

        /**
         * 測試中
         */
        TESTING
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BankStatus.ACTIVE;
        }
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
        // 設定預設支援服務
        if (supportTransfer == null) supportTransfer = true;
        if (supportWithdrawal == null) supportWithdrawal = true;
        if (supportDeposit == null) supportDeposit = false;
        if (supportBalanceInquiry == null) supportBalanceInquiry = true;
        if (supportTaiwanPay == null) supportTaiwanPay = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 檢查銀行是否有效
     */
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        if (status != BankStatus.ACTIVE) {
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
     * 取得完整銀行代碼 (總行 + 分行)
     */
    public String getFullBankCode() {
        if (branchCode != null && !branchCode.isEmpty()) {
            return headOfficeCode + branchCode;
        }
        return headOfficeCode;
    }
}
