package com.fep.jmeter.engine;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.message.iso8583.parser.FiscMessageParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Bank Core System Dual-Channel Simulator Engine for JMeter.
 *
 * <p>Simulates a bank core system with dual-channel architecture:
 * <ul>
 *   <li>Send Channel: Receives requests from FEP</li>
 *   <li>Receive Channel: Sends responses back to FEP</li>
 *   <li>Supports proactive message sending (notifications, reconciliation triggers)</li>
 * </ul>
 *
 * <p>Message flow:
 * <pre>
 *     FEP                         Bank Core Simulator
 *     ---                         -------------------
 *     Send Channel ──request──►   Send Port (receives requests)
 *                                 │
 *                                 ▼ (validation → handler → queue)
 *                                 │
 *     Receive Channel ◄──resp──   Receive Port (sends responses)
 * </pre>
 *
 * <p>Supported transaction types:
 * <ul>
 *   <li>Account Inquiry (查詢)</li>
 *   <li>Deposit/Withdrawal (存提款)</li>
 *   <li>Transfer (轉帳)</li>
 *   <li>Bill Payment (代繳)</li>
 *   <li>Reconciliation Request (對帳請求)</li>
 *   <li>System Notifications (系統通知)</li>
 * </ul>
 */
@Slf4j
public class BankCoreSimulatorEngine implements AutoCloseable {

    private final int sendPort;
    private final int receivePort;
    private final Iso8583MessageFactory messageFactory;
    private final FiscMessageParser messageParser;
    private final FiscMessageAssembler messageAssembler;

    /** Request handlers by MTI */
    @Getter
    private final Map<String, Function<Iso8583Message, Iso8583Message>> requestHandlers = new ConcurrentHashMap<>();

    /** Queue of pending responses to send via Receive channel */
    private final BlockingQueue<PendingResponse> responseQueue = new LinkedBlockingQueue<>();

    /** Queue of received requests for inspection */
    @Getter
    private final BlockingQueue<ReceivedRequest> requestQueue = new LinkedBlockingQueue<>(10000);

    /** Client router for multi-client support */
    @Getter
    private final ClientRouter clientRouter = new ClientRouter();

    // Netty components
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel sendServerChannel;
    private Channel receiveServerChannel;
    private final AtomicBoolean started = new AtomicBoolean(false);

    // Response sender thread
    private Thread responseSenderThread;
    private volatile boolean runResponseSender = true;

    // Statistics
    @Getter
    private final AtomicInteger sendChannelClientCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger receiveChannelClientCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesReceived = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    @Getter
    private final AtomicInteger validationErrors = new AtomicInteger(0);

    /** Delay in milliseconds before sending response */
    @Getter
    @Setter
    private volatile long responseDelayMs = 0;

    /** Default response code (00 = success) */
    @Getter
    @Setter
    private volatile String defaultResponseCode = "00";

    /** Validation error response code */
    @Getter
    @Setter
    private volatile String validationErrorCode = "30";

    /** Enable FEP ID routing */
    @Getter
    @Setter
    private volatile boolean enableFepIdRouting = true;

    /** FEP ID field number (default F32 - Acquiring Institution ID) */
    @Getter
    @Setter
    private volatile int fepIdField = 32;

    /** Validation callback - return null if validation passes, error message if fails */
    @Setter
    private Function<Iso8583Message, String> validationCallback;

    /** Request received callback */
    @Setter
    private BiConsumer<Iso8583Message, String> requestReceivedCallback;

    /** Last received request info */
    @Getter
    private volatile String lastRequestMti;
    @Getter
    private volatile String lastRequestStan;
    @Getter
    private volatile String lastValidationResult = "N/A";

    // Balance simulation data
    @Getter
    @Setter
    private volatile String defaultAvailableBalance = "000000100000"; // 1,000.00
    @Getter
    @Setter
    private volatile String defaultLedgerBalance = "000000150000";    // 1,500.00

    /**
     * Creates a simulator engine with random available ports.
     */
    public BankCoreSimulatorEngine() {
        this(0, 0);
    }

    /**
     * Creates a simulator engine with specified ports.
     *
     * @param sendPort the port for Send channel (0 for random)
     * @param receivePort the port for Receive channel (0 for random)
     */
    public BankCoreSimulatorEngine(int sendPort, int receivePort) {
        this.sendPort = sendPort;
        this.receivePort = receivePort;
        this.messageFactory = new Iso8583MessageFactory();
        this.messageParser = new FiscMessageParser(false);
        this.messageAssembler = new FiscMessageAssembler(true);
        initializeDefaultHandlers();
    }

    /**
     * Gets the actual Send port after binding.
     */
    public int getSendPort() {
        if (sendServerChannel == null) {
            return sendPort;
        }
        return ((InetSocketAddress) sendServerChannel.localAddress()).getPort();
    }

    /**
     * Gets the actual Receive port after binding.
     */
    public int getReceivePort() {
        if (receiveServerChannel == null) {
            return receivePort;
        }
        return ((InetSocketAddress) receiveServerChannel.localAddress()).getPort();
    }

    /**
     * Initializes default message handlers for bank core operations.
     */
    private void initializeDefaultHandlers() {
        // Network Management (0800) - Sign On/Off, Echo Test
        registerHandler("0800", request -> {
            String networkCode = request.getFieldAsString(70);
            log.info("[BankCore] Network management: code={}", networkCode);

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            response.setField(70, networkCode);
            return response;
        });

        // Balance Inquiry (0200 with processing code 31xxxx)
        registerHandler("0200", request -> {
            String processingCode = request.getFieldAsString(3);
            String amount = request.getFieldAsString(4);
            String accountNumber = request.getFieldAsString(102); // From Account

            log.info("[BankCore] Financial request: code={}, amount={}, account={}",
                processingCode, amount, accountNumber);

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);

            // Add balance info for inquiry (31xxxx)
            if (processingCode != null && processingCode.startsWith("31")) {
                response.setField(54, defaultAvailableBalance + defaultLedgerBalance);
            }

            return response;
        });

        // Reversal Request (0400)
        registerHandler("0400", request -> {
            log.info("[BankCore] Reversal request: STAN={}, OrigSTAN={}",
                request.getFieldAsString(11), request.getFieldAsString(90));

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            return response;
        });

        // Authorization Request (0100)
        registerHandler("0100", request -> {
            String processingCode = request.getFieldAsString(3);
            String amount = request.getFieldAsString(4);

            log.info("[BankCore] Authorization request: code={}, amount={}", processingCode, amount);

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            response.setField(38, generateAuthCode()); // Authorization Code
            return response;
        });
    }

    /**
     * Generates a random authorization code.
     */
    private String generateAuthCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /**
     * Registers a custom request handler.
     *
     * @param mti the message type indicator
     * @param handler the handler function
     */
    public void registerHandler(String mti, Function<Iso8583Message, Iso8583Message> handler) {
        requestHandlers.put(mti, handler);
    }

    /**
     * Starts the dual-channel simulator engine.
     *
     * @return CompletableFuture that completes when both ports are bound
     */
    public CompletableFuture<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        bossGroup = new NioEventLoopGroup(2);
        workerGroup = new NioEventLoopGroup();

        try {
            CompletableFuture<Void> sendFuture = startServer("SEND", sendPort, true);
            CompletableFuture<Void> receiveFuture = startServer("RECEIVE", receivePort, false);

            CompletableFuture.allOf(sendFuture, receiveFuture)
                .thenRun(() -> {
                    startResponseSender();
                    log.info("[BankCore] Dual-channel started: Send={}, Receive={}",
                        getSendPort(), getReceivePort());
                    future.complete(null);
                })
                .exceptionally(ex -> {
                    started.set(false);
                    future.completeExceptionally(ex);
                    return null;
                });

        } catch (Exception e) {
            started.set(false);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Starts a server on the specified port.
     */
    private CompletableFuture<Void> startServer(String name, int port, boolean isSendChannel) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("decoder", new CoreMessageDecoder());
                    pipeline.addLast("encoder", new CoreMessageEncoder());
                    if (isSendChannel) {
                        pipeline.addLast("handler", new CoreSendChannelHandler());
                    } else {
                        pipeline.addLast("handler", new CoreReceiveChannelHandler());
                    }
                }
            });

        bootstrap.bind(port).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                if (isSendChannel) {
                    sendServerChannel = f.channel();
                } else {
                    receiveServerChannel = f.channel();
                }
                int boundPort = ((InetSocketAddress) f.channel().localAddress()).getPort();
                log.info("[BankCore] {} port bound: {}", name, boundPort);
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    /**
     * Starts the response sender thread.
     */
    private void startResponseSender() {
        runResponseSender = true;
        responseSenderThread = new Thread(() -> {
            while (runResponseSender) {
                try {
                    PendingResponse pending = responseQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (pending != null) {
                        // Apply response delay if configured
                        if (responseDelayMs > 0) {
                            Thread.sleep(responseDelayMs);
                        }

                        // Route response to the correct client
                        boolean sent = false;
                        if (enableFepIdRouting && pending.fepId != null) {
                            Channel targetChannel = clientRouter.getSendChannel(pending.fepId);
                            if (targetChannel != null && targetChannel.isActive()) {
                                targetChannel.writeAndFlush(pending.response).sync();
                                messagesSent.incrementAndGet();
                                log.debug("[BankCore] Sent response via FEP ID routing: fepId={}, STAN={}",
                                    pending.fepId, pending.response.getFieldAsString(11));
                                sent = true;
                            }
                        }

                        // Fallback to first available client
                        if (!sent) {
                            Channel firstClient = clientRouter.getFirstAvailableSendChannel();
                            if (firstClient != null && firstClient.isActive()) {
                                firstClient.writeAndFlush(pending.response).sync();
                                messagesSent.incrementAndGet();
                                log.debug("[BankCore] Sent response to first available client: STAN={}",
                                    pending.response.getFieldAsString(11));
                                sent = true;
                            }
                        }

                        if (!sent) {
                            log.warn("[BankCore] No receive channel client connected, response dropped: STAN={}",
                                pending.response.getFieldAsString(11));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[BankCore] Error sending response", e);
                }
            }
        }, "bankcore-response-sender");
        responseSenderThread.setDaemon(true);
        responseSenderThread.start();
    }

    /**
     * Stops the simulator engine.
     */
    public CompletableFuture<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        runResponseSender = false;
        if (responseSenderThread != null) {
            responseSenderThread.interrupt();
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        List<ChannelFuture> closeFutures = new java.util.ArrayList<>();
        if (sendServerChannel != null) {
            closeFutures.add(sendServerChannel.close());
        }
        if (receiveServerChannel != null) {
            closeFutures.add(receiveServerChannel.close());
        }

        if (closeFutures.isEmpty()) {
            shutdownEventLoops(future);
        } else {
            ChannelFuture lastClose = closeFutures.get(closeFutures.size() - 1);
            lastClose.addListener(f -> shutdownEventLoops(future));
        }

        return future;
    }

    private void shutdownEventLoops(CompletableFuture<Void> future) {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("[BankCore] Dual-channel simulator engine stopped");
        future.complete(null);
    }

    /**
     * Sends a proactive message to a specific FEP by ID.
     * Use this for notifications, reconciliation triggers, etc.
     *
     * @param fepId the target FEP ID
     * @param message the message to send
     * @return true if sent successfully
     */
    public boolean sendProactiveMessage(String fepId, Iso8583Message message) {
        Channel channel = clientRouter.getSendChannel(fepId);
        if (channel != null && channel.isActive()) {
            try {
                channel.writeAndFlush(message).sync();
                messagesSent.incrementAndGet();
                log.info("[BankCore] Sent proactive message to fepId={}: MTI={}",
                    fepId, message.getMti());
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[BankCore] Failed to send proactive message to fepId={}", fepId, e);
            }
        }
        return false;
    }

    /**
     * Broadcasts a proactive message to all connected FEPs.
     *
     * @param message the message to broadcast
     * @return number of FEPs the message was sent to
     */
    public int broadcastMessage(Iso8583Message message) {
        int sentCount = 0;
        for (Channel channel : clientRouter.getAllSendChannels()) {
            if (channel.isActive()) {
                try {
                    channel.writeAndFlush(message).sync();
                    messagesSent.incrementAndGet();
                    sentCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("[BankCore] Broadcast message to {} FEPs: MTI={}", sentCount, message.getMti());
        return sentCount;
    }

    /**
     * Sends a reconciliation notification to a specific FEP.
     *
     * @param fepId the target FEP ID
     * @param settlementDate the settlement date (MMDD format)
     * @return true if sent successfully
     */
    public boolean sendReconciliationNotification(String fepId, String settlementDate) {
        Iso8583Message notification = messageFactory.createNetworkManagementMessage("301"); // Reconciliation notification
        notification.setField(7, getCurrentDateTime());
        notification.setField(11, generateStan());
        notification.setField(15, settlementDate); // Settlement date

        return sendProactiveMessage(fepId, notification);
    }

    /**
     * Sends a system status notification to all FEPs.
     *
     * @param statusCode the status code (001=available, 002=maintenance, etc.)
     * @return number of FEPs notified
     */
    public int sendSystemStatusNotification(String statusCode) {
        Iso8583Message notification = messageFactory.createNetworkManagementMessage(statusCode);
        notification.setField(7, getCurrentDateTime());
        notification.setField(11, generateStan());

        return broadcastMessage(notification);
    }

    private String getCurrentDateTime() {
        return java.time.format.DateTimeFormatter.ofPattern("MMddHHmmss")
            .format(java.time.LocalDateTime.now());
    }

    private String generateStan() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /**
     * Checks if the engine is running.
     */
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Resets statistics counters.
     */
    public void resetCounters() {
        messagesReceived.set(0);
        messagesSent.set(0);
        validationErrors.set(0);
        requestQueue.clear();
    }

    @Override
    public void close() {
        stop().join();
    }

    // ==================== Inner Classes ====================

    /**
     * Pending response wrapper with routing info.
     */
    private record PendingResponse(Iso8583Message response, String fepId) {}

    /**
     * Received request wrapper.
     */
    public record ReceivedRequest(
        Iso8583Message message,
        String fepId,
        long timestamp,
        String validationResult
    ) {}

    /**
     * ISO 8583 Message Decoder for Bank Core.
     */
    private class CoreMessageDecoder extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 2) {
                return;
            }

            in.markReaderIndex();
            int length = readBcdLength(in);

            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }

            byte[] data = new byte[length];
            in.readBytes(data);

            try {
                Iso8583Message message = messageParser.parse(data);
                out.add(message);
            } catch (Exception e) {
                log.error("[BankCore] Decode error", e);
                throw e;
            }
        }

        private int readBcdLength(ByteBuf in) {
            byte b1 = in.readByte();
            byte b2 = in.readByte();
            int h1 = (b1 >> 4) & 0x0F;
            int l1 = b1 & 0x0F;
            int h2 = (b2 >> 4) & 0x0F;
            int l2 = b2 & 0x0F;
            return h1 * 1000 + l1 * 100 + h2 * 10 + l2;
        }
    }

    /**
     * ISO 8583 Message Encoder for Bank Core.
     */
    private class CoreMessageEncoder extends MessageToByteEncoder<Iso8583Message> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) {
            byte[] data = messageAssembler.assemble(msg);
            out.writeBytes(data);
        }
    }

    /**
     * Handler for Send channel (receives requests from FEP).
     */
    private class CoreSendChannelHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        private String channelId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channelId = ctx.channel().remoteAddress().toString();
            int count = sendChannelClientCount.incrementAndGet();
            clientRouter.registerReceiveChannel(channelId, ctx.channel());
            log.info("[BankCore] Send channel FEP connected: {} (total: {})", channelId, count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            int count = sendChannelClientCount.decrementAndGet();
            clientRouter.unregisterReceiveChannel(channelId);
            log.info("[BankCore] Send channel FEP disconnected: {} (total: {})", channelId, count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message request) {
            String mti = request.getMti();
            String stan = request.getFieldAsString(11);
            String fepId = request.getFieldAsString(fepIdField);

            messagesReceived.incrementAndGet();
            lastRequestMti = mti;
            lastRequestStan = stan;

            log.debug("[BankCore] Received on Send channel: MTI={}, STAN={}, FepId={}", mti, stan, fepId);

            // Register FEP ID mapping for routing
            if (enableFepIdRouting && fepId != null && !fepId.isEmpty()) {
                clientRouter.associateBankIdWithChannel(fepId, channelId);
            }

            // Validate request
            String validationError = null;
            if (validationCallback != null) {
                validationError = validationCallback.apply(request);
                if (validationError != null) {
                    validationErrors.incrementAndGet();
                    lastValidationResult = "FAIL: " + validationError;
                    log.warn("[BankCore] Validation failed for MTI={}, STAN={}: {}",
                        mti, stan, validationError);
                } else {
                    lastValidationResult = "PASS";
                }
            } else {
                lastValidationResult = "SKIP";
            }

            // Store received request
            requestQueue.offer(new ReceivedRequest(request, fepId, System.currentTimeMillis(), lastValidationResult));

            // Notify callback
            if (requestReceivedCallback != null) {
                requestReceivedCallback.accept(request, lastValidationResult);
            }

            // If validation failed, return error response
            if (validationError != null) {
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, validationErrorCode);
                responseQueue.offer(new PendingResponse(errorResponse, fepId));
                return;
            }

            // Find handler and create response
            Function<Iso8583Message, Iso8583Message> handler = requestHandlers.get(mti);
            if (handler != null) {
                try {
                    Iso8583Message response = handler.apply(request);
                    responseQueue.offer(new PendingResponse(response, fepId));
                    log.debug("[BankCore] Response queued: MTI={}, STAN={}", response.getMti(), stan);
                } catch (Exception e) {
                    log.error("[BankCore] Handler error for MTI={}", mti, e);
                    Iso8583Message errorResponse = request.createResponse();
                    errorResponse.setField(39, "96"); // System malfunction
                    responseQueue.offer(new PendingResponse(errorResponse, fepId));
                }
            } else {
                log.warn("[BankCore] No handler for MTI={}", mti);
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, "12"); // Invalid transaction
                responseQueue.offer(new PendingResponse(errorResponse, fepId));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[BankCore] Send channel exception", cause);
            ctx.close();
        }
    }

    /**
     * Handler for Receive channel (sends responses to FEP).
     */
    private class CoreReceiveChannelHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        private String channelId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channelId = ctx.channel().remoteAddress().toString();
            clientRouter.registerSendChannel(channelId, ctx.channel());
            int count = receiveChannelClientCount.incrementAndGet();
            log.info("[BankCore] Receive channel FEP connected: {} (total: {})", channelId, count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            clientRouter.unregisterSendChannel(channelId);
            int count = receiveChannelClientCount.decrementAndGet();
            log.info("[BankCore] Receive channel FEP disconnected: {} (total: {})", channelId, count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message msg) {
            // Receive channel shouldn't receive messages from FEP in dual-channel mode
            log.warn("[BankCore] Unexpected message on Receive channel: MTI={}", msg.getMti());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[BankCore] Receive channel exception", cause);
            ctx.close();
        }
    }
}
