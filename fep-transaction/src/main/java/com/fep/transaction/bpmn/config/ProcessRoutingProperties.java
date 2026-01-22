package com.fep.transaction.bpmn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * BPMN 流程路由設定屬性
 *
 * <p>透過設定檔定義 通道 (channelId) + 交易類型 (MTI) → BPMN 流程 的 mapping 規則。
 *
 * <p>高 TPS 架構使用三個獨立流程：
 * <ul>
 *   <li>{@code Process_TransferRequest} - 請求流程（快速結束，不等待回應）</li>
 *   <li>{@code Process_TransferResponse} - 回應流程（由 FiscResponseHandler 觸發）</li>
 *   <li>{@code Process_TransferTimeout} - 超時流程（由 TimeoutScanner 觸發）</li>
 * </ul>
 *
 * <p>設定範例：
 * <pre>
 * fep:
 *   bpmn:
 *     process-routing:
 *       enabled: true
 *       default-process: Process_TransferRequest
 *       rules:
 *         - name: ATM 2500 交易
 *           channel-pattern: "ATM.*"
 *           mti: "2500"
 *           process-key: Only_Log_Process
 *           priority: 10
 *         - name: 跨行轉帳
 *           channel-pattern: ".*"
 *           mti: "0200"
 *           processing-code: "40"
 *           process-key: Process_TransferRequest
 *           priority: 100
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "fep.bpmn.process-routing")
public class ProcessRoutingProperties {

    /**
     * 是否啟用流程路由
     */
    private boolean enabled = true;

    /**
     * 預設流程 Key（當無匹配規則時使用）
     *
     * <p>高 TPS 架構預設使用 Request 流程
     */
    private String defaultProcess = "Process_TransferRequest";

    /**
     * 路由規則列表
     */
    private List<RoutingRule> rules = new ArrayList<>();

    /**
     * 路由規則
     */
    @Data
    public static class RoutingRule {
        /**
         * 規則名稱（用於日誌識別）
         */
        private String name;

        /**
         * 通道匹配模式（支援正則表達式）
         * <p>例如：
         * <ul>
         *   <li>"ATM.*" - 匹配所有 ATM 開頭的通道</li>
         *   <li>".*" - 匹配所有通道</li>
         *   <li>"ATM_FISC_V1" - 精確匹配</li>
         * </ul>
         */
        private String channelPattern;

        /**
         * MTI (Message Type Indicator)
         * <p>例如："0200", "0400", "2500"
         */
        private String mti;

        /**
         * Processing Code 前綴（可選，用於進一步限制）
         * <p>例如："40" 匹配轉帳交易
         */
        private String processingCode;

        /**
         * 對應的 BPMN 流程 Key
         * <p>必須與 BPMN 檔案中定義的 process id 一致
         */
        private String processKey;

        /**
         * 優先級（數字越小越優先）
         */
        private int priority = 100;
    }
}
