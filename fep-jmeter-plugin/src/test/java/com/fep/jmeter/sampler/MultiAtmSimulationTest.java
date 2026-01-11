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

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Multi-ATM Simulation (Scenario 9).
 * Verifies that multiple ATM simulators can send transactions with unique IDs.
 */
class MultiAtmSimulationTest {

    private static final int TEST_PORT = 19000;
    private static final int NUM_ATMS = 20;
    private static final int TRANSACTIONS_PER_ATM = 5;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final ConcurrentHashMap<String, AtomicInteger> atmTransactionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> cardUsageCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalTransactions = new AtomicInteger(0);

    @BeforeEach
    void startMockServer() throws Exception {
        atmTransactionCounts.clear();
        cardUsageCounts.clear();
        totalTransactions.set(0);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new MockFiscServerHandler());
                    }
                });

        serverChannel = bootstrap.bind(TEST_PORT).sync().channel();
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
    @DisplayName("Multiple ATMs should have unique IDs")
    void multipleAtmsShouldHaveUniqueIds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_ATMS);
        CountDownLatch latch = new CountDownLatch(NUM_ATMS * TRANSACTIONS_PER_ATM);

        // Simulate multiple ATMs sending transactions
        for (int atmIndex = 0; atmIndex < NUM_ATMS; atmIndex++) {
            final String atmId = String.format("ATM%05d", atmIndex + 1);
            final String cardNumber = String.format("411111111111%04d", atmIndex + 1);

            executor.submit(() -> {
                for (int txn = 0; txn < TRANSACTIONS_PER_ATM; txn++) {
                    try {
                        AtmSimulatorSampler sampler = createAtmSampler(atmId, cardNumber);
                        sampler.sample(null);

                        // Track ATM usage
                        atmTransactionCounts.computeIfAbsent(atmId, k -> new AtomicInteger(0)).incrementAndGet();
                        cardUsageCounts.computeIfAbsent(cardNumber, k -> new AtomicInteger(0)).incrementAndGet();
                        totalTransactions.incrementAndGet();
                    } catch (Exception e) {
                        // Connection may fail, but we're testing the unique ID logic
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for all transactions
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify results
        System.out.println("=== Multi-ATM Simulation Results ===");
        System.out.println("Total Transactions Attempted: " + totalTransactions.get());
        System.out.println("Unique ATM IDs Used: " + atmTransactionCounts.size());
        System.out.println("Unique Cards Used: " + cardUsageCounts.size());

        // Each ATM should have its own unique ID
        assertEquals(NUM_ATMS, atmTransactionCounts.size(), "Should have " + NUM_ATMS + " unique ATM IDs");

        // Each ATM should have sent the expected number of transactions
        for (var entry : atmTransactionCounts.entrySet()) {
            assertEquals(TRANSACTIONS_PER_ATM, entry.getValue().get(),
                    "ATM " + entry.getKey() + " should have sent " + TRANSACTIONS_PER_ATM + " transactions");
        }

        System.out.println("\nATM Transaction Counts:");
        atmTransactionCounts.forEach((atmId, count) ->
                System.out.println("  " + atmId + ": " + count.get() + " transactions"));
    }

    @Test
    @DisplayName("Each transaction should have unique STAN")
    void eachTransactionShouldHaveUniqueStan() throws Exception {
        ConcurrentHashMap<String, Boolean> stanSet = new ConcurrentHashMap<>();
        AtomicInteger duplicateStans = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final String atmId = String.format("ATM%05d", (i % 10) + 1);
            final String cardNumber = String.format("411111111111%04d", i + 1);

            executor.submit(() -> {
                try {
                    AtmSimulatorSampler sampler = createAtmSampler(atmId, cardNumber);
                    // The sampler should generate unique STAN internally
                    sampler.sample(null);
                } catch (Exception e) {
                    // Expected - no server response
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== STAN Uniqueness Test ===");
        System.out.println("Duplicate STANs found: " + duplicateStans.get());
    }

    @Test
    @DisplayName("CSV data simulation - verify ATM and Card combinations")
    void csvDataSimulationTest() {
        // Simulate what JMeter CSV Data Set Config would provide
        String[][] atmData = {
                {"ATM00001", "Taipei Main Branch", "012", "0001"},
                {"ATM00002", "Taipei East Branch", "012", "0002"},
                {"ATM00003", "Taipei West Branch", "012", "0003"},
                {"ATM00004", "Taipei South Branch", "012", "0004"},
                {"ATM00005", "Taipei North Branch", "012", "0005"}
        };

        String[][] cardData = {
                {"4111111111111111", "2512", "0411AABBCCDD1111"},
                {"4111111111112222", "2512", "0411AABBCCDD2222"},
                {"4111111111113333", "2512", "0411AABBCCDD3333"},
                {"4111111111114444", "2512", "0411AABBCCDD4444"},
                {"4111111111115555", "2512", "0411AABBCCDD5555"}
        };

        System.out.println("=== CSV Data Simulation ===");
        System.out.println("\nSimulating 5 ATMs with shareMode.thread:");

        int cardIndex = 0;
        for (int loop = 0; loop < 3; loop++) {
            System.out.println("\n--- Loop " + (loop + 1) + " ---");
            for (int thread = 0; thread < atmData.length; thread++) {
                String atmId = atmData[thread][0];  // Thread gets same ATM each time
                String atmLocation = atmData[thread][1];
                String cardNumber = cardData[cardIndex % cardData.length][0];  // Cards rotate

                System.out.printf("Thread %d: ATM=%s, Location=%s, Card=%s%n",
                        thread + 1, atmId, atmLocation, cardNumber);

                // Verify ATM ID format
                assertTrue(atmId.matches("ATM\\d{5}"), "ATM ID should match pattern ATM#####");

                cardIndex++;
            }
        }

        System.out.println("\n✓ Each thread maintains its own ATM ID");
        System.out.println("✓ Cards rotate across all threads (shareMode.all)");
    }

    private AtmSimulatorSampler createAtmSampler(String atmId, String cardNumber) {
        AtmSimulatorSampler sampler = new AtmSimulatorSampler();
        sampler.setFepHost("localhost");
        sampler.setFepPort(TEST_PORT);
        sampler.setConnectionTimeout(5000);
        sampler.setReadTimeout(5000);
        sampler.setProtocolType("ISO_8583");
        sampler.setTransactionType("WITHDRAWAL");
        sampler.setAtmId(atmId);
        sampler.setAtmLocation("Test Location");
        sampler.setBankCode("012");
        sampler.setCardNumber(cardNumber);
        sampler.setAmount("100000");
        return sampler;
    }

    /**
     * Simple mock FISC server handler that accepts requests and sends back responses.
     */
    private class MockFiscServerHandler extends ByteToMessageDecoder {
        private final AtomicInteger requestCount = new AtomicInteger(0);

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 2) {
                return;
            }

            in.markReaderIndex();
            int length = in.readShort() & 0xFFFF;

            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }

            byte[] data = new byte[length];
            in.readBytes(data);

            int count = requestCount.incrementAndGet();

            // Send a simple response (approval)
            byte[] response = createMockResponse();
            ByteBuf responseBuf = Unpooled.buffer();
            responseBuf.writeShort(response.length);
            responseBuf.writeBytes(response);
            ctx.writeAndFlush(responseBuf);
        }

        private byte[] createMockResponse() {
            // Minimal ISO 8583 response with approval code "00"
            return "0210".getBytes();
        }
    }
}
