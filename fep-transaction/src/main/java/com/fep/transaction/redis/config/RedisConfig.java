package com.fep.transaction.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置類
 *
 * <p>配置 Redis 連線和序列化方式，支援高 TPS 交易處理架構。
 *
 * <p>主要用途：
 * <ul>
 *   <li>Pending Transaction ZSET - timeout 掃描</li>
 *   <li>Transaction HASH - 交易詳情快取</li>
 *   <li>Distributed Lock - 分散式鎖</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "fep.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * 配置通用 RedisTemplate
     *
     * <p>使用 String 作為 key，JSON 作為 value 的序列化方式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON 序列化
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 StringRedisTemplate
     *
     * <p>專門用於 ZSET 和簡單字串操作
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
