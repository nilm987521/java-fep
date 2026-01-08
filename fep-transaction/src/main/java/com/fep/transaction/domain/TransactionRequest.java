package com.fep.transaction.domain;

import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction request domain object.
 * Contains all information needed to process a transaction.
 */
@Data
@Builder
public class TransactionRequest {

    /** Unique transaction identifier */
    private String transactionId;

    /** Transaction type */
    private TransactionType transactionType;

    /** Processing code (Field 3) */
    private String processingCode;

    /** Primary Account Number (PAN) */
    private String pan;

    /** Transaction amount */
    private BigDecimal amount;

    /** Currency code (ISO 4217) */
    @Builder.Default
    private String currencyCode = "901"; // TWD

    /** Source account number */
    private String sourceAccount;

    /** Source account type */
    private AccountType sourceAccountType;

    /** Destination account number (for transfers) */
    private String destinationAccount;

    /** Destination account type */
    private AccountType destinationAccountType;

    /** Destination bank code (for interbank transfers) */
    private String destinationBankCode;

    /** Card expiration date (YYMM) */
    private String expirationDate;

    /** Encrypted PIN block */
    private String pinBlock;

    /** Track 2 data */
    private String track2Data;

    /** Terminal ID */
    private String terminalId;

    /** Merchant ID */
    private String merchantId;

    /** Acquiring bank code */
    private String acquiringBankCode;

    /** Transaction date/time */
    @Builder.Default
    private LocalDateTime transactionDateTime = LocalDateTime.now();

    /** STAN (System Trace Audit Number) */
    private String stan;

    /** RRN (Retrieval Reference Number) */
    private String rrn;

    /** Channel (ATM, POS, MOBILE, etc.) */
    private String channel;

    /** Original transaction data (for reversals) */
    private String originalTransactionId;

    // Bill Payment Fields (代收代付)
    /** Bill payment number (繳費編號/銷帳編號) */
    private String billPaymentNumber;

    /** Bill type code (繳費類別代碼) */
    private String billTypeCode;

    /** Payee institution code (代收機構代碼) */
    private String payeeInstitutionCode;

    /** Tax ID for tax payments (統一編號/身分證字號) */
    private String taxId;

    /** Bill due date */
    private LocalDateTime billDueDate;

    // E-Ticket Top-up Fields (電子票證加值)
    /** E-ticket card number */
    private String eTicketCardNumber;

    /** E-ticket type (EasyCard, iPASS, etc.) */
    private String eTicketType;

    // Taiwan Pay / QR Code Fields (台灣Pay/QR碼支付)
    /** QR code data */
    private String qrCodeData;

    /** Merchant QR code (for scan to pay) */
    private String merchantQrCode;

    /** Taiwan Pay token */
    private String taiwanPayToken;

    /** Payment type (PUSH/PULL) */
    private String paymentType;

    // Cardless Transaction Fields (無卡交易)
    /** Cardless withdrawal code */
    private String cardlessCode;

    /** Mobile phone number for cardless */
    private String mobilePhone;

    /** OTP verification code */
    private String otpCode;

    // Designated Account Fields (約定帳戶)
    /** Whether destination is a designated account */
    private Boolean isDesignatedAccount;

    // Cross-Border Payment Fields (跨境支付)
    /** Destination country code (ISO 3166-1 alpha-2) */
    private String destinationCountryCode;

    /** Beneficiary name */
    private String beneficiaryName;

    /** Beneficiary bank SWIFT code */
    private String beneficiaryBankSwift;

    /** Purpose of remittance code */
    private String remittancePurposeCode;

    /** Sender reference number */
    private String senderReference;

    // Foreign Currency Exchange Fields (外幣兌換)
    /** Source currency code (e.g., TWD) */
    private String sourceCurrency;

    /** Target currency code (e.g., USD) */
    private String targetCurrency;

    /** Exchange rate (if pre-agreed) */
    private BigDecimal exchangeRate;

    /** Foreign currency amount */
    private BigDecimal foreignAmount;

    /** Exchange type (BUY/SELL) */
    private String exchangeType;

    // E-Wallet Fields (電子錢包)
    /** E-wallet provider code */
    private String eWalletProvider;

    /** E-wallet account ID */
    private String eWalletAccountId;

    /** E-wallet transaction type (TOPUP/PAYMENT/TRANSFER) */
    private String eWalletTxnType;

    /** E-wallet recipient account (for transfers) */
    private String eWalletRecipient;

    /** E-wallet recipient name */
    private String eWalletRecipientName;

    /** Additional data */
    private String additionalData;

    /** Request timestamp */
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();

    /**
     * Gets the masked PAN for logging.
     */
    public String getMaskedPan() {
        if (pan == null || pan.length() < 10) {
            return "****";
        }
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    /**
     * Gets the amount in minor units (cents/分).
     */
    public long getAmountInMinorUnits() {
        if (amount == null) {
            return 0;
        }
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}
