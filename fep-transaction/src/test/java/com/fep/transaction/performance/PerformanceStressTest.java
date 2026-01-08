package com.fep.transaction.performance;

import com.fep.transaction.config.TransactionModule;
import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 效能壓力測試類別
 * 測試系統是否能達到 2000+ TPS 的效能目標
 *
 * 使用 @Tag("performance") 標記，避免在一般測試中執行
 * 執行方式：mvn test -Dgroups=performance
 */
@Tag("performance")
@DisplayName("Performance Stress Tests - 2000+ TPS Target")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceStressTest {

    private TransactionModule module;
    private TransactionService transactionService;

    // Control flag for sustained and mixed load tests
    private volatile boolean running;

    // 效能測試統計資料
    private static final class PerformanceMetrics {
        final AtomicLong totalTransactions = new AtomicLong(0);
        final AtomicLong successfulTransactions = new AtomicLong(0);
        final AtomicLong failedTransactions = new AtomicLong(0);
        final List<Long> responseTimes = new CopyOnWriteArrayList<>();
        final AtomicLong totalProcessingTime = new AtomicLong(0);

        void recordTransaction(boolean success, long processingTimeMs) {
            totalTransactions.incrementAndGet();
            if (success) {
                successfulTransactions.incrementAndGet();
            } else {
                failedTransactions.incrementAndGet();
            }
            responseTimes.add(processingTimeMs);
            totalProcessingTime.addAndGet(processingTimeMs);
        }

        double getTps(long durationMs) {
            if (durationMs == 0) return 0.0;
            return (totalTransactions.get() * 1000.0) / durationMs;
        }

        double getSuccessRate() {
            long total = totalTransactions.get();
            if (total == 0) return 0.0;
            return (successfulTransactions.get() * 100.0) / total;
        }

        double getAverageResponseTime() {
            if (responseTimes.isEmpty()) return 0.0;
            return responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        long getPercentile(double percentile) {
            if (responseTimes.isEmpty()) return 0L;
            List<Long> sorted = new ArrayList<>(responseTimes);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }

        long getMinResponseTime() {
            return responseTimes.stream().mapToLong(Long::longValue).min().orElse(0L);
        }

        long getMaxResponseTime() {
            return responseTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
        }
    }

    @BeforeEach
    void setUp() {
        // 建立完整配置的模組
        module = TransactionModuleConfig.moduleBuilder()
                .batchParallelism(20) // 增加批次並行度以支援高 TPS
                .build();
        transactionService = module.getTransactionService();
    }

    @AfterEach
    void tearDown() {
        if (module != null) {
            module.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. TPS 壓力測試 - 目標 2000+ TPS (10 秒測試)")
    void testTpsStress_2000Plus() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("TPS 壓力測試 - 目標 2000+ TPS");
        System.out.println("========================================");

        int numberOfThreads = 50; // 並發執行緒數
        int transactionsPerThread = 400; // 每個執行緒執行的交易數
        int totalTransactions = numberOfThreads * transactionsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        PerformanceMetrics metrics = new PerformanceMetrics();

        System.out.println("配置：");
        System.out.println("  - 並發執行緒數: " + numberOfThreads);
        System.out.println("  - 每執行緒交易數: " + transactionsPerThread);
        System.out.println("  - 總交易數: " + totalTransactions);
        System.out.println("\n開始執行壓力測試...\n");

        Instant startTime = Instant.now();

        // 建立並啟動所有執行緒
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 等待所有執行緒準備就緒
                    startLatch.await();

                    // 執行交易
                    for (int j = 0; j < transactionsPerThread; j++) {
                        String txnId = String.format("TPS-T%02d-TXN%04d", threadId, j);
                        TransactionRequest request = createWithdrawalRequest(txnId, new BigDecimal("1000"));

                        long txnStart = System.currentTimeMillis();
                        TransactionResponse response = transactionService.process(request);
                        long txnEnd = System.currentTimeMillis();

                        metrics.recordTransaction(response.isApproved(), txnEnd - txnStart);
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " error: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 同時啟動所有執行緒
        startLatch.countDown();

        // 等待所有執行緒完成
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long durationMs = Duration.between(startTime, endTime).toMillis();

        // 輸出效能報告
        printPerformanceReport(metrics, durationMs);

        // 驗證效能目標
        assertTrue(completed, "測試應在 30 秒內完成");
        double actualTps = metrics.getTps(durationMs);
        System.out.println("\n效能驗證：");
        System.out.println("  目標 TPS: >= 2000");
        System.out.println("  實際 TPS: " + String.format("%.2f", actualTps));

        assertTrue(actualTps >= 2000,
                String.format("TPS 應達到 2000 以上，實際為 %.2f", actualTps));
        assertTrue(metrics.getSuccessRate() >= 95.0,
                String.format("成功率應達到 95%% 以上，實際為 %.2f%%", metrics.getSuccessRate()));
    }

    @Test
    @Order(2)
    @DisplayName("2. 延遲測試 - P50, P95, P99 延遲分析")
    void testLatencyAnalysis() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("延遲測試 - P50, P95, P99 延遲分析");
        System.out.println("========================================");

        int numberOfThreads = 30;
        int transactionsPerThread = 200;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        PerformanceMetrics metrics = new PerformanceMetrics();

        System.out.println("執行 " + (numberOfThreads * transactionsPerThread) + " 筆交易進行延遲分析...\n");

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < transactionsPerThread; j++) {
                        String txnId = String.format("LAT-T%02d-TXN%04d", threadId, j);

                        // 混合不同類型的交易
                        TransactionRequest request;
                        if (j % 3 == 0) {
                            request = createBalanceInquiryRequest(txnId);
                        } else if (j % 3 == 1) {
                            request = createWithdrawalRequest(txnId, new BigDecimal("2000"));
                        } else {
                            request = createTransferRequest(txnId, new BigDecimal("5000"));
                        }

                        long txnStart = System.currentTimeMillis();
                        TransactionResponse response = transactionService.process(request);
                        long txnEnd = System.currentTimeMillis();

                        metrics.recordTransaction(response.isApproved(), txnEnd - txnStart);
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " error: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(completed, "延遲測試應在 30 秒內完成");

        // 延遲分析報告
        System.out.println("延遲分析結果：");
        System.out.println("  - 最小延遲: " + metrics.getMinResponseTime() + " ms");
        System.out.println("  - 平均延遲: " + String.format("%.2f", metrics.getAverageResponseTime()) + " ms");
        System.out.println("  - P50 延遲: " + metrics.getPercentile(50) + " ms");
        System.out.println("  - P95 延遲: " + metrics.getPercentile(95) + " ms");
        System.out.println("  - P99 延遲: " + metrics.getPercentile(99) + " ms");
        System.out.println("  - 最大延遲: " + metrics.getMaxResponseTime() + " ms");
        System.out.println("  - 總交易數: " + metrics.totalTransactions.get());
        System.out.println("  - 成功率: " + String.format("%.2f%%", metrics.getSuccessRate()));

        // 驗證延遲目標（依據需求文件：<500ms）
        long p95Latency = metrics.getPercentile(95);
        long p99Latency = metrics.getPercentile(99);

        System.out.println("\n延遲目標驗證（目標：P95 < 500ms）：");
        System.out.println("  P95: " + p95Latency + " ms " + (p95Latency < 500 ? "✓ 通過" : "✗ 未通過"));
        System.out.println("  P99: " + p99Latency + " ms " + (p99Latency < 1000 ? "✓ 通過" : "✗ 未通過"));

        assertTrue(p95Latency < 500,
                String.format("P95 延遲應小於 500ms，實際為 %d ms", p95Latency));
        assertTrue(p99Latency < 1000,
                String.format("P99 延遲應小於 1000ms，實際為 %d ms", p99Latency));
    }

    @Test
    @Order(3)
    @DisplayName("3. 持續負載測試 - 30 秒持續壓力")
    void testSustainedLoad() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("持續負載測試 - 30 秒持續壓力");
        System.out.println("========================================");

        int numberOfThreads = 40;
        int testDurationSeconds = 30;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger txnCounter = new AtomicInteger(0);
        PerformanceMetrics metrics = new PerformanceMetrics();

        running = true;

        System.out.println("配置：");
        System.out.println("  - 並發執行緒數: " + numberOfThreads);
        System.out.println("  - 測試時長: " + testDurationSeconds + " 秒");
        System.out.println("\n開始持續負載測試...\n");

        Instant startTime = Instant.now();

        // 建立持續執行的執行緒
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                while (running) {
                    try {
                        int txnNum = txnCounter.incrementAndGet();
                        String txnId = String.format("SUSTAIN-T%02d-TXN%06d", threadId, txnNum);

                        // 混合不同交易類型
                        TransactionRequest request;
                        int type = txnNum % 4;
                        switch (type) {
                            case 0:
                                request = createBalanceInquiryRequest(txnId);
                                break;
                            case 1:
                                request = createWithdrawalRequest(txnId, new BigDecimal("3000"));
                                break;
                            case 2:
                                request = createTransferRequest(txnId, new BigDecimal("8000"));
                                break;
                            default:
                                request = createDepositRequest(txnId, new BigDecimal("5000"));
                                break;
                        }

                        long txnStart = System.currentTimeMillis();
                        TransactionResponse response = transactionService.process(request);
                        long txnEnd = System.currentTimeMillis();

                        metrics.recordTransaction(response.isApproved(), txnEnd - txnStart);

                        // 模擬真實場景，短暫暫停
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // 記錄錯誤但繼續執行
                        metrics.failedTransactions.incrementAndGet();
                    }
                }
            });
            futures.add(future);
        }

        // 定期輸出統計資料
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            long elapsedMs = Duration.between(startTime, Instant.now()).toMillis();
            double currentTps = metrics.getTps(elapsedMs);
            System.out.printf("  [%2ds] TPS: %.2f | 完成: %d | 成功率: %.2f%%\n",
                    elapsedMs / 1000, currentTps, metrics.totalTransactions.get(), metrics.getSuccessRate());
        }, 5, 5, TimeUnit.SECONDS);

        // 執行指定時間
        Thread.sleep(testDurationSeconds * 1000L);
        running = false;

        // 等待所有執行緒完成
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 忽略
            }
        }

        scheduler.shutdown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        long actualDurationMs = Duration.between(startTime, endTime).toMillis();

        // 輸出最終報告
        System.out.println("\n持續負載測試結果：");
        printPerformanceReport(metrics, actualDurationMs);

        // 驗證持續負載下的效能
        double avgTps = metrics.getTps(actualDurationMs);
        System.out.println("\n效能驗證：");
        System.out.println("  - 平均 TPS: " + String.format("%.2f", avgTps));
        System.out.println("  - 目標 TPS: >= 1500 (持續負載)");

        assertTrue(avgTps >= 1500,
                String.format("持續負載下 TPS 應達到 1500 以上，實際為 %.2f", avgTps));
        assertTrue(metrics.getSuccessRate() >= 90.0,
                String.format("持續負載下成功率應達到 90%% 以上，實際為 %.2f%%", metrics.getSuccessRate()));
    }

    @Test
    @Order(4)
    @DisplayName("4. 尖峰負載測試 - 模擬突發大量請求")
    void testBurstLoad() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("尖峰負載測試 - 模擬突發大量請求");
        System.out.println("========================================");

        int burstThreads = 100; // 突發時的執行緒數
        int transactionsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(burstThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(burstThreads);

        PerformanceMetrics metrics = new PerformanceMetrics();

        System.out.println("配置：");
        System.out.println("  - 突發執行緒數: " + burstThreads);
        System.out.println("  - 每執行緒交易數: " + transactionsPerThread);
        System.out.println("  - 突發交易總數: " + (burstThreads * transactionsPerThread));
        System.out.println("\n模擬突發大量請求...\n");

        Instant startTime = Instant.now();

        for (int i = 0; i < burstThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < transactionsPerThread; j++) {
                        String txnId = String.format("BURST-T%03d-TXN%04d", threadId, j);

                        // 模擬真實突發場景：主要是查詢和小額交易
                        TransactionRequest request;
                        if (j % 2 == 0) {
                            request = createBalanceInquiryRequest(txnId);
                        } else {
                            request = createWithdrawalRequest(txnId, new BigDecimal("1000"));
                        }

                        long txnStart = System.currentTimeMillis();
                        TransactionResponse response = transactionService.process(request);
                        long txnEnd = System.currentTimeMillis();

                        metrics.recordTransaction(response.isApproved(), txnEnd - txnStart);
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " error: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 觸發突發負載
        startLatch.countDown();

        // 等待所有交易完成
        boolean completed = endLatch.await(45, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long durationMs = Duration.between(startTime, endTime).toMillis();

        System.out.println("尖峰負載測試結果：");
        printPerformanceReport(metrics, durationMs);

        assertTrue(completed, "突發負載測試應在 45 秒內完成");

        double burstTps = metrics.getTps(durationMs);
        long p95Latency = metrics.getPercentile(95);

        System.out.println("\n尖峰負載驗證：");
        System.out.println("  - 尖峰 TPS: " + String.format("%.2f", burstTps));
        System.out.println("  - P95 延遲: " + p95Latency + " ms");
        System.out.println("  - 成功率: " + String.format("%.2f%%", metrics.getSuccessRate()));

        // 尖峰負載下的效能標準可以稍微放寬
        assertTrue(burstTps >= 1000,
                String.format("尖峰負載下 TPS 應達到 1000 以上，實際為 %.2f", burstTps));
        assertTrue(metrics.getSuccessRate() >= 85.0,
                String.format("尖峰負載下成功率應達到 85%% 以上，實際為 %.2f%%", metrics.getSuccessRate()));
        assertTrue(p95Latency < 1000,
                String.format("尖峰負載下 P95 延遲應小於 1000ms，實際為 %d ms", p95Latency));
    }

    @Test
    @Order(5)
    @DisplayName("5. 綜合效能測試 - 混合交易類型與負載")
    void testMixedWorkload() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("綜合效能測試 - 混合交易類型與負載");
        System.out.println("========================================");

        int numberOfThreads = 60;
        int testDurationSeconds = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger txnCounter = new AtomicInteger(0);
        PerformanceMetrics metrics = new PerformanceMetrics();

        // 各類交易的計數器
        AtomicInteger withdrawalCount = new AtomicInteger(0);
        AtomicInteger transferCount = new AtomicInteger(0);
        AtomicInteger inquiryCount = new AtomicInteger(0);
        AtomicInteger depositCount = new AtomicInteger(0);

        running = true;

        System.out.println("配置：");
        System.out.println("  - 執行緒數: " + numberOfThreads);
        System.out.println("  - 測試時長: " + testDurationSeconds + " 秒");
        System.out.println("  - 交易類型: 提款、轉帳、查詢、存款（隨機混合）");
        System.out.println("\n開始綜合效能測試...\n");

        Instant startTime = Instant.now();
        Random random = new Random();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                while (running) {
                    try {
                        int txnNum = txnCounter.incrementAndGet();
                        String txnId = String.format("MIX-T%02d-TXN%06d", threadId, txnNum);

                        // 依照真實交易分佈比例（查詢 40%, 提款 30%, 轉帳 20%, 存款 10%）
                        int rand = random.nextInt(100);
                        TransactionRequest request;

                        if (rand < 40) {
                            request = createBalanceInquiryRequest(txnId);
                            inquiryCount.incrementAndGet();
                        } else if (rand < 70) {
                            // Withdrawal: must be multiple of 100 and <= 20000
                            int withdrawAmount = (10 + random.nextInt(190)) * 100; // 1000-19900
                            request = createWithdrawalRequest(txnId,
                                    new BigDecimal(withdrawAmount));
                            withdrawalCount.incrementAndGet();
                        } else if (rand < 90) {
                            // Transfer: reasonable amount range
                            int transferAmount = (10 + random.nextInt(100)) * 100; // 1000-10900
                            request = createTransferRequest(txnId,
                                    new BigDecimal(transferAmount));
                            transferCount.incrementAndGet();
                        } else {
                            // Deposit: reasonable amount range
                            int depositAmount = (10 + random.nextInt(100)) * 100; // 1000-10900
                            request = createDepositRequest(txnId,
                                    new BigDecimal(depositAmount));
                            depositCount.incrementAndGet();
                        }

                        long txnStart = System.currentTimeMillis();
                        TransactionResponse response = transactionService.process(request);
                        long txnEnd = System.currentTimeMillis();

                        metrics.recordTransaction(response.isApproved(), txnEnd - txnStart);

                        // 模擬真實場景間隔
                        Thread.sleep(random.nextInt(3));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        metrics.failedTransactions.incrementAndGet();
                    }
                }
            });
            futures.add(future);
        }

        // 執行指定時間
        Thread.sleep(testDurationSeconds * 1000L);
        running = false;

        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 忽略
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        long actualDurationMs = Duration.between(startTime, endTime).toMillis();

        // 輸出測試結果
        System.out.println("綜合效能測試結果：");
        System.out.println("\n交易類型分佈：");
        System.out.println("  - 餘額查詢: " + inquiryCount.get() + " 筆");
        System.out.println("  - 提款交易: " + withdrawalCount.get() + " 筆");
        System.out.println("  - 轉帳交易: " + transferCount.get() + " 筆");
        System.out.println("  - 存款交易: " + depositCount.get() + " 筆");

        printPerformanceReport(metrics, actualDurationMs);

        // 驗證綜合效能
        double avgTps = metrics.getTps(actualDurationMs);
        System.out.println("\n綜合效能驗證：");
        System.out.println("  - 平均 TPS: " + String.format("%.2f", avgTps));

        assertTrue(avgTps >= 1800,
                String.format("混合負載下 TPS 應達到 1800 以上，實際為 %.2f", avgTps));
        assertTrue(metrics.getSuccessRate() >= 92.0,
                String.format("混合負載下成功率應達到 92%% 以上，實際為 %.2f%%", metrics.getSuccessRate()));
    }

    // ==================== Helper Methods ====================

    private void printPerformanceReport(PerformanceMetrics metrics, long durationMs) {
        System.out.println("\n效能統計報告：");
        System.out.println("  - 測試時長: " + String.format("%.2f", durationMs / 1000.0) + " 秒");
        System.out.println("  - 總交易數: " + metrics.totalTransactions.get());
        System.out.println("  - 成功交易: " + metrics.successfulTransactions.get());
        System.out.println("  - 失敗交易: " + metrics.failedTransactions.get());
        System.out.println("  - 成功率: " + String.format("%.2f%%", metrics.getSuccessRate()));
        System.out.println("  - 平均 TPS: " + String.format("%.2f", metrics.getTps(durationMs)));
        System.out.println("\n延遲統計：");
        System.out.println("  - 最小延遲: " + metrics.getMinResponseTime() + " ms");
        System.out.println("  - 平均延遲: " + String.format("%.2f", metrics.getAverageResponseTime()) + " ms");
        System.out.println("  - P50 延遲: " + metrics.getPercentile(50) + " ms");
        System.out.println("  - P95 延遲: " + metrics.getPercentile(95) + " ms");
        System.out.println("  - P99 延遲: " + metrics.getPercentile(99) + " ms");
        System.out.println("  - 最大延遲: " + metrics.getMaxResponseTime() + " ms");
    }

    private TransactionRequest createWithdrawalRequest(String txnId, BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("010000")
                .pan("4111111111111111")
                .amount(amount)
                .currencyCode("901")
                .sourceAccount(generateRandomAccount())
                .terminalId("ATM" + String.format("%05d", txnId.hashCode() % 100000))
                .acquiringBankCode("004")
                .stan(generateStan())
                .rrn(generateRrn())
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createTransferRequest(String txnId, BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.TRANSFER)
                .processingCode("400000")
                .pan("4111111111111111")
                .amount(amount)
                .currencyCode("901")
                .sourceAccount(generateRandomAccount())
                .destinationAccount(generateRandomAccount())
                .destinationBankCode("012")
                .terminalId("ATM" + String.format("%05d", txnId.hashCode() % 100000))
                .acquiringBankCode("004")
                .stan(generateStan())
                .rrn(generateRrn())
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createBalanceInquiryRequest(String txnId) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("310000")
                .pan("4111111111111111")
                .currencyCode("901")
                .sourceAccount(generateRandomAccount())
                .terminalId("ATM" + String.format("%05d", txnId.hashCode() % 100000))
                .acquiringBankCode("004")
                .stan(generateStan())
                .rrn(generateRrn())
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createDepositRequest(String txnId, BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.DEPOSIT)
                .processingCode("210000")
                .pan("4111111111111111")
                .amount(amount)
                .currencyCode("901")
                .sourceAccount(generateRandomAccount())
                .terminalId("ATM" + String.format("%05d", txnId.hashCode() % 100000))
                .acquiringBankCode("004")
                .stan(generateStan())
                .rrn(generateRrn())
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private String generateRandomAccount() {
        return String.format("%014d", ThreadLocalRandom.current().nextLong(10000000000000L, 99999999999999L));
    }

    private String generateStan() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1, 999999));
    }

    private String generateRrn() {
        return String.format("%012d", ThreadLocalRandom.current().nextLong(100000000000L, 999999999999L));
    }
}
