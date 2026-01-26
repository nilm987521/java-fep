package com.fep.message.transform;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Field Mapping 配置
 *
 * <p>從 field-mappings.yml 讀取通道欄位映射配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "")
public class FieldMappingConfig {

    /**
     * 全域設定
     */
    private GlobalConfig global = new GlobalConfig();

    /**
     * 通道映射定義
     */
    private Map<String, ChannelMapping> channelMappings = new HashMap<>();

    /**
     * 轉換類型定義
     */
    private Map<String, TransformationType> transformationTypes = new HashMap<>();

    /**
     * 取得指定通道的映射配置
     */
    public ChannelMapping getChannelMapping(String channelId) {
        return channelMappings.get(channelId);
    }

    /**
     * 是否有指定通道的映射
     */
    public boolean hasChannelMapping(String channelId) {
        return channelMappings.containsKey(channelId);
    }

    /**
     * 全域設定
     */
    @Data
    public static class GlobalConfig {
        /**
         * 內部 Schema 版本
         */
        private String internalSchemaVersion = "FEP_Internal_V1";

        /**
         * 嚴格模式
         */
        private boolean strictMode = false;

        /**
         * 預設值
         */
        private Map<String, String> defaults = new HashMap<>();
    }

    /**
     * 通道映射
     */
    @Data
    public static class ChannelMapping {
        /**
         * 描述
         */
        private String description;

        /**
         * Schema 名稱
         */
        private String schemaName;

        /**
         * 外部 → 內部映射
         */
        private Map<String, String> toInternal = new HashMap<>();

        /**
         * 內部 → 外部映射
         */
        private Map<String, String> fromInternal = new HashMap<>();

        /**
         * 轉換規則
         */
        private List<Transformation> transformations;
    }

    /**
     * 欄位轉換規則
     */
    @Data
    public static class Transformation {
        /**
         * 欄位名稱
         */
        private String field;

        /**
         * 轉換類型
         */
        private String type;

        /**
         * 轉換方向 (toInternal, fromInternal, both)
         */
        private String direction = "both";

        /**
         * 轉換參數
         */
        private Map<String, Object> params = new HashMap<>();
    }

    /**
     * 轉換類型定義
     */
    @Data
    public static class TransformationType {
        /**
         * 描述
         */
        private String description;

        /**
         * 是否雙向
         */
        private boolean bidirectional;

        /**
         * 參數定義
         */
        private List<ParamDef> params;
    }

    /**
     * 參數定義
     */
    @Data
    public static class ParamDef {
        private String name;
        private String type;
        private boolean required;
        private String defaultValue;
    }
}
