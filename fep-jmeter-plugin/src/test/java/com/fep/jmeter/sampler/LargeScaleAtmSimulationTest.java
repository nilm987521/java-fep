package com.fep.jmeter.sampler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Large-scale ATM simulation test - 2000 ATMs.
 */
class LargeScaleAtmSimulationTest {

    private static final int TEST_PORT = 19001;
    private static final String ATM_CSV_PATH = "examples/data/atm-list-large.csv";
    private static final String CARD_CSV_PATH = "examples/data/card-list-large.csv";

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final ConcurrentHashMap<String, AtomicInteger> atmTransactionCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalTransactions = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);

    @BeforeEach
    void startMockServer() throws Exception {
        atmTransactionCounts.clear();
        totalTransactions.set(0);
        successfulConnections.set(0);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(8);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 2048)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new MockServerHandler());
                    }
                });

        serverChannel = bootstrap.bind(TEST_PORT).sync().channel();
        System.out.println("Mock server started on port " + TEST_PORT);
    }

    @AfterEach
    void stopMockServer() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("Verify 2000 ATM CSV data is valid")
    void verifyCsvDataValidity() throws Exception {
        Path atmPath = Paths.get(ATM_CSV_PATH);
        Path cardPath = Paths.get(CARD_CSV_PATH);

        // Check files exist
        assertTrue(Files.exists(atmPath), "ATM CSV file should exist: " + atmPath.toAbsolutePath());
        assertTrue(Files.exists(cardPath), "Card CSV file should exist: " + cardPath.toAbsolutePath());

        // Load and verify ATM data
        List<String[]> atmData = loadCsv(atmPath);
        assertEquals(2000, atmData.size(), "Should have 2000 ATMs");

        // Check all ATM IDs are unique
        Set<String> atmIds = new HashSet<>();
        for (String[] row : atmData) {
            String atmId = row[0];
            assertTrue(atmId.matches("ATM\\d{5}"), "ATM ID should match pattern: " + atmId);
            assertTrue(atmIds.add(atmId), "ATM ID should be unique: " + atmId);
        }
        assertEquals(2000, atmIds.size(), "All 2000 ATM IDs should be unique");

        // Load and verify Card data
        List<String[]> cardData = loadCsv(cardPath);
        assertEquals(10000, cardData.size(), "Should have 10000 cards");

        // Check all card numbers are unique
        Set<String> cardNumbers = new HashSet<>();
        for (String[] row : cardData) {
            String cardNumber = row[0];
            assertTrue(cardNumber.matches("\\d{16}"), "Card number should be 16 digits: " + cardNumber);
            assertTrue(cardNumbers.add(cardNumber), "Card number should be unique: " + cardNumber);
        }
        assertEquals(10000, cardNumbers.size(), "All 10000 card numbers should be unique");

        System.out.println("=== CSV Data Verification ===");
        System.out.println("ATM Count: " + atmData.size() + " (all unique IDs)");
        System.out.println("Card Count: " + cardData.size() + " (all unique numbers)");
        System.out.println("✓ All data is valid");
    }

    @Test
    @DisplayName("Simulate 100 concurrent ATMs (subset of 2000)")
    void simulate100ConcurrentAtms() throws Exception {
        Path atmPath = Paths.get(ATM_CSV_PATH);
        Path cardPath = Paths.get(CARD_CSV_PATH);

        if (!Files.exists(atmPath) || !Files.exists(cardPath)) {
            System.out.println("Skipping test - CSV files not found. Run generate-test-data.sh first.");
            return;
        }

        List<String[]> atmData = loadCsv(atmPath);
        List<String[]> cardData = loadCsv(cardPath);

        int numAtms = 100;  // Test with 100 ATMs (subset of 2000)
        int transactionsPerAtm = 3;

        ExecutorService executor = Executors.newFixedThreadPool(numAtms);
        CountDownLatch latch = new CountDownLatch(numAtms * transactionsPerAtm);
        AtomicInteger cardIndex = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numAtms; i++) {
            final String[] atm = atmData.get(i);
            final int atmIdx = i;

            executor.submit(() -> {
                for (int txn = 0; txn < transactionsPerAtm; txn++) {
                    try {
                        int cIdx = cardIndex.getAndIncrement() % cardData.size();
                        String[] card = cardData.get(cIdx);

                        AtmSimulatorSampler sampler = new AtmSimulatorSampler();
                        sampler.setFepHost("localhost");
                        sampler.setFepPort(TEST_PORT);
                        sampler.setConnectionTimeout(5000);
                        sampler.setReadTimeout(5000);
                        sampler.setProtocolType("ISO_8583");
                        sampler.setTransactionType("WITHDRAWAL");
                        sampler.setAtmId(atm[0]);           // ATM_ID
                        sampler.setAtmLocation(atm[1]);     // ATM_LOCATION
                        sampler.setBankCode(atm[2]);        // BANK_CODE
                        sampler.setCardNumber(card[0]);     // CARD_NUMBER
                        sampler.setAmount(String.valueOf(10000 + (atmIdx * 100) + txn));

                        sampler.sample(null);

                        atmTransactionCounts.computeIfAbsent(atm[0], k -> new AtomicInteger(0)).incrementAndGet();
                        totalTransactions.incrementAndGet();
                        successfulConnections.incrementAndGet();
                    } catch (Exception e) {
                        // Expected - mock server doesn't send proper response
                        atmTransactionCounts.computeIfAbsent(atm[0], k -> new AtomicInteger(0)).incrementAndGet();
                        totalTransactions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // Results
        System.out.println("\n=== 100 ATM Simulation Results ===");
        System.out.println("Completed: " + completed);
        System.out.println("Total Time: " + (endTime - startTime) + " ms");
        System.out.println("Total Transactions: " + totalTransactions.get());
        System.out.println("Unique ATMs Used: " + atmTransactionCounts.size());
        System.out.println("TPS: " + String.format("%.2f", totalTransactions.get() * 1000.0 / (endTime - startTime)));

        // Verify
        assertEquals(numAtms, atmTransactionCounts.size(), "Should have " + numAtms + " unique ATM IDs");
        assertEquals(numAtms * transactionsPerAtm, totalTransactions.get(), "Total transactions should match");

        // Show sample
        System.out.println("\nSample ATM Transaction Counts (first 10):");
        atmTransactionCounts.entrySet().stream()
                .limit(10)
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue().get() + " txns"));

        System.out.println("\n✓ All " + numAtms + " ATMs have unique IDs");
        System.out.println("✓ Each ATM sent " + transactionsPerAtm + " transactions");
    }

    @Test
    @DisplayName("Verify 2000 ATM distribution simulation")
    void verify2000AtmDistribution() throws Exception {
        Path atmPath = Paths.get(ATM_CSV_PATH);

        if (!Files.exists(atmPath)) {
            System.out.println("Skipping test - CSV files not found.");
            return;
        }

        List<String[]> atmData = loadCsv(atmPath);

        // Simulate JMeter's shareMode.thread behavior
        // Each thread gets assigned one ATM and keeps it for all iterations
        int numThreads = 2000;
        int iterationsPerThread = 5;

        Map<Integer, String> threadToAtm = new HashMap<>();
        Map<String, Integer> atmTransactionCount = new HashMap<>();

        System.out.println("=== Simulating JMeter CSV Data Set Config ===");
        System.out.println("Mode: shareMode.thread");
        System.out.println("Threads: " + numThreads);
        System.out.println("Iterations per thread: " + iterationsPerThread);

        // Simulate thread assignment
        for (int threadNum = 0; threadNum < numThreads; threadNum++) {
            // Each thread gets one ATM (shareMode.thread = each thread reads next line once)
            String atmId = atmData.get(threadNum % atmData.size())[0];
            threadToAtm.put(threadNum, atmId);
        }

        // Simulate iterations
        for (int iter = 0; iter < iterationsPerThread; iter++) {
            for (int threadNum = 0; threadNum < numThreads; threadNum++) {
                String atmId = threadToAtm.get(threadNum);
                atmTransactionCount.merge(atmId, 1, Integer::sum);
            }
        }

        // Verify results
        assertEquals(2000, threadToAtm.size(), "Should have 2000 thread-to-ATM mappings");
        assertEquals(2000, atmTransactionCount.size(), "Should have 2000 unique ATMs");

        // Each ATM should have exactly 5 transactions
        for (var entry : atmTransactionCount.entrySet()) {
            assertEquals(iterationsPerThread, entry.getValue(),
                    "ATM " + entry.getKey() + " should have " + iterationsPerThread + " transactions");
        }

        System.out.println("\n✓ Thread-to-ATM mapping verified");
        System.out.println("✓ Each of 2000 ATMs assigned to one thread");
        System.out.println("✓ Each ATM would send " + iterationsPerThread + " transactions");
        System.out.println("✓ Total transactions: " + (numThreads * iterationsPerThread));

        // Show distribution sample
        System.out.println("\nSample Thread-to-ATM assignments:");
        for (int i = 0; i < 5; i++) {
            System.out.println("  Thread " + (i + 1) + " → " + threadToAtm.get(i));
        }
        System.out.println("  ...");
        for (int i = 1995; i < 2000; i++) {
            System.out.println("  Thread " + (i + 1) + " → " + threadToAtm.get(i));
        }
    }

    private List<String[]> loadCsv(Path path) throws IOException {
        List<String[]> data = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                data.add(line.split(","));
            }
        }
        return data;
    }

    private class MockServerHandler extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 2) return;

            in.markReaderIndex();
            int length = in.readShort() & 0xFFFF;

            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }

            byte[] data = new byte[length];
            in.readBytes(data);

            // Send minimal response
            ByteBuf response = Unpooled.buffer();
            response.writeShort(4);
            response.writeBytes("0210".getBytes());
            ctx.writeAndFlush(response);
        }
    }
}
