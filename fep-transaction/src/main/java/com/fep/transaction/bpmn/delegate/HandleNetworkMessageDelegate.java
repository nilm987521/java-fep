package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BPMN Service Task Delegate: 處理網路管理訊息
 *
 * <p>對應 BPMN 中的 Task_HandleNetworkMessage
 *
 * <p>處理 MTI 0800 網路管理訊息，包括：
 * <ul>
 *   <li>Sign-On (301): 簽到</li>
 *   <li>Sign-Off (302): 簽退</li>
 *   <li>Echo Test (300): 回音測試</li>
 *   <li>Key Exchange: 換鑰</li>
 * </ul>
 *
 * <p>此 Delegate 是完全配置化設計的一部分，當 application.yml 中配置
 * MTI=0800 路由至 Process_NetworkManagement 時，此 Delegate 會被調用。
 */
@Slf4j
@Component("handleNetworkMessageDelegate")
@RequiredArgsConstructor
public class HandleNetworkMessageDelegate implements JavaDelegate {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String mti = (String) execution.getVariable("mti");
        String processingCode = (String) execution.getVariable("processingCode");
        String channelId = (String) execution.getVariable("channelId");

        log.info("[{}] 處理網路管理訊息: MTI={}, processingCode={}, channel={}",
                transactionId, mti, processingCode, channelId);

        try {
            // 判斷網路管理類型
            String networkFunctionCode = extractNetworkFunctionCode(processingCode);
            String responseCode = processNetworkMessage(networkFunctionCode, execution);

            // 設定回應資訊
            LocalDateTime now = LocalDateTime.now();
            execution.setVariable("responseMti", "0810");
            execution.setVariable("responseCode", responseCode);
            execution.setVariable("responseTime", now.format(TIME_FORMAT));
            execution.setVariable("responseDate", now.format(DATE_FORMAT));
            execution.setVariable("networkFunctionCode", networkFunctionCode);

            log.info("[{}] 網路管理處理完成: function={}, responseCode={}",
                    transactionId, networkFunctionCode, responseCode);

        } catch (Exception e) {
            log.error("[{}] 網路管理處理失敗: {}", transactionId, e.getMessage(), e);
            execution.setVariable("responseMti", "0810");
            execution.setVariable("responseCode", "96"); // System malfunction
            execution.setVariable("errorMessage", e.getMessage());
        }
    }

    /**
     * 提取網路功能代碼
     *
     * <p>Processing Code 欄位 3 的前 2 位代表網路功能：
     * <ul>
     *   <li>30: Echo Test (回音測試)</li>
     *   <li>31: Sign-On (簽到)</li>
     *   <li>32: Sign-Off (簽退)</li>
     *   <li>16: Key Exchange (換鑰)</li>
     * </ul>
     */
    private String extractNetworkFunctionCode(String processingCode) {
        if (processingCode == null || processingCode.length() < 2) {
            return "UNKNOWN";
        }
        return processingCode.substring(0, 2);
    }

    /**
     * 處理網路管理訊息
     *
     * @param functionCode 網路功能代碼
     * @param execution 執行上下文
     * @return 回應碼
     */
    private String processNetworkMessage(String functionCode, DelegateExecution execution) {
        return switch (functionCode) {
            case "30" -> handleEchoTest(execution);
            case "31" -> handleSignOn(execution);
            case "32" -> handleSignOff(execution);
            case "16" -> handleKeyExchange(execution);
            default -> {
                log.warn("未知的網路功能代碼: {}", functionCode);
                yield "12"; // Invalid transaction
            }
        };
    }

    /**
     * 處理回音測試 (Echo Test)
     */
    private String handleEchoTest(DelegateExecution execution) {
        String transactionId = execution.getProcessInstanceId();
        log.info("[{}] 處理回音測試 (Echo Test)", transactionId);

        // Echo Test 直接回應成功
        execution.setVariable("echoStatus", "SUCCESS");
        return "00"; // Approved
    }

    /**
     * 處理簽到 (Sign-On)
     */
    private String handleSignOn(DelegateExecution execution) {
        String transactionId = execution.getProcessInstanceId();
        String channelId = (String) execution.getVariable("channelId");
        log.info("[{}] 處理簽到 (Sign-On): channel={}", transactionId, channelId);

        // TODO: 實際簽到邏輯
        // 1. 驗證終端/通道身份
        // 2. 下發工作金鑰
        // 3. 記錄簽到時間

        execution.setVariable("signOnStatus", "SUCCESS");
        execution.setVariable("signOnTime", LocalDateTime.now().toString());

        return "00"; // Approved
    }

    /**
     * 處理簽退 (Sign-Off)
     */
    private String handleSignOff(DelegateExecution execution) {
        String transactionId = execution.getProcessInstanceId();
        String channelId = (String) execution.getVariable("channelId");
        log.info("[{}] 處理簽退 (Sign-Off): channel={}", transactionId, channelId);

        // TODO: 實際簽退邏輯
        // 1. 標記終端/通道已簽退
        // 2. 記錄簽退時間

        execution.setVariable("signOffStatus", "SUCCESS");
        execution.setVariable("signOffTime", LocalDateTime.now().toString());

        return "00"; // Approved
    }

    /**
     * 處理換鑰 (Key Exchange)
     */
    private String handleKeyExchange(DelegateExecution execution) {
        String transactionId = execution.getProcessInstanceId();
        log.info("[{}] 處理換鑰 (Key Exchange)", transactionId);

        // TODO: 實際換鑰邏輯
        // 1. 與 HSM 整合產生新金鑰
        // 2. 加密傳輸新金鑰
        // 3. 更新金鑰版本

        execution.setVariable("keyExchangeStatus", "SUCCESS");
        execution.setVariable("keyExchangeTime", LocalDateTime.now().toString());

        return "00"; // Approved
    }
}
