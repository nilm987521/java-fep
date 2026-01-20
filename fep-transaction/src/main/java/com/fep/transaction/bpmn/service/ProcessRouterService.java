package com.fep.transaction.bpmn.service;

import com.fep.transaction.bpmn.config.ProcessRoutingProperties;
import com.fep.transaction.bpmn.config.ProcessRoutingProperties.RoutingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * BPMN 流程路由服務
 *
 * <p>根據通道 ID、MTI 和 Processing Code 決定要啟動哪個 BPMN 流程。
 *
 * <p>路由邏輯：
 * <ol>
 *   <li>按優先級（priority）排序所有規則</li>
 *   <li>依序檢查每個規則是否匹配</li>
 *   <li>返回第一個匹配規則的 processKey</li>
 *   <li>若無匹配，返回預設流程</li>
 * </ol>
 *
 * <p>匹配條件：
 * <ul>
 *   <li>channelPattern：使用正則表達式匹配 channelId</li>
 *   <li>mti：精確匹配（必要條件）</li>
 *   <li>processingCode：前綴匹配（可選條件）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRouterService {

    private final ProcessRoutingProperties properties;

    /**
     * 已排序的規則列表（按優先級）
     */
    private List<RoutingRule> sortedRules;

    @PostConstruct
    public void init() {
        if (properties.isEnabled() && properties.getRules() != null) {
            sortedRules = properties.getRules().stream()
                    .sorted(Comparator.comparingInt(RoutingRule::getPriority))
                    .toList();
            log.info("流程路由服務初始化完成: 載入 {} 條規則, 預設流程={}",
                    sortedRules.size(), properties.getDefaultProcess());

            sortedRules.forEach(rule ->
                log.debug("  - [{}] channel={}, mti={}, processingCode={}, processKey={}, priority={}",
                        rule.getName(),
                        rule.getChannelPattern(),
                        rule.getMti(),
                        rule.getProcessingCode(),
                        rule.getProcessKey(),
                        rule.getPriority()));
        } else {
            sortedRules = List.of();
            log.info("流程路由服務已停用或無規則配置，將使用預設流程: {}",
                    properties.getDefaultProcess());
        }
    }

    /**
     * 解析流程 Key
     *
     * @param channelId 通道 ID (e.g., "ATM_FISC_V1")
     * @param mti MTI (e.g., "0200", "2500")
     * @param processingCode Processing Code (e.g., "400000")
     * @return 對應的 BPMN 流程 Key
     */
    public String resolveProcessKey(String channelId, String mti, String processingCode) {
        if (!properties.isEnabled()) {
            log.debug("流程路由已停用，使用預設流程: {}", properties.getDefaultProcess());
            return properties.getDefaultProcess();
        }

        Optional<RoutingRule> matchedRule = findMatchingRule(channelId, mti, processingCode);

        if (matchedRule.isPresent()) {
            RoutingRule rule = matchedRule.get();
            log.info("流程路由匹配成功: channel={}, mti={}, processingCode={} -> [{}] processKey={}",
                    channelId, mti, processingCode, rule.getName(), rule.getProcessKey());
            return rule.getProcessKey();
        }

        log.info("無匹配規則，使用預設流程: channel={}, mti={}, processingCode={} -> {}",
                channelId, mti, processingCode, properties.getDefaultProcess());
        return properties.getDefaultProcess();
    }

    /**
     * 尋找匹配的規則
     */
    private Optional<RoutingRule> findMatchingRule(String channelId, String mti, String processingCode) {
        return sortedRules.stream()
                .filter(rule -> matchesRule(rule, channelId, mti, processingCode))
                .findFirst();
    }

    /**
     * 檢查規則是否匹配
     */
    private boolean matchesRule(RoutingRule rule, String channelId, String mti, String processingCode) {
        // 1. 檢查 MTI（必要條件）
        if (rule.getMti() == null || !rule.getMti().equals(mti)) {
            return false;
        }

        // 2. 檢查通道模式
        if (rule.getChannelPattern() != null && !matchesPattern(rule.getChannelPattern(), channelId)) {
            return false;
        }

        // 3. 檢查 Processing Code（可選條件）
        if (rule.getProcessingCode() != null && processingCode != null) {
            if (!processingCode.startsWith(rule.getProcessingCode())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 正則表達式匹配
     */
    private boolean matchesPattern(String pattern, String value) {
        if (pattern == null || value == null) {
            return pattern == null;
        }

        try {
            return Pattern.matches(pattern, value);
        } catch (PatternSyntaxException e) {
            log.warn("無效的正則表達式: pattern={}, error={}", pattern, e.getMessage());
            return pattern.equals(value);
        }
    }

    /**
     * 取得預設流程 Key
     */
    public String getDefaultProcessKey() {
        return properties.getDefaultProcess();
    }

    /**
     * 檢查路由是否啟用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 取得已載入的規則數量
     */
    public int getRuleCount() {
        return sortedRules != null ? sortedRules.size() : 0;
    }
}
