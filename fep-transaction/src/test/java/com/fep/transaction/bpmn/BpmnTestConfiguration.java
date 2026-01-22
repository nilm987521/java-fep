package com.fep.transaction.bpmn;

import com.fep.transaction.bpmn.config.ProcessRoutingProperties;
import com.fep.transaction.redis.service.DistributedLockService;
import com.fep.transaction.redis.service.PendingTransactionRedisService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for BPMN workflow integration tests.
 * Provides a minimal Spring Boot context with Camunda BPM.
 *
 * <p>高 TPS 架構需要 Mock Redis 服務，因為測試環境不連接真實 Redis。
 */
@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@EnableProcessApplication
@EnableConfigurationProperties(ProcessRoutingProperties.class)
@ComponentScan(basePackages = {
    "com.fep.transaction.bpmn"
})
public class BpmnTestConfiguration {

    // Camunda services are auto-configured by camunda-bpm-spring-boot-starter
    // No additional bean definitions needed for basic tests

    /**
     * Mock PendingTransactionRedisService for testing
     */
    @Bean
    @Primary
    public PendingTransactionRedisService pendingTransactionRedisService() {
        return mock(PendingTransactionRedisService.class);
    }

    /**
     * Mock DistributedLockService for testing
     */
    @Bean
    @Primary
    public DistributedLockService distributedLockService() {
        return mock(DistributedLockService.class);
    }
}
