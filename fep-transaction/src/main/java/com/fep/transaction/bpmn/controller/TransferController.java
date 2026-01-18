package com.fep.transaction.bpmn.controller;

import com.fep.transaction.bpmn.service.TransferProcessService;
import com.fep.transaction.bpmn.service.TransferProcessService.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 跨行轉帳 REST API
 *
 * <p>提供外部系統呼叫 BPMN 流程的入口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferProcessService processService;

    /**
     * 發起跨行轉帳
     *
     * <p>範例請求:
     * <pre>
     * POST /api/v1/transfer
     * {
     *   "sourceAccount": "1234567890123456",
     *   "targetAccount": "9876543210987654",
     *   "amount": 10000,
     *   "sourceBankCode": "004",
     *   "targetBankCode": "012",
     *   "designated": true,
     *   "channel": "ATM"
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(@RequestBody TransferRequestDto request) {
        log.info("收到轉帳請求: {} -> {}, 金額={}",
            request.getSourceAccount(), request.getTargetAccount(), request.getAmount());

        // 產生業務鍵
        String businessKey = generateBusinessKey();

        // 轉換為服務層請求
        TransferRequest processRequest = TransferRequest.builder()
            .businessKey(businessKey)
            .sourceAccount(request.getSourceAccount())
            .targetAccount(request.getTargetAccount())
            .amount(request.getAmount())
            .sourceBankCode(request.getSourceBankCode())
            .targetBankCode(request.getTargetBankCode())
            .designated(request.isDesignated())
            .channel(request.getChannel())
            .build();

        // 啟動流程
        String processId = processService.startTransferProcess(processRequest);

        return ResponseEntity.ok(TransferResponse.builder()
            .transactionId(businessKey)
            .processId(processId)
            .status("PROCESSING")
            .message("轉帳請求已受理")
            .build());
    }

    /**
     * 查詢轉帳狀態
     */
    @GetMapping("/{processId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String processId) {
        TransferProcessService.ProcessStatus status = processService.getProcessStatus(processId);

        return ResponseEntity.ok(Map.of(
            "processId", processId,
            "status", status.name()
        ));
    }

    /**
     * 取消轉帳 (僅限處理中的交易)
     */
    @DeleteMapping("/{processId}")
    public ResponseEntity<Void> cancelTransfer(
            @PathVariable String processId,
            @RequestParam(defaultValue = "用戶取消") String reason) {

        processService.cancelProcess(processId, reason);
        return ResponseEntity.noContent().build();
    }

    private String generateBusinessKey() {
        // 格式: TXN + 時間戳 + 隨機數
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    // ==================== DTO ====================

    @lombok.Data
    public static class TransferRequestDto {
        private String sourceAccount;
        private String targetAccount;
        private long amount;
        private String sourceBankCode;
        private String targetBankCode;
        private boolean designated;
        private String channel;
    }

    @lombok.Data
    @lombok.Builder
    public static class TransferResponse {
        private String transactionId;
        private String processId;
        private String status;
        private String message;
    }
}
