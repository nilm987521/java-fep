package com.fep.transaction.timeout;

import com.fep.transaction.db.entity.FepTransactionEntity;
import com.fep.transaction.db.service.PendingTransactionDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timeout Scanner 服務
 *
 * <p>定期掃描 DB 中超時的交易，並啟動 Timeout BPMN 流程處理。
 *
 * <p>純 DB 架構：使用 FOR UPDATE SKIP LOCKED 替代 Redis 分散式鎖。
 *
 * <p>多實例部署支援：
 * <ul>
 *   <li>使用 DB 悲觀鎖 + SKIP LOCKED 避免重複處理</li>
 *   <li>每個實例獨立掃描，已被鎖定的交易會被跳過</li>
 *   <li>不需要 Leader Election，所有實例均可參與掃描</li>
 * </ul>
 *
 * <p>掃描邏輯：
 * <pre>
 * 1. 查詢狀態為 SENT 且 expireTime <= now 的交易
 * 2. 使用 FOR UPDATE SKIP LOCKED 避免重複掃描
 * 3. 對每筆超時交易：
 *    a. 更新狀態為 TIMEOUT
 *    b. 啟動 Timeout BPMN 流程
 * </pre>
 *
 * <p>配置範例：
 * <pre>
 * fep:
 *   timeout-scanner:
 *     enabled: true
 *     interval-ms: 1000
 *     batch-size: 100
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fep.timeout-scanner.enabled", havingValue = "true", matchIfMissing = true)
public class TimeoutScannerService {

    private final PendingTransactionDbService dbService;
    private final RuntimeService runtimeService;

    /**
     * Timeout BPMN Process Key
     */
    private static final String TIMEOUT_PROCESS_KEY = "Process_TransferTimeout";

    /**
     * 實例唯一識別碼 (用於日誌追蹤)
     */
    private String instanceId;

    @Value("${fep.timeout-scanner.batch-size:100}")
    private int batchSize;

    @PostConstruct
    public void init() {
        this.instanceId = "fep-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Timeout Scanner initialized: instanceId={}, batchSize={}",
                instanceId, batchSize);
    }

    /**
     * 定期掃描超時交易
     *
     * <p>預設每秒執行一次
     *
     * <p>使用 @Transactional 確保整個掃描在同一交易中完成，
     * FOR UPDATE SKIP LOCKED 鎖定會在交易結束時釋放。
     */
    @Scheduled(fixedRateString = "${fep.timeout-scanner.interval-ms:1000}")
    @Transactional
    public void scanTimeoutTransactions() {
        try {
            doScan();
        } catch (Exception e) {
            log.error("Timeout scanning failed: instanceId={}, error={}",
                    instanceId, e.getMessage(), e);
        }
    }

    /**
     * 執行掃描邏輯
     */
    private void doScan() {
        // 1. 取得超時交易 (已被 FOR UPDATE SKIP LOCKED 鎖定)
        List<FepTransactionEntity> timeoutTransactions = dbService.scanTimeoutTransactionEntities(batchSize);

        if (timeoutTransactions.isEmpty()) {
            return;
        }

        log.info("Found {} timeout transactions: instanceId={}", timeoutTransactions.size(), instanceId);

        // 2. 對每筆超時交易啟動 Timeout BPMN 流程
        int processed = 0;
        int failed = 0;

        for (FepTransactionEntity entity : timeoutTransactions) {
            try {
                // 更新狀態為 TIMEOUT (避免重複掃描)
                entity.setStatus(FepTransactionEntity.TransactionStatus.TIMEOUT);
                dbService.save(entity);

                // 啟動 Timeout 流程
                startTimeoutProcess(entity);
                processed++;

            } catch (Exception e) {
                log.error("Failed to process timeout transaction: txnId={}, error={}",
                        entity.getTransactionId(), e.getMessage(), e);
                failed++;
            }
        }

        if (processed > 0 || failed > 0) {
            log.info("Timeout scan completed: processed={}, failed={}, instanceId={}",
                    processed, failed, instanceId);
        }
    }

    /**
     * 啟動 Timeout BPMN 流程
     *
     * @param entity 超時交易實體
     */
    private void startTimeoutProcess(FepTransactionEntity entity) {
        String transactionId = entity.getTransactionId();
        log.info("Starting Timeout BPMN process: txnId={}", transactionId);

        // 準備流程變數
        Map<String, Object> variables = new HashMap<>();
        variables.put("transactionId", transactionId);
        variables.put("stan", entity.getStan());
        variables.put("timeoutDetectedAt", System.currentTimeMillis());
        variables.put("timeoutReason", "FISC_RESPONSE_TIMEOUT");

        // 啟動流程
        String businessKey = "TIMEOUT-" + transactionId;
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                TIMEOUT_PROCESS_KEY,
                businessKey,
                variables
        );

        log.info("Timeout BPMN process started: processId={}, txnId={}",
                instance.getId(), transactionId);
    }

    /**
     * 手動觸發掃描 (監控/測試用)
     *
     * @return 處理的交易數量
     */
    @Transactional
    public int triggerScan() {
        log.info("Manual scan triggered: instanceId={}", instanceId);

        List<FepTransactionEntity> timeoutTransactions = dbService.scanTimeoutTransactionEntities(batchSize);
        int processed = 0;

        for (FepTransactionEntity entity : timeoutTransactions) {
            try {
                entity.setStatus(FepTransactionEntity.TransactionStatus.TIMEOUT);
                dbService.save(entity);
                startTimeoutProcess(entity);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process timeout transaction: txnId={}", entity.getTransactionId(), e);
            }
        }

        return processed;
    }

    /**
     * 取得實例 ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 取得 Pending (SENT) 交易數量
     */
    public long getPendingCount() {
        return dbService.getPendingCount();
    }
}
