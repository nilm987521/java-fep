package com.fep.integration.fisc;

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
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * FISC Dual-Channel Connection Simulator for integration testing.
 *
 * <p>Simulates FISC dual-channel server behavior with:
 * <ul>
 *   <li><b>Send Port</b>: Receives all requests from clients</li>
 *   <li><b>Receive Port</b>: Sends all responses to clients</li>
 * </ul>
 *
 * <p>This accurately models the FISC production environment where
 * requests and responses flow through separate TCP connections.
 *
 * <p>Message flow:
 * <pre>
 *     Client                    Simulator
 *     ------                    ---------
 *     Send Channel ──request──► Send Port (receives requests)
 *                               │
 *                               ▼ (internal queue)
 *                               │
 *     Receive Channel ◄──resp── Receive Port (sends responses)
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * FiscDualChannelSimulator simulator = new FiscDualChannelSimulator();
 * simulator.start().get();
 *
 * System.out.println("Send Port: " + simulator.getSendPort());
 * System.out.println("Receive Port: " + simulator.getReceivePort());
 *
 * // Configure response handlers
 * simulator.registerHandler("0200", request -> {
 *     Iso8583Message response = request.createResponse();
 *     response.setField(39, "00");
 *     return response;
 * });
 *
 * // ... run tests ...
 *
 * simulator.close();
 * }</pre>
 */
@Slf4j
public class FiscDualChannelSimulator implements AutoCloseable {

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

    /** Connected receive channel clients */
    private final Map<String, Channel> receiveChannelClients = new ConcurrentHashMap<>();

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
    private final AtomicInteger sendChannelClients = new AtomicInteger(0);
    @Getter
    private final AtomicInteger receiveChannelClients = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesReceived = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesSent = new AtomicInteger(0);

    /** Delay in milliseconds before sending response (simulates processing time) */
    @Getter
    private volatile long responseDelayMs = 0;

    /**
     * Creates a simulator with random available ports.
     */
    public FiscDualChannelSimulator() {
        this(0, 0);
    }

    /**
     * Creates a simulator with specified ports.
     *
     * @param sendPort the port for Send channel (0 for random)
     * @param receivePort the port for Receive channel (0 for random)
     */
    public FiscDualChannelSimulator(int sendPort, int receivePort) {
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
     * Sets the response delay (for testing timeout scenarios).
     *
     * @param delayMs delay in milliseconds
     */
    public void setResponseDelayMs(long delayMs) {
        this.responseDelayMs = delayMs;
    }

    /**
     * Initializes default message handlers.
     */
    private void initializeDefaultHandlers() {
        // Network Management (0800)
        registerHandler("0800", request -> {
            String networkCode = request.getFieldAsString(70);
            log.info("[Simulator] Network management: code={}", networkCode);

            Iso8583Message response = request.createResponse();
            response.setField(39, "00");
            response.setField(70, networkCode);
            return response;
        });

        // Financial Request (0200)
        registerHandler("0200", request -> {
            String processingCode = request.getFieldAsString(3);
            String amount = request.getFieldAsString(4);
            log.info("[Simulator] Financial request: code={}, amount={}", processingCode, amount);

            Iso8583Message response = request.createResponse();
            response.setField(39, "00");
            return response;
        });

        // Reversal Request (0400)
        registerHandler("0400", request -> {
            log.info("[Simulator] Reversal request: STAN={}", request.getFieldAsString(11));

            Iso8583Message response = request.createResponse();
            response.setField(39, "00");
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
     * Starts the dual-channel simulator.
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
                    log.info("[Simulator] Dual-channel started: Send={}, Receive={}",
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
                    pipeline.addLast("decoder", new SimulatorDecoder());
                    pipeline.addLast("encoder", new SimulatorEncoder());
                    if (isSendChannel) {
                        pipeline.addLast("handler", new SendChannelServerHandler());
                    } else {
                        pipeline.addLast("handler", new ReceiveChannelServerHandler());
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
                log.info("[Simulator] {} port bound: {}", name, boundPort);
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

                        // Send to any connected receive channel client
                        boolean sent = false;
                        for (Channel client : receiveChannelClients.values()) {
                            if (client.isActive()) {
                                client.writeAndFlush(pending.response).sync();
                                messagesSent.incrementAndGet();
                                log.debug("[Simulator] Sent response via Receive channel: STAN={}",
                                    pending.response.getFieldAsString(11));
                                sent = true;
                                break; // Send to first available client
                            }
                        }
                        if (!sent) {
                            log.warn("[Simulator] No receive channel client connected, response dropped: STAN={}",
                                pending.response.getFieldAsString(11));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[Simulator] Error sending response", e);
                }
            }
        }, "response-sender");
        responseSenderThread.setDaemon(true);
        responseSenderThread.start();
    }

    /**
     * Stops the simulator.
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
        log.info("[Simulator] Dual-channel simulator stopped");
        future.complete(null);
    }

    /**
     * Manually sends a response (for testing unsolicited messages).
     *
     * @param response the response to send
     */
    public void sendResponse(Iso8583Message response) {
        responseQueue.offer(new PendingResponse(response));
    }

    /**
     * Waits for a request to arrive (for advanced test scenarios).
     *
     * @param timeoutMs timeout in milliseconds
     * @return the received request, or null if timeout
     */
    public Iso8583Message waitForRequest(long timeoutMs) throws InterruptedException {
        // This would require a request queue - simplified for now
        Thread.sleep(timeoutMs);
        return null;
    }

    /**
     * Resets statistics counters.
     */
    public void resetCounters() {
        messagesReceived.set(0);
        messagesSent.set(0);
    }

    @Override
    public void close() {
        stop().join();
    }

    // ==================== Inner Classes ====================

    /**
     * Pending response wrapper.
     */
    private record PendingResponse(Iso8583Message response) {}

    /**
     * ISO 8583 Message Decoder.
     */
    private class SimulatorDecoder extends ByteToMessageDecoder {

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
                log.error("[Simulator] Decode error", e);
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
     * ISO 8583 Message Encoder.
     */
    private class SimulatorEncoder extends MessageToByteEncoder<Iso8583Message> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) {
            byte[] data = messageAssembler.assemble(msg);
            out.writeBytes(data);
        }
    }

    /**
     * Handler for Send channel (receives requests).
     */
    private class SendChannelServerHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            int count = sendChannelClients.incrementAndGet();
            log.info("[Simulator] Send channel client connected: {} (total: {})",
                ctx.channel().remoteAddress(), count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            int count = sendChannelClients.decrementAndGet();
            log.info("[Simulator] Send channel client disconnected: {} (total: {})",
                ctx.channel().remoteAddress(), count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message request) {
            String mti = request.getMti();
            String stan = request.getFieldAsString(11);

            messagesReceived.incrementAndGet();
            log.debug("[Simulator] Received on Send channel: MTI={}, STAN={}", mti, stan);

            // Find handler and create response
            Function<Iso8583Message, Iso8583Message> handler = requestHandlers.get(mti);
            if (handler != null) {
                try {
                    Iso8583Message response = handler.apply(request);
                    // Queue response to be sent via Receive channel
                    responseQueue.offer(new PendingResponse(response));
                    log.debug("[Simulator] Response queued: MTI={}, STAN={}",
                        response.getMti(), stan);
                } catch (Exception e) {
                    log.error("[Simulator] Handler error for MTI={}", mti, e);
                    Iso8583Message errorResponse = request.createResponse();
                    errorResponse.setField(39, "96");
                    responseQueue.offer(new PendingResponse(errorResponse));
                }
            } else {
                log.warn("[Simulator] No handler for MTI={}", mti);
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, "12");
                responseQueue.offer(new PendingResponse(errorResponse));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[Simulator] Send channel exception", cause);
            ctx.close();
        }
    }

    /**
     * Handler for Receive channel (sends responses).
     */
    private class ReceiveChannelServerHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        private String clientId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            clientId = ctx.channel().remoteAddress().toString();
            receiveChannelClients.put(clientId, ctx.channel());
            int count = FiscDualChannelSimulator.this.receiveChannelClients.incrementAndGet();
            log.info("[Simulator] Receive channel client connected: {} (total: {})", clientId, count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            receiveChannelClients.remove(clientId);
            int count = FiscDualChannelSimulator.this.receiveChannelClients.decrementAndGet();
            log.info("[Simulator] Receive channel client disconnected: {} (total: {})", clientId, count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message msg) {
            // Receive channel shouldn't receive messages from client in dual-channel mode
            log.warn("[Simulator] Unexpected message on Receive channel: MTI={}",
                msg.getMti());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[Simulator] Receive channel exception", cause);
            ctx.close();
        }
    }
}
