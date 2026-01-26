package com.fep.message.transform;

import com.fep.message.channel.ChannelSchemaRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MessageTransformer 配置
 *
 * <p>建立 MessageTransformer Bean，用於 GenericMessage ↔ InternalMessage 的轉換。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MessageTransformerConfig {

    private final FieldMappingConfig fieldMappingConfig;
    private final ChannelSchemaRegistry schemaRegistry;

    /**
     * 建立 MessageTransformer Bean
     *
     * <p>DefaultMessageTransformer 使用配置驅動的欄位映射，
     * 根據 field-mappings.yml 進行轉換。
     */
    @Bean
    public MessageTransformer messageTransformer() {
        log.info("建立 MessageTransformer Bean");
        return new DefaultMessageTransformer(fieldMappingConfig, schemaRegistry);
    }
}
