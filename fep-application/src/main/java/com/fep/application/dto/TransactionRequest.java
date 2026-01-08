package com.fep.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Transaction Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    /**
     * Transaction type
     * WITHDRAWAL, TRANSFER, BALANCE_INQUIRY, DEPOSIT, BILL_PAYMENT, etc.
     */
    @NotBlank(message = "交易類型不得為空")
    private String transactionType;

    /**
     * Channel type
     * ATM, POS, INTERNET_BANKING, MOBILE_BANKING
     */
    @NotBlank(message = "通路類型不得為空")
    private String channelType;

    /**
     * Card number (masked for security)
     */
    @Pattern(regexp = "^[0-9]{16}$", message = "卡號格式錯誤")
    private String cardNumber;

    /**
     * Source account number
     */
    @NotBlank(message = "來源帳號不得為空")
    @Size(min = 10, max = 16, message = "帳號長度須為10-16碼")
    private String sourceAccount;

    /**
     * Destination account number (for transfers)
     */
    @Size(min = 10, max = 16, message = "帳號長度須為10-16碼")
    private String destinationAccount;

    /**
     * Destination bank code (for transfers)
     */
    @Pattern(regexp = "^[0-9]{3}$", message = "銀行代碼須為3碼數字")
    private String destinationBankCode;

    /**
     * Transaction amount
     */
    @DecimalMin(value = "0.01", message = "交易金額須大於0")
    private BigDecimal amount;

    /**
     * Currency code (default: TWD)
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "幣別代碼須為3碼英文")
    private String currency = "TWD";

    /**
     * Terminal ID
     */
    @NotBlank(message = "終端機代號不得為空")
    private String terminalId;

    /**
     * PIN Block (encrypted)
     */
    private String pinBlock;

    /**
     * Bill payment - Payee code
     */
    private String payeeCode;

    /**
     * Bill payment - Payment number
     */
    private String paymentNumber;

    /**
     * Reference note
     */
    @Size(max = 100, message = "備註最多100字")
    private String memo;
}
