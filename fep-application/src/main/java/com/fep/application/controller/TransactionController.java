package com.fep.application.controller;

import com.fep.application.dto.ApiResponse;
import com.fep.application.dto.TransactionRequest;
import com.fep.application.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction API Controller
 */
@Slf4j
@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction API", description = "交易處理 API")
public class TransactionController {

    @PostMapping
    @Operation(summary = "執行交易", description = "處理各類跨行交易請求")
    public ResponseEntity<ApiResponse<TransactionResponse>> processTransaction(
            @Valid @RequestBody TransactionRequest request) {

        long startTime = System.currentTimeMillis();
        log.info("Processing transaction: type={}, terminal={}",
                request.getTransactionType(), request.getTerminalId());

        try {
            // TODO: Integrate with actual transaction pipeline
            // TransactionResult result = transactionPipeline.process(request);

            // Mock response for now
            TransactionResponse response = TransactionResponse.builder()
                    .responseCode("00")
                    .responseMessage("交易成功")
                    .referenceNumber(generateRRN())
                    .traceNumber(generateSTAN())
                    .authorizationCode(generateAuthCode())
                    .transactionTime(LocalDateTime.now())
                    .amount(request.getAmount())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            log.info("Transaction completed: rrn={}, code={}",
                    response.getReferenceNumber(), response.getResponseCode());

            return ResponseEntity.ok(ApiResponse.success(response, "交易處理成功"));

        } catch (Exception e) {
            log.error("Transaction failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("9999", "交易處理失敗: " + e.getMessage()));
        }
    }

    @PostMapping("/withdrawal")
    @Operation(summary = "跨行提款", description = "ATM 跨行提款交易")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdrawal(
            @Valid @RequestBody TransactionRequest request) {
        request.setTransactionType("WITHDRAWAL");
        return processTransaction(request);
    }

    @PostMapping("/transfer")
    @Operation(summary = "跨行轉帳", description = "跨行轉帳交易")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransactionRequest request) {
        request.setTransactionType("TRANSFER");
        return processTransaction(request);
    }

    @PostMapping("/balance-inquiry")
    @Operation(summary = "餘額查詢", description = "跨行餘額查詢")
    public ResponseEntity<ApiResponse<TransactionResponse>> balanceInquiry(
            @Valid @RequestBody TransactionRequest request) {

        log.info("Balance inquiry: account={}", maskAccount(request.getSourceAccount()));

        // Mock balance response
        TransactionResponse response = TransactionResponse.builder()
                .responseCode("00")
                .responseMessage("查詢成功")
                .referenceNumber(generateRRN())
                .traceNumber(generateSTAN())
                .transactionTime(LocalDateTime.now())
                .availableBalance(new BigDecimal("50000.00"))
                .ledgerBalance(new BigDecimal("52000.00"))
                .processingTimeMs(50L)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/bill-payment")
    @Operation(summary = "代收代付", description = "繳費代收代付交易")
    public ResponseEntity<ApiResponse<TransactionResponse>> billPayment(
            @Valid @RequestBody TransactionRequest request) {
        request.setTransactionType("BILL_PAYMENT");
        return processTransaction(request);
    }

    @GetMapping("/{referenceNumber}")
    @Operation(summary = "查詢交易", description = "依交易參考編號查詢交易狀態")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @Parameter(description = "交易參考編號") @PathVariable String referenceNumber) {

        log.info("Query transaction: rrn={}", referenceNumber);

        // TODO: Query from transaction repository
        TransactionResponse response = TransactionResponse.builder()
                .responseCode("00")
                .responseMessage("交易成功")
                .referenceNumber(referenceNumber)
                .transactionTime(LocalDateTime.now().minusMinutes(5))
                .amount(new BigDecimal("1000.00"))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{referenceNumber}/reversal")
    @Operation(summary = "沖正交易", description = "沖正指定交易")
    public ResponseEntity<ApiResponse<TransactionResponse>> reversal(
            @Parameter(description = "原交易參考編號") @PathVariable String referenceNumber) {

        log.info("Reversal request: originalRrn={}", referenceNumber);

        TransactionResponse response = TransactionResponse.builder()
                .responseCode("00")
                .responseMessage("沖正成功")
                .referenceNumber(generateRRN())
                .traceNumber(generateSTAN())
                .transactionTime(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "沖正處理成功"));
    }

    // Helper methods
    private String generateRRN() {
        return String.format("%012d", System.currentTimeMillis() % 1000000000000L);
    }

    private String generateSTAN() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    private String generateAuthCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 4) {
            return "****";
        }
        return "****" + account.substring(account.length() - 4);
    }
}
