package com.fep.integration.fisc;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import com.fep.message.iso8583.parser.FiscMessageParser;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * FISC Connection Simulator for integration testing.
 *
 * <p>Simulates FISC server behavior including:
 * <ul>
 *   <li>TCP/IP server socket</li>
 *   <li>ISO 8583 message encoding/decoding</li>
 *   <li>Network management (0800/0810)</li>
 *   <li>Financial transactions (0200/0210)</li>
 *   <li>Reversals (0400/0410)</li>
 *   <li>Configurable response handlers</li>
 * </ul>
 */
@Slf4j
public class FiscConnectionSimulator implements AutoCloseable {

    private final int port;
    private final Iso8583MessageFactory messageFactory;
    private final FiscMessageParser messageParser;
    private final FiscMessageAssembler messageAssembler;

    @Getter
    private final Map<String, Function<Iso8583Message, Iso8583Message>> requestHandlers = new ConcurrentHashMap<>();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Getter
    private final java.util.concurrent.atomic.AtomicInteger connectedClients = new java.util.concurrent.atomic.AtomicInteger(0);

    @Getter
    private final java.util.concurrent.atomic.AtomicInteger messagesReceived = new java.util.concurrent.atomic.AtomicInteger(0);

    @Getter
    private final java.util.concurrent.atomic.AtomicInteger messagesSent = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * Creates a simulator on a random available port.
     */
    public FiscConnectionSimulator() {
        this(0); // 0 = random port
    }

    /**
     * Creates a simulator on the specified port.
     *
     * @param port the port to bind to (0 for random)
     */
    public FiscConnectionSimulator(int port) {
        this.port = port;
        this.messageFactory = new Iso8583MessageFactory();
        this.messageParser = new FiscMessageParser(false); // No length prefix in decoder
        this.messageAssembler = new FiscMessageAssembler(true); // Include length prefix in encoder
        initializeDefaultHandlers();
    }

    /**
     * Gets the actual port the server is bound to.
     */
    public int getPort() {
        if (serverChannel == null) {
            return port;
        }
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    /**
     * Initializes default message handlers.
     */
    private void initializeDefaultHandlers() {
        // Network Management - Sign On (0800 code=001)
        registerHandler("0800", request -> {
            String networkCode = request.getFieldAsString(70);
            log.info("Received network management request: code={}", networkCode);

            Iso8583Message response = request.createResponse();
            response.setField(39, "00"); // Approved
            response.setField(70, networkCode);
            return response;
        });

        // Financial Request (0200)
        registerHandler("0200", request -> {
            String processingCode = request.getFieldAsString(3);
            String amount = request.getFieldAsString(4);
            log.info("Received financial request: processingCode={}, amount={}",
                processingCode, amount);

            Iso8583Message response = request.createResponse();
            response.setField(39, "00"); // Approved
            return response;
        });

        // Reversal Request (0400)
        registerHandler("0400", request -> {
            String originalStan = request.getFieldAsString(11);
            log.info("Received reversal request: originalStan={}", originalStan);

            Iso8583Message response = request.createResponse();
            response.setField(39, "00"); // Approved
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
        log.debug("Registered handler for MTI: {}", mti);
    }

    /**
     * Starts the simulator server.
     *
     * @return CompletableFuture that completes when server is started
     */
    public CompletableFuture<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
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
                        pipeline.addLast("handler", new SimulatorHandler());
                    }
                });

            ChannelFuture bindFuture = bootstrap.bind(port);
            bindFuture.addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    serverChannel = f.channel();
                    int boundPort = getPort();
                    log.info("FISC simulator started on port {}", boundPort);
                    future.complete(null);
                } else {
                    log.error("Failed to start FISC simulator: {}", f.cause().getMessage());
                    started.set(false);
                    future.completeExceptionally(f.cause());
                }
            });

        } catch (Exception e) {
            log.error("Error starting FISC simulator", e);
            started.set(false);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Stops the simulator server.
     */
    public CompletableFuture<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        log.info("Stopping FISC simulator on port {}", getPort());

        if (serverChannel != null) {
            serverChannel.close().addListener(f -> {
                if (workerGroup != null) {
                    workerGroup.shutdownGracefully();
                }
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully();
                }
                future.complete(null);
            });
        } else {
            future.complete(null);
        }

        return future;
    }

    /**
     * Resets statistics counters.
     */
    public void resetCounters() {
        messagesReceived.set(0);
        messagesSent.set(0);
    }

    /**
     * Gets the number of connected clients.
     */
    public int getConnectedClientsCount() {
        return connectedClients.get();
    }

    /**
     * Gets the number of messages received.
     */
    public int getMessagesReceivedCount() {
        return messagesReceived.get();
    }

    /**
     * Gets the number of messages sent.
     */
    public int getMessagesSentCount() {
        return messagesSent.get();
    }

    @Override
    public void close() {
        stop().join();
    }

    /**
     * ISO 8583 Message Decoder for simulator.
     */
    private class SimulatorDecoder extends ByteToMessageDecoder {

        private static final int LENGTH_PREFIX_SIZE = 2;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < LENGTH_PREFIX_SIZE) {
                return;
            }

            in.markReaderIndex();
            int messageLength = readBcdLength(in);

            if (in.readableBytes() < messageLength) {
                in.resetReaderIndex();
                return;
            }

            byte[] messageData = new byte[messageLength];
            in.readBytes(messageData);

            try {
                Iso8583Message message = messageParser.parse(messageData);
                out.add(message);
                messagesReceived.incrementAndGet();
                log.debug("Decoded message: MTI={}, STAN={}",
                    message.getMti(), message.getFieldAsString(11));
            } catch (Exception e) {
                log.error("Failed to decode message", e);
                throw e;
            }
        }

        private int readBcdLength(ByteBuf in) {
            byte b1 = in.readByte();
            byte b2 = in.readByte();
            int high1 = (b1 >> 4) & 0x0F;
            int low1 = b1 & 0x0F;
            int high2 = (b2 >> 4) & 0x0F;
            int low2 = b2 & 0x0F;
            return high1 * 1000 + low1 * 100 + high2 * 10 + low2;
        }
    }

    /**
     * ISO 8583 Message Encoder for simulator.
     */
    private class SimulatorEncoder extends MessageToByteEncoder<Iso8583Message> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) {
            byte[] data = messageAssembler.assemble(msg);
            out.writeBytes(data);
            messagesSent.incrementAndGet();
            log.debug("Encoded message: MTI={}, size={} bytes",
                msg.getMti(), data.length);
        }
    }

    /**
     * Business logic handler for simulator.
     */
    private class SimulatorHandler extends SimpleChannelInboundHandler<Iso8583Message> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            int count = connectedClients.incrementAndGet();
            log.info("Client connected: {} (total: {})",
                ctx.channel().remoteAddress(), count);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            int count = connectedClients.decrementAndGet();
            log.info("Client disconnected: {} (total: {})",
                ctx.channel().remoteAddress(), count);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message request) {
            String mti = request.getMti();
            String stan = request.getFieldAsString(11);

            log.debug("Received request: MTI={}, STAN={}", mti, stan);

            // Find handler for this MTI
            Function<Iso8583Message, Iso8583Message> handler = requestHandlers.get(mti);

            if (handler != null) {
                try {
                    Iso8583Message response = handler.apply(request);
                    ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            log.debug("Sent response: MTI={}, STAN={}",
                                response.getMti(), response.getFieldAsString(11));
                        } else {
                            log.error("Failed to send response", f.cause());
                        }
                    });
                } catch (Exception e) {
                    log.error("Error handling request MTI={}", mti, e);
                    // Send error response
                    Iso8583Message errorResponse = request.createResponse();
                    errorResponse.setField(39, "96"); // System error
                    ctx.writeAndFlush(errorResponse);
                }
            } else {
                log.warn("No handler registered for MTI: {}", mti);
                // Send unsupported response
                Iso8583Message errorResponse = request.createResponse();
                errorResponse.setField(39, "12"); // Invalid transaction
                ctx.writeAndFlush(errorResponse);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Exception in simulator handler", cause);
            ctx.close();
        }
    }
}
