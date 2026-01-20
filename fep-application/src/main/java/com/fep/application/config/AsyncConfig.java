package com.fep.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 非同步執行緒池配置
 *
 * <p>替代 Spring 預設的 SimpleAsyncTaskExecutor（每次都建立新執行緒），
 * 使用 ThreadPoolTaskExecutor 提供執行緒池管理，大幅提升 TPS 效能。
 *
 * <p>配置說明：
 * <ul>
 *   <li>corePoolSize: 核心執行緒數，保持活躍處理請求</li>
 *   <li>maxPoolSize: 最大執行緒數，高峰期擴展</li>
 *   <li>queueCapacity: 等待佇列容量，緩衝突發流量</li>
 *   <li>keepAliveSeconds: 閒置執行緒存活時間</li>
 * </ul>
 *
 * <p>效能影響：
 * <ul>
 *   <li>避免頻繁建立/銷毀執行緒的開銷</li>
 *   <li>減少 context switching</li>
 *   <li>提供背壓機制保護系統</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 交易處理執行緒池
     *
     * <p>用於處理 @Async 標註的方法，主要是 TransactionEventListener
     */
    @Bean(name = "transactionExecutor")
    public ThreadPoolTaskExecutor transactionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心執行緒數：CPU 核心數 * 2（適合 I/O 密集型）
        int coreCount = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(coreCount * 2);

        // 最大執行緒數：核心數 * 4（高峰期擴展）
        executor.setMaxPoolSize(coreCount * 4);

        // 等待佇列容量：緩衝突發流量
        executor.setQueueCapacity(5000);

        // 閒置執行緒存活時間（秒）
        executor.setKeepAliveSeconds(60);

        // 執行緒名稱前綴（方便日誌追蹤）
        executor.setThreadNamePrefix("txn-");

        // 拒絕策略：由呼叫者執行緒執行（背壓機制）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任務完成後再關閉
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 允許核心執行緒超時（節省資源）
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("Transaction executor initialized: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 5000);

        return executor;
    }

    /**
     * BPMN 流程執行緒池
     *
     * <p>專門用於 BPMN 流程啟動和訊息關聯
     */
    @Bean(name = "bpmnExecutor")
    public ThreadPoolTaskExecutor bpmnExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int coreCount = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(coreCount * 2);
        executor.setMaxPoolSize(coreCount * 4);
        executor.setQueueCapacity(3000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("bpmn-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("BPMN executor initialized: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 3000);

        return executor;
    }

    /**
     * 預設非同步執行器
     *
     * <p>所有未指定執行器的 @Async 方法使用此執行器
     */
    @Override
    public Executor getAsyncExecutor() {
        return transactionExecutor();
    }

    /**
     * 非同步異常處理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async method {} threw exception: {}",
                    method.getName(), throwable.getMessage(), throwable);
        };
    }
}
