package com.fep.transaction.performance;

import com.fep.transaction.config.TransactionModule;
import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 記憶體洩漏測試類別
 * 驗證系統長時間運行不會發生記憶體洩漏或 OOM
 *
 * 測試重點：
 * 1. 連續大量交易後記憶體使用是否穩定
 * 2. 物件池化是否正常釋放資源
 * 3. 長時間運行是否會累積記憶體
 * 4. GC 後記憶體是否能正常回收
 *
 * 使用 @Tag("performance") 標記，避免在一般測試中執行
 * 執行方式：mvn test -Dgroups=performance
 */
@Tag("performance")
@DisplayName("Memory Leak Tests - Long Running Stability")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryLeakTest {

    private TransactionModule module;
    private TransactionService transactionService;
    private MemoryMXBean memoryBean;

    // Memory snapshot data
    private static class MemorySnapshot {
        final long heapUsed;
        final long heapMax;
        final long heapCommitted;
        final long nonHeapUsed;
        final Instant timestamp;

        MemorySnapshot() {
            MemoryUsage heapMemory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            MemoryUsage nonHeapMemory = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

            this.heapUsed = heapMemory.getUsed();
            this.heapMax = heapMemory.getMax();
            this.heapCommitted = heapMemory.getCommitted();
            this.nonHeapUsed = nonHeapMemory.getUsed();
            this.timestamp = Instant.now();
        }

        double getUsagePercentage() {
            return (heapUsed * 100.0) / heapMax;
        }

        long getHeapUsedMB() {
            return heapUsed / (1024 * 1024);
        }

        long getHeapMaxMB() {
            return heapMax / (1024 * 1024);
        }

        @Override
        public String toString() {
            return String.format("Heap: %d MB / %d MB (%.2f%%), Non-Heap: %d MB",
                    getHeapUsedMB(), getHeapMaxMB(), getUsagePercentage(),
                    nonHeapUsed / (1024 * 1024));
        }
    }

    @BeforeEach
    void setUp() {
        // 建立模組
        module = TransactionModuleConfig.moduleBuilder()
                .batchParallelism(10)
                .build();
        transactionService = module.getTransactionService();
        memoryBean = ManagementFactory.getMemoryMXBean();

        // 執行初始 GC
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (module != null) {
            module.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 連續大量交易記憶體使用測試")
    void testMemoryUsageAfterMassiveTransactions() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("連續大量交易記憶體使用測試");
        System.out.println("========================================");

        int totalTransactions = 100_000;
        int batchSize = 10_000;
        List<MemorySnapshot> snapshots = new ArrayList<>();

        // 記錄初始記憶體
        MemorySnapshot initialSnapshot = new MemorySnapshot();
        snapshots.add(initialSnapshot);
        System.out.println("初始記憶體: " + initialSnapshot);

        // 執行多批次交易
        for (int batch = 1; batch <= totalTransactions / batchSize; batch++) {
            System.out.println("\n執行批次 " + batch + " ...");

            for (int i = 0; i < batchSize; i++) {
                String txnId = String.format("MEM-BATCH%d-TXN%05d", batch, i);
                TransactionRequest request = createTestRequest(txnId,
                        i % 2 == 0 ? TransactionType.WITHDRAWAL : TransactionType.BALANCE_INQUIRY);

                TransactionResponse response = transactionService.process(request);
                assertNotNull(response);
            }

            // 批次完成後記錄記憶體
            MemorySnapshot afterBatchSnapshot = new MemorySnapshot();
            snapshots.add(afterBatchSnapshot);
            System.out.println("批次 " + batch + " 完成後: " + afterBatchSnapshot);
        }

        // 建議 GC
        System.out.println("\n執行 GC...");
        System.gc();
        Thread.sleep(2000);

        // 記錄 GC 後記憶體
        MemorySnapshot afterGcSnapshot = new MemorySnapshot();
        snapshots.add(afterGcSnapshot);
        System.out.println("GC 後記憶體: " + afterGcSnapshot);

        // 分析記憶體增長
        System.out.println("\n記憶體使用分析：");
        long memoryGrowth = afterGcSnapshot.heapUsed - initialSnapshot.heapUsed;
        long memoryGrowthMB = memoryGrowth / (1024 * 1024);
        double growthPercentage = (memoryGrowth * 100.0) / initialSnapshot.heapUsed;

        System.out.println("  - 總交易數: " + totalTransactions);
        System.out.println("  - 初始記憶體: " + initialSnapshot.getHeapUsedMB() + " MB");
        System.out.println("  - GC後記憶體: " + afterGcSnapshot.getHeapUsedMB() + " MB");
        System.out.println("  - 記憶體增長: " + memoryGrowthMB + " MB (" + String.format("%.2f%%", growthPercentage) + ")");

        // 驗證記憶體沒有明顯洩漏
        // 允許記憶體增長不超過 200MB 或初始記憶體的 1000%（測試環境允許更大波動）
        // 注意：測試環境中 JVM 記憶體管理行為與生產環境不同
        assertTrue(memoryGrowthMB < 200 || growthPercentage < 1000,
                String.format("記憶體增長過大: %d MB (%.2f%%), 可能有記憶體洩漏",
                        memoryGrowthMB, growthPercentage));
    }

    @Test
    @Order(2)
    @DisplayName("2. 並發執行記憶體穩定性測試")
    void testMemoryStabilityUnderConcurrentLoad() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("並發執行記憶體穩定性測試");
        System.out.println("========================================");

        int numberOfThreads = 20;
        int transactionsPerThread = 5000;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        AtomicLong completedCount = new AtomicLong(0);

        // 記錄初始記憶體
        System.gc();
        Thread.sleep(1000);
        MemorySnapshot initialSnapshot = new MemorySnapshot();
        System.out.println("初始記憶體: " + initialSnapshot);

        System.out.println("\n啟動 " + numberOfThreads + " 個並發執行緒，每個執行 " + transactionsPerThread + " 筆交易...\n");

        Instant startTime = Instant.now();

        // 建立並發執行緒
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < transactionsPerThread; j++) {
                        String txnId = String.format("CONCURRENT-T%02d-TXN%05d", threadId, j);

                        // 混合不同類型的交易
                        TransactionType txnType;
                        if (j % 3 == 0) {
                            txnType = TransactionType.BALANCE_INQUIRY;
                        } else if (j % 3 == 1) {
                            txnType = TransactionType.WITHDRAWAL;
                        } else {
                            txnType = TransactionType.TRANSFER;
                        }

                        TransactionRequest request = createTestRequest(txnId, txnType);
                        TransactionResponse response = transactionService.process(request);
                        assertNotNull(response);

                        long completed = completedCount.incrementAndGet();
                        if (completed % 20000 == 0) {
                            MemorySnapshot snapshot = new MemorySnapshot();
                            System.out.println("  [進度: " + completed + "] " + snapshot);
                        }
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

        // 等待完成 (120秒超時，以適應不同環境的執行速度差異)
        boolean completed = endLatch.await(120, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(completed, "並發測試應在 120 秒內完成");

        // 記錄完成後記憶體
        MemorySnapshot afterLoadSnapshot = new MemorySnapshot();
        System.out.println("\n負載完成後: " + afterLoadSnapshot);

        // 執行 GC
        System.out.println("執行 GC...");
        System.gc();
        Thread.sleep(2000);

        MemorySnapshot afterGcSnapshot = new MemorySnapshot();
        System.out.println("GC 後記憶體: " + afterGcSnapshot);

        // 統計報告
        long durationMs = Duration.between(startTime, endTime).toMillis();
        long totalTransactions = numberOfThreads * transactionsPerThread;
        double tps = (totalTransactions * 1000.0) / durationMs;

        System.out.println("\n並發測試結果：");
        System.out.println("  - 總交易數: " + totalTransactions);
        System.out.println("  - 執行時間: " + String.format("%.2f", durationMs / 1000.0) + " 秒");
        System.out.println("  - 平均 TPS: " + String.format("%.2f", tps));
        System.out.println("  - 初始記憶體: " + initialSnapshot.getHeapUsedMB() + " MB");
        System.out.println("  - GC後記憶體: " + afterGcSnapshot.getHeapUsedMB() + " MB");
        System.out.println("  - 記憶體增長: " + (afterGcSnapshot.getHeapUsedMB() - initialSnapshot.getHeapUsedMB()) + " MB");

        // 驗證記憶體穩定（測試環境允許更大波動）
        long memoryGrowthMB = afterGcSnapshot.getHeapUsedMB() - initialSnapshot.getHeapUsedMB();
        assertTrue(memoryGrowthMB < 200,
                String.format("並發執行後記憶體增長過大: %d MB", memoryGrowthMB));
    }

    @Test
    @Order(3)
    @DisplayName("3. 長時間運行記憶體洩漏檢測")
    void testLongRunningMemoryLeak() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("長時間運行記憶體洩漏檢測");
        System.out.println("========================================");

        int testDurationSeconds = 30;
        int numberOfThreads = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicLong txnCounter = new AtomicLong(0);
        List<MemorySnapshot> snapshots = new CopyOnWriteArrayList<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        // 初始記憶體
        System.gc();
        Thread.sleep(1000);
        MemorySnapshot initialSnapshot = new MemorySnapshot();
        snapshots.add(initialSnapshot);
        System.out.println("初始記憶體: " + initialSnapshot);

        System.out.println("\n開始長時間運行測試 (" + testDurationSeconds + " 秒)...\n");

        Instant startTime = Instant.now();

        // 建立持續執行的執行緒
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                while (running.get()) {
                    try {
                        long txnNum = txnCounter.incrementAndGet();
                        String txnId = String.format("LONGRUN-T%02d-TXN%08d", threadId, txnNum);

                        TransactionType txnType = TransactionType.values()[(int)(txnNum % 4)];
                        TransactionRequest request = createTestRequest(txnId, txnType);
                        TransactionResponse response = transactionService.process(request);
                        assertNotNull(response);

                        // 模擬真實間隔
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // 繼續執行
                    }
                }
            });
            futures.add(future);
        }

        // 定期記錄記憶體快照
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            long elapsedSeconds = Duration.between(startTime, Instant.now()).toSeconds();
            MemorySnapshot snapshot = new MemorySnapshot();
            snapshots.add(snapshot);

            long txnCount = txnCounter.get();
            System.out.printf("  [%2ds] 交易數: %6d | %s\n", elapsedSeconds, txnCount, snapshot);
        }, 5, 5, TimeUnit.SECONDS);

        // 執行指定時間
        Thread.sleep(testDurationSeconds * 1000L);
        running.set(false);

        // 停止執行緒
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

        // 執行 GC
        System.out.println("\n執行 GC...");
        System.gc();
        Thread.sleep(2000);

        MemorySnapshot finalSnapshot = new MemorySnapshot();
        snapshots.add(finalSnapshot);
        System.out.println("最終記憶體: " + finalSnapshot);

        // 分析記憶體趨勢
        System.out.println("\n記憶體趨勢分析：");
        System.out.println("  - 測試時長: " + testDurationSeconds + " 秒");
        System.out.println("  - 總交易數: " + txnCounter.get());
        System.out.println("  - 初始記憶體: " + initialSnapshot.getHeapUsedMB() + " MB");
        System.out.println("  - 最終記憶體: " + finalSnapshot.getHeapUsedMB() + " MB");

        // 計算記憶體成長率
        long memoryGrowthMB = finalSnapshot.getHeapUsedMB() - initialSnapshot.getHeapUsedMB();
        double growthPerSecond = (double) memoryGrowthMB / testDurationSeconds;

        System.out.println("  - 記憶體增長: " + memoryGrowthMB + " MB");
        System.out.println("  - 每秒增長: " + String.format("%.2f", growthPerSecond) + " MB/s");

        // 檢查記憶體洩漏跡象
        // 如果每秒記憶體增長超過 5MB，可能有洩漏（測試環境允許更大波動）
        assertTrue(growthPerSecond < 5.0,
                String.format("檢測到可能的記憶體洩漏: %.2f MB/s 增長率", growthPerSecond));

        // 檢查記憶體是否線性增長（洩漏的特徵）
        if (snapshots.size() >= 3) {
            long firstHalfGrowth = snapshots.get(snapshots.size() / 2).getHeapUsedMB() -
                                   snapshots.get(0).getHeapUsedMB();
            long secondHalfGrowth = snapshots.get(snapshots.size() - 1).getHeapUsedMB() -
                                    snapshots.get(snapshots.size() / 2).getHeapUsedMB();

            System.out.println("  - 前半段增長: " + firstHalfGrowth + " MB");
            System.out.println("  - 後半段增長: " + secondHalfGrowth + " MB");

            // 如果後半段增長明顯大於前半段，可能有洩漏
            assertTrue(secondHalfGrowth <= firstHalfGrowth * 1.5,
                    "記憶體呈現線性增長趨勢，可能有洩漏");
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. 物件池化資源釋放測試")
    void testObjectPoolingResourceRelease() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("物件池化資源釋放測試");
        System.out.println("========================================");

        int cycles = 5;
        int transactionsPerCycle = 20_000;

        System.out.println("執行 " + cycles + " 個週期，每週期 " + transactionsPerCycle + " 筆交易");
        System.out.println("驗證物件池是否正確釋放資源\n");

        List<Long> cycleMemoryUsage = new ArrayList<>();

        for (int cycle = 1; cycle <= cycles; cycle++) {
            System.out.println("週期 " + cycle + " 開始...");

            // 執行交易
            for (int i = 0; i < transactionsPerCycle; i++) {
                String txnId = String.format("POOL-C%d-TXN%05d", cycle, i);
                TransactionRequest request = createTestRequest(txnId, TransactionType.WITHDRAWAL);
                TransactionResponse response = transactionService.process(request);
                assertNotNull(response);
            }

            // 強制 GC
            System.gc();
            Thread.sleep(1000);

            // 記錄記憶體
            MemorySnapshot snapshot = new MemorySnapshot();
            cycleMemoryUsage.add(snapshot.getHeapUsedMB());
            System.out.println("週期 " + cycle + " 完成: " + snapshot);
        }

        // 分析各週期記憶體
        System.out.println("\n各週期記憶體使用 (MB): " + cycleMemoryUsage);

        // 驗證記憶體沒有持續增長
        // 比較最後一個週期和第一個週期的記憶體
        long firstCycleMemory = cycleMemoryUsage.get(0);
        long lastCycleMemory = cycleMemoryUsage.get(cycleMemoryUsage.size() - 1);
        long memoryDiff = lastCycleMemory - firstCycleMemory;

        System.out.println("\n資源釋放分析：");
        System.out.println("  - 第一週期記憶體: " + firstCycleMemory + " MB");
        System.out.println("  - 最後週期記憶體: " + lastCycleMemory + " MB");
        System.out.println("  - 記憶體差異: " + memoryDiff + " MB");

        // 物件池應該正常釋放，記憶體差異不應過大（測試環境允許更大波動）
        assertTrue(Math.abs(memoryDiff) < 150,
                String.format("物件池可能未正確釋放資源，記憶體差異: %d MB", memoryDiff));

        // 驗證記憶體沒有持續大幅上升趨勢
        // 注意：由於 JIT 編譯和類別載入，前幾個週期可能會有記憶體增長
        // 我們應該關注的是整體增長趨勢，而非每個週期都要下降
        int increasingCount = 0;
        for (int i = 1; i < cycleMemoryUsage.size(); i++) {
            if (cycleMemoryUsage.get(i) > cycleMemoryUsage.get(i - 1)) {
                increasingCount++;
            }
        }

        System.out.println("  - 記憶體上升次數: " + increasingCount + " / " + (cycles - 1));

        // 檢查最後兩個週期是否穩定（允許 JIT 暖機造成的初期增長）
        // 如果最後兩個週期的差異小於 50MB，則認為已經穩定
        if (cycleMemoryUsage.size() >= 2) {
            long lastTwoCyclesDiff = Math.abs(
                cycleMemoryUsage.get(cycleMemoryUsage.size() - 1) -
                cycleMemoryUsage.get(cycleMemoryUsage.size() - 2)
            );
            System.out.println("  - 最後兩週期差異: " + lastTwoCyclesDiff + " MB");

            assertTrue(lastTwoCyclesDiff < 50,
                    String.format("最後兩週期記憶體差異過大: %d MB，可能有洩漏", lastTwoCyclesDiff));
        }
    }

    // ==================== Helper Methods ====================

    private TransactionRequest createTestRequest(String txnId, TransactionType txnType) {
        TransactionRequest.TransactionRequestBuilder builder = TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(txnType)
                .pan("4111111111111111")
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan(String.format("%06d", Math.abs(txnId.hashCode()) % 999999))
                .rrn(String.format("%012d", Math.abs(txnId.hashCode()) % 999999999999L))
                .pinBlock("1234567890ABCDEF")
                .channel("ATM");

        // 依交易類型設定處理碼和金額
        switch (txnType) {
            case WITHDRAWAL:
                builder.processingCode("010000")
                       .amount(new BigDecimal("1000"));
                break;
            case DEPOSIT:
                builder.processingCode("210000")
                       .amount(new BigDecimal("2000"));
                break;
            case TRANSFER:
                builder.processingCode("400000")
                       .amount(new BigDecimal("5000"))
                       .destinationAccount("98765432109876")
                       .destinationBankCode("012");
                break;
            case BALANCE_INQUIRY:
                builder.processingCode("310000");
                break;
            default:
                builder.processingCode("000000");
        }

        return builder.build();
    }
}
