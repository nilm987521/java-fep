package com.fep.transaction.domain;

import com.fep.transaction.enums.ResponseCode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction response domain object.
 * Contains the result of a processed transaction.
 */
@Data
@Builder
public class TransactionResponse {

    /** Original transaction ID */
    private String transactionId;

    /** Response code (Field 39) */
    private String responseCode;

    /** Response code enum */
    private ResponseCode responseCodeEnum;

    /** Response description */
    private String responseDescription;

    /** Chinese response description */
    private String responseDescriptionChinese;

    /** Whether the transaction was approved */
    private boolean approved;

    /** Authorization code (Field 38) */
    private String authorizationCode;

    /** Available balance after transaction */
    private BigDecimal availableBalance;

    /** Ledger balance */
    private BigDecimal ledgerBalance;

    /** Transaction amount (confirmed) */
    private BigDecimal amount;

    /** Currency code */
    private String currencyCode;

    /** Host reference number */
    private String hostReferenceNumber;

    /** RRN from original request */
    private String rrn;

    /** STAN from original request */
    private String stan;

    /** Response date/time from host */
    private LocalDateTime hostDateTime;

    /** Processing time in milliseconds */
    private long processingTimeMs;

    /** Response timestamp */
    @Builder.Default
    private LocalDateTime responseTime = LocalDateTime.now();

    /** Additional response data */
    private String additionalData;

    /** Error details (if any) */
    private String errorDetails;

    // Bill Payment Response Fields (代收代付)
    /** Bill payment number (繳費編號) */
    private String billPaymentNumber;

    /** Bill type code */
    private String billTypeCode;

    /** Payment receipt number */
    private String paymentReceiptNumber;

    // E-Ticket Top-up Response Fields (電子票證加值)
    /** E-ticket card balance after top-up */
    private BigDecimal eTicketBalance;

    /** E-ticket card number */
    private String eTicketCardNumber;

    // Taiwan Pay Response Fields (台灣Pay)
    /** Taiwan Pay transaction reference */
    private String taiwanPayReference;

    /** Merchant name */
    private String merchantName;

    // Cardless Transaction Response Fields (無卡交易)
    /** Cardless withdrawal reference */
    private String cardlessReference;

    /** Withdrawal expiry time */
    private LocalDateTime cardlessExpiry;

    // Cross-Border Payment Response Fields (跨境支付)
    /** Cross-border transaction reference */
    private String crossBorderReference;

    /** SWIFT transaction reference */
    private String swiftReference;

    /** Estimated arrival date */
    private LocalDateTime estimatedArrival;

    /** Fee charged for cross-border transfer */
    private BigDecimal crossBorderFee;

    // Foreign Currency Exchange Response Fields (外幣兌換)
    /** Applied exchange rate */
    private BigDecimal appliedExchangeRate;

    /** Exchange reference number */
    private String exchangeReference;

    /** Foreign currency amount */
    private BigDecimal foreignAmount;

    /** Exchange fee */
    private BigDecimal exchangeFee;

    /** Rate expiry time */
    private LocalDateTime rateExpiryTime;

    // E-Wallet Response Fields (電子錢包)
    /** E-wallet transaction reference */
    private String eWalletReference;

    /** E-wallet balance after transaction */
    private BigDecimal eWalletBalance;

    /** E-wallet provider name */
    private String eWalletProviderName;

    /**
     * Creates a successful response.
     */
    public static TransactionResponse success(String transactionId, String authCode) {
        return TransactionResponse.builder()
                .transactionId(transactionId)
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(authCode)
                .build();
    }

    /**
     * Creates a failed response with the given response code.
     */
    public static TransactionResponse failure(String transactionId, ResponseCode responseCode) {
        return TransactionResponse.builder()
                .transactionId(transactionId)
                .responseCode(responseCode.getCode())
                .responseCodeEnum(responseCode)
                .responseDescription(responseCode.getDescription())
                .responseDescriptionChinese(responseCode.getChineseDescription())
                .approved(false)
                .build();
    }

    /**
     * Creates a failure response with custom error details.
     */
    public static TransactionResponse failure(String transactionId, ResponseCode responseCode, String errorDetails) {
        TransactionResponse response = failure(transactionId, responseCode);
        response.setErrorDetails(errorDetails);
        return response;
    }

    /**
     * Gets the formatted response message for logging.
     */
    public String getFormattedResponse() {
        return String.format("[%s] %s - %s (%s)",
                transactionId,
                responseCode,
                responseDescription,
                approved ? "APPROVED" : "DECLINED");
    }
}
