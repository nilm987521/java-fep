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
 * FISC Dual-Channel Simulator Engine for JMeter.
 *
 * <p>Simulates FISC (Financial Information Service Center) server with dual-channel architecture.
 *
 * <p>Features:
 * <ul>
 *   <li>Request queue for tracking received messages</li>
 *   <li>Proactive message sending capability</li>
 *   <li>Validation callback mechanism</li>
 *   <li>Multi-client routing by Bank ID</li>
 * </ul>
 *
 * <p>Port naming (from Sampler/Simulator's perspective - what the Sampler DOES):
 * <pre>
 *     Client (FEP)              Sampler/Simulator (FISC)
 *     ------------              -----------------------
 *         ├── send request ───► Receive Port (9000)  [Sampler RECEIVES here]
 *         │                           │
 *         │                           ▼ (validate → process → queue)
 *         │                           │
 *         └── receive response ◄─ Send Port (9001)   [Sampler SENDS here]
 * </pre>
 */
@Slf4j
public class FiscDualChannelSimulatorEngine implements AutoCloseable {

    private final int receivePort;
    private final int sendPort;
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
    private Channel receiveServerChannel;
    private Channel sendServerChannel;
    private final AtomicBoolean started = new AtomicBoolean(false);

    // Response sender thread
    private Thread responseSenderThread;
    private volatile boolean runResponseSender = true;

    // Statistics
    @Getter
    private final AtomicInteger receiveChannelClientCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger sendChannelClientCount = new AtomicInteger(0);
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

    /** Default response code */
    @Getter
    @Setter
    private volatile String defaultResponseCode = "00";

    /** Validation error response code */
    @Getter
    @Setter
    private volatile String validationErrorCode = "30";

    /** Enable Bank ID routing */
    @Getter
    @Setter
    private volatile boolean enableBankIdRouting = true;

    /** Bank ID field number (default F32) */
    @Getter
    @Setter
    private volatile int bankIdField = 32;

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

    /**
     * Creates a simulator engine with random available ports.
     */
    public FiscDualChannelSimulatorEngine() {
        this(0, 0);
    }

    /**
     * Creates a simulator engine with specified ports.
     *
     * @param receivePort the port where Sampler RECEIVES requests (0 for random)
     * @param sendPort the port where Sampler SENDS responses (0 for random)
     */
    public FiscDualChannelSimulatorEngine(int receivePort, int sendPort) {
        this.receivePort = receivePort;
        this.sendPort = sendPort;
        this.messageFactory = new Iso8583MessageFactory();
        this.messageParser = new FiscMessageParser(false);
        this.messageAssembler = new FiscMessageAssembler(true);
        initializeDefaultHandlers();
    }

    /**
     * Gets the actual Receive port after binding (where Sampler RECEIVES requests).
     */
    public int getReceivePort() {
        if (receiveServerChannel == null) {
            return receivePort;
        }
        return ((InetSocketAddress) receiveServerChannel.localAddress()).getPort();
    }

    /**
     * Gets the actual Send port after binding (where Sampler SENDS responses).
     */
    public int getSendPort() {
        if (sendServerChannel == null) {
            return sendPort;
        }
        return ((InetSocketAddress) sendServerChannel.localAddress()).getPort();
    }

    /**
     * Initializes default message handlers.
     */
    private void initializeDefaultHandlers() {
        // Network Management (0800)
        registerHandler("0800", request -> {
            String networkCode = request.getFieldAsString(70);
            log.info("[Engine] Network management: code={}", networkCode);

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            response.setField(70, networkCode);
            return response;
        });

        // Financial Request (0200)
        registerHandler("0200", request -> {
            String processingCode = request.getFieldAsString(3);
            String amount = request.getFieldAsString(4);
            log.info("[Engine] Financial request: code={}, amount={}", processingCode, amount);

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            return response;
        });

        // Reversal Request (0400)
        registerHandler("0400", request -> {
            log.info("[Engine] Reversal request: STAN={}", request.getFieldAsString(11));

            Iso8583Message response = request.createResponse();
            response.setField(39, defaultResponseCode);
            return response;
        });
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
            CompletableFuture<Void> receiveFuture = startServer("RECEIVE", receivePort, true);
            CompletableFuture<Void> sendFuture = startServer("SEND", sendPort, false);

            CompletableFuture.allOf(receiveFuture, sendFuture)
                .thenRun(() -> {
                    startResponseSender();
                    log.info("[Engine] Dual-channel started: ReceivePort={}, SendPort={}",
                        getReceivePort(), getSendPort());
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
    private CompletableFuture<Void> startServer(String name, int port, boolean isReceiveChannel) {
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
                    pipeline.addLast("decoder", new EngineDecoder());
                    pipeline.addLast("encoder", new EngineEncoder());
                    if (isReceiveChannel) {
                        pipeline.addLast("handler", new ReceiveChannelHandler());
                    } else {
                        pipeline.addLast("handler", new SendChannelHandler());
                    }
                }
            });

        bootstrap.bind(port).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                if (isReceiveChannel) {
                    receiveServerChannel = f.channel();
                } else {
                    sendServerChannel = f.channel();
                }
                int boundPort = ((InetSocketAddress) f.channel().localAddress()).getPort();
                log.info("[Engine] {} port bound: {}", name, boundPort);
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
                        if (enableBankIdRouting && pending.bankId != null) {
                            Channel targetChannel = clientRouter.getSendChannel(pending.bankId);
                            if (targetChannel != null && targetChannel.isActive()) {
                                targetChannel.writeAndFlush(pending.response).sync();
                                messagesSent.incrementAndGet();
                                log.debug("[Engine] Sent response via Bank ID routing: bankId={}, STAN={}",
                                    pending.bankId, pending.response.getFieldAsString(11));
                                sent = true;
                            }
                        }

                        // Fallback to first available client
                        if (!sent) {
                            Channel firstClient = clientRouter.getFirstAvailableSendChannel();
                            if (firstClient != null && firstClient.isActive()) {
                                firstClient.writeAndFlush(pending.response).sync();
                                messagesSent.incrementAndGet();
                                log.debug("[Engine] Sent response to first available client: STAN={}",
                                    pending.response.getFieldAsString(11));
                                sent = true;
                            }
                        }

                        if (!sent) {
                            log.warn("[Engine] No response channel client connected, response dropped: STAN={}",
                                pending.response.getFieldAsString(11));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[Engine] Error sending response", e);
                }
            }
        }, "response-sender");
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
        if (receiveServerChannel != null) {
            closeFutures.add(receiveServerChannel.close());
        }
        if (sendServerChannel != null) {
            closeFutures.add(sendServerChannel.close());
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
        log.info("[Engine] Dual-channel simulator engine stopped");
        future.complete(null);
    }

    /**
     * Sends a proactive message to a specific client by Bank ID.
     *
     * @param bankId the target bank ID
     * @param message the message to send
     * @return true if sent successfully
     */
    public boolean sendProactiveMessage(String bankId, Iso8583Message message) {
        Channel channel = clientRouter.getSendChannel(bankId);
        if (channel != null && channel.isActive()) {
            try {
                channel.writeAndFlush(message).sync();
                messagesSent.incrementAndGet();
                log.info("[Engine] Sent proactive message to bankId={}: MTI={}",
                    bankId, message.getMti());
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[Engine] Failed to send proactive message to bankId={}", bankId, e);
            }
        }
        return false;
    }

    /**
     * Broadcasts a proactive message to all connected clients.
     *
     * @param message the message to broadcast
     * @return number of clients the message was sent to
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
        log.info("[Engine] Broadcast message to {} clients: MTI={}", sentCount, message.getMti());
        return sentCount;
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
    private record PendingResponse(Iso8583Message response, String bankId) {}

    /**
     * Received request wrapper.
     */
    public record ReceivedRequest(
        Iso8583Message message,
        String bankId,
        long timestamp,
        String validationResult
    ) {}

    /**
     * ISO 8583 Message Decoder.
     */
    private class EngineDecoder extends ByteToMessageDecoder {

        private static final int MAX_MESSAGE_SIZE = 9999;
        private static final int MIN_MESSAGE_SIZE = 10; // MTI (2) + Bitmap (8) minimum

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 2) {
                return;
            }

            in.markReaderIndex();

            // Read length prefix
            byte b1 = in.readByte();
            byte b2 = in.readByte();
            int length = readBcdLength(b1, b2);

            // Validate length
            if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                log.error("[Engine] Invalid message length: {} (raw: 0x{}{}) - discarding {} bytes",
                    length, String.format("%02X", b1), String.format("%02X", b2), in.readableBytes());
                logRawBytes(in, Math.min(50, in.readableBytes()));
                in.skipBytes(in.readableBytes()); // Discard all data
                return;
            }

            // Check for suspiciously small messages
            if (length < MIN_MESSAGE_SIZE) {
                log.warn("[Engine] Message too short ({} bytes), minimum is {} - skipping",
                    length, MIN_MESSAGE_SIZE);
                if (in.readableBytes() >= length) {
                    in.skipBytes(length);
                }
                return;
            }

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
                // Log at WARN level for common malformed messages to reduce noise
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Not enough bytes") || errorMsg.contains("Failed to decode"))) {
                    log.warn("[Engine] Skipping malformed message ({} bytes): {}", length, errorMsg);
                    if (log.isDebugEnabled()) {
                        logRawBytes(data);
                    }
                } else {
                    log.error("[Engine] Decode error: {} - message length={}", errorMsg, length);
                    logRawBytes(data);
                }
                // Don't throw - just skip this malformed message
            }
        }

        private int readBcdLength(byte b1, byte b2) {
            int h1 = (b1 >> 4) & 0x0F;
            int l1 = b1 & 0x0F;
            int h2 = (b2 >> 4) & 0x0F;
            int l2 = b2 & 0x0F;
            return h1 * 1000 + l1 * 100 + h2 * 10 + l2;
        }

        private void logRawBytes(ByteBuf buf, int maxBytes) {
            byte[] preview = new byte[maxBytes];
            buf.getBytes(buf.readerIndex(), preview);
            logRawBytes(preview);
        }

        private void logRawBytes(byte[] data) {
            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            int limit = Math.min(data.length, 100);

            for (int i = 0; i < limit; i++) {
                hex.append(String.format("%02X ", data[i]));
                char c = (char) (data[i] & 0xFF);
                ascii.append(c >= 32 && c < 127 ? c : '.');
            }

            if (data.length > limit) {
                hex.append("...");
                ascii.append("...");
            }

            log.error("[Engine] Raw bytes ({}): HEX=[{}] ASCII=[{}]", data.length, hex.toString().trim(), ascii);
        }
    }

    /**
     * ISO 8583 Message Encoder.
     */
    private class EngineEncoder extends MessageToByteEncoder<Iso8583Message> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) {
            byte[] data = messageAssembler.assemble(msg);
            out.writeBytes(data);
        }
    }

    /**
     * Handler for Receive channel (Sampler RECEIVES requests from clients here).
     */
    private class ReceiveChannelHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        private String channelId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channelId = ctx.channel().remoteAddress().toString();
            int count = receiveChannelClientCount.incrementAndGet();
            clientRouter.registerReceiveChannel(channelId, ctx.channel());
            log.info("[Engine] Receive channel client connected: {} (total: {})", channelId, count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            int count = receiveChannelClientCount.decrementAndGet();
            clientRouter.unregisterReceiveChannel(channelId);
            log.info("[Engine] Receive channel client disconnected: {} (total: {})", channelId, count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message request) {
            String mti = request.getMti();
            String stan = request.getFieldAsString(11);
            String bankId = request.getFieldAsString(bankIdField);

            messagesReceived.incrementAndGet();
            lastRequestMti = mti;
            lastRequestStan = stan;

            log.debug("[Engine] Received on Receive channel: MTI={}, STAN={}, BankId={}", mti, stan, bankId);

            // Register Bank ID mapping for routing
            if (enableBankIdRouting && bankId != null && !bankId.isEmpty()) {
                clientRouter.associateBankIdWithChannel(bankId, channelId);
            }

            // Validate request
            String validationError = null;
            if (validationCallback != null) {
                validationError = validationCallback.apply(request);
                if (validationError != null) {
                    validationErrors.incrementAndGet();
                    lastValidationResult = "FAIL: " + validationError;
                    log.warn("[Engine] Validation failed for MTI={}, STAN={}: {}",
                        mti, stan, validationError);
                } else {
                    lastValidationResult = "PASS";
                }
            } else {
                lastValidationResult = "SKIP";
            }

            // Store received request
            requestQueue.offer(new ReceivedRequest(request, bankId, System.currentTimeMillis(), lastValidationResult));

            // Notify callback
            if (requestReceivedCallback != null) {
                requestReceivedCallback.accept(request, lastValidationResult);
            }

            // If validation failed, return error response
            if (validationError != null) {
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, validationErrorCode);
                responseQueue.offer(new PendingResponse(errorResponse, bankId));
                return;
            }

            // Find handler and create response
            Function<Iso8583Message, Iso8583Message> handler = requestHandlers.get(mti);
            if (handler != null) {
                try {
                    Iso8583Message response = handler.apply(request);
                    responseQueue.offer(new PendingResponse(response, bankId));
                    log.debug("[Engine] Response queued: MTI={}, STAN={}", response.getMti(), stan);
                } catch (Exception e) {
                    log.error("[Engine] Handler error for MTI={}", mti, e);
                    Iso8583Message errorResponse = request.createResponse();
                    errorResponse.setField(39, "96");
                    responseQueue.offer(new PendingResponse(errorResponse, bankId));
                }
            } else {
                log.warn("[Engine] No handler for MTI={}", mti);
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, "12");
                responseQueue.offer(new PendingResponse(errorResponse, bankId));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[Engine] Receive channel exception", cause);
            ctx.close();
        }
    }

    /**
     * Handler for Send channel (Sampler SENDS responses to clients here).
     */
    private class SendChannelHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        private String channelId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channelId = ctx.channel().remoteAddress().toString();
            clientRouter.registerSendChannel(channelId, ctx.channel());
            int count = sendChannelClientCount.incrementAndGet();
            log.info("[Engine] Send channel client connected: {} (total: {})", channelId, count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            clientRouter.unregisterSendChannel(channelId);
            int count = sendChannelClientCount.decrementAndGet();
            log.info("[Engine] Send channel client disconnected: {} (total: {})", channelId, count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message msg) {
            // Send channel shouldn't receive messages from client in dual-channel mode
            // But some clients might send a registration/hello message - just log and ignore
            log.warn("[Engine] Unexpected message on Send channel from {}: MTI={}, STAN={}",
                channelId, msg.getMti(), msg.getFieldAsString(11));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[Engine] Send channel exception from {}: {}", channelId, cause.getMessage());
            // Don't close the channel on decode errors - just log and continue
            if (cause.getMessage() != null && cause.getMessage().contains("parse")) {
                log.warn("[Engine] Parse error on send channel - client may be sending invalid data");
            } else {
                ctx.close();
            }
        }
    }
}
