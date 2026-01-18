package com.fep.communication.server;

import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.communication.client.DualChannelState;
import com.fep.communication.config.DualChannelConfig;
import com.fep.communication.codec.FiscMessageDecoder;
import com.fep.communication.codec.FiscMessageEncoder;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.service.ChannelMessageService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * FISC Dual-Channel TCP Server for accepting incoming connections.
 *
 * <p>In server mode, FEP listens on two ports:
 * <ul>
 *   <li><b>Send Port</b>: Accepts connections from clients to receive their requests</li>
 *   <li><b>Receive Port</b>: Accepts connections from clients to send responses back</li>
 * </ul>
 *
 * <p>This is used when external systems (ATM, POS) connect to FEP.
 *
 * <p>Message flow (from ATM perspective):
 * <pre>
 *     ATM                         FEP Server
 *     ---                         ----------
 *     Send Channel ──request──►   Send Port (19001)
 *                                 │
 *                                 ▼ (process request)
 *                                 │
 *     Receive Channel ◄──resp──   Receive Port (19002)
 * </pre>
 *
 * <p>Usage:
 * <pre>{@code
 * DualChannelConfig config = DualChannelConfig.builder()
 *     .sendPort(19001)
 *     .receivePort(19002)
 *     .connectionMode(ConnectionMode.SERVER)
 *     .channelId("ATM_FISC_V1")
 *     .build();
 *
 * FiscDualChannelServer server = new FiscDualChannelServer(config);
 * server.setMessageHandler((clientId, message) -> {
 *     // Process request and return response
 *     return processMessage(message);
 * });
 * server.start().get();
 * }</pre>
 */
@Slf4j
public class FiscDualChannelServer implements AutoCloseable {

    private final DualChannelConfig config;
    private final String channelId;

    // Netty components
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel sendServerChannel;
    private Channel receiveServerChannel;

    // Client connection management
    private final Map<String, ClientConnection> clientConnections = new ConcurrentHashMap<>();

    // State
    @Getter
    private volatile DualChannelState state = DualChannelState.DISCONNECTED;

    // Message handling
    @Setter
    private BiConsumer<String, Iso8583Message> messageHandler;

    @Setter
    private ChannelMessageService channelMessageService;

    // Listeners
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<ServerClientListener> clientListeners = new CopyOnWriteArrayList<>();

    // Statistics
    @Getter
    private final AtomicInteger totalClientsConnected = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesReceived = new AtomicInteger(0);
    @Getter
    private final AtomicInteger messagesSent = new AtomicInteger(0);

    /**
     * Creates a new dual-channel server.
     *
     * @param config the server configuration
     */
    public FiscDualChannelServer(DualChannelConfig config) {
        this.config = config;
        this.channelId = config.getChannelId() != null ? config.getChannelId() : "SERVER";
        log.info("[{}] FiscDualChannelServer created (sendPort={}, receivePort={})",
                channelId, config.getSendPort(), config.getReceivePort());
    }

    /**
     * Starts the dual-channel server.
     *
     * @return CompletableFuture that completes when both ports are bound
     */
    public CompletableFuture<Void> start() {
        if (state != DualChannelState.DISCONNECTED) {
            log.warn("[{}] Server already starting or started, state={}", channelId, state);
            return CompletableFuture.completedFuture(null);
        }

        setState(DualChannelState.CONNECTING);
        CompletableFuture<Void> future = new CompletableFuture<>();

        bossGroup = new NioEventLoopGroup(2);
        workerGroup = new NioEventLoopGroup();

        try {
            CompletableFuture<Void> sendFuture = startServer("SEND", config.getSendPort(), true);
            CompletableFuture<Void> receiveFuture = startServer("RECEIVE", config.getReceivePort(), false);

            CompletableFuture.allOf(sendFuture, receiveFuture)
                    .thenRun(() -> {
                        setState(DualChannelState.BOTH_CONNECTED);
                        log.info("[{}] Dual-channel server started: SendPort={}, ReceivePort={}",
                                channelId, getActualSendPort(), getActualReceivePort());
                        notifyConnected();
                        future.complete(null);
                    })
                    .exceptionally(ex -> {
                        setState(DualChannelState.FAILED);
                        log.error("[{}] Failed to start server", channelId, ex);
                        shutdownEventLoops();
                        future.completeExceptionally(ex);
                        return null;
                    });

        } catch (Exception e) {
            setState(DualChannelState.FAILED);
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
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
                .childOption(ChannelOption.SO_RCVBUF, config.getReceiveBufferSize())
                .childOption(ChannelOption.SO_SNDBUF, config.getSendBufferSize())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("decoder", new FiscMessageDecoder());
                        pipeline.addLast("encoder", new FiscMessageEncoder());
                        if (isSendChannel) {
                            pipeline.addLast("handler", new SendPortHandler());
                        } else {
                            pipeline.addLast("handler", new ReceivePortHandler());
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
                log.info("[{}] {} port bound: {}", channelId, name, boundPort);
                future.complete(null);
            } else {
                log.error("[{}] Failed to bind {} port: {}", channelId, name, port);
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    /**
     * Stops the server.
     *
     * @return CompletableFuture that completes when server is stopped
     */
    public CompletableFuture<Void> stop() {
        if (state == DualChannelState.DISCONNECTED || state == DualChannelState.CLOSED) {
            return CompletableFuture.completedFuture(null);
        }

        setState(DualChannelState.CLOSING);
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Close all client connections
        for (ClientConnection client : clientConnections.values()) {
            try {
                if (client.sendChannel != null && client.sendChannel.isOpen()) {
                    client.sendChannel.close();
                }
                if (client.receiveChannel != null && client.receiveChannel.isOpen()) {
                    client.receiveChannel.close();
                }
            } catch (Exception e) {
                log.warn("[{}] Error closing client connection: {}", channelId, e.getMessage());
            }
        }
        clientConnections.clear();

        // Close server channels
        CompletableFuture<Void> closeFuture = CompletableFuture.runAsync(() -> {
            try {
                if (sendServerChannel != null && sendServerChannel.isOpen()) {
                    sendServerChannel.close().sync();
                }
                if (receiveServerChannel != null && receiveServerChannel.isOpen()) {
                    receiveServerChannel.close().sync();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        closeFuture.thenRun(() -> {
            shutdownEventLoops();
            setState(DualChannelState.CLOSED);
            log.info("[{}] Server stopped", channelId);
            notifyDisconnected();
            future.complete(null);
        });

        return future;
    }

    private void shutdownEventLoops() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Sends a message to a specific client.
     *
     * @param clientId the client identifier
     * @param message  the message to send
     * @return true if sent successfully
     */
    public boolean sendToClient(String clientId, Iso8583Message message) {
        ClientConnection client = clientConnections.get(clientId);
        if (client == null || client.receiveChannel == null || !client.receiveChannel.isActive()) {
            log.warn("[{}] Cannot send to client {}: not connected", channelId, clientId);
            return false;
        }

        try {
            client.receiveChannel.writeAndFlush(message).sync();
            messagesSent.incrementAndGet();
            log.debug("[{}] Sent message to client {}: MTI={}", channelId, clientId, message.getMti());
            return true;
        } catch (Exception e) {
            log.error("[{}] Failed to send to client {}", channelId, clientId, e);
            return false;
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     *
     * @param message the message to broadcast
     * @return number of clients the message was sent to
     */
    public int broadcast(Iso8583Message message) {
        int sentCount = 0;
        for (Map.Entry<String, ClientConnection> entry : clientConnections.entrySet()) {
            if (sendToClient(entry.getKey(), message)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    /**
     * Gets the actual Send port after binding.
     */
    public int getActualSendPort() {
        if (sendServerChannel == null) {
            return config.getSendPort();
        }
        return ((InetSocketAddress) sendServerChannel.localAddress()).getPort();
    }

    /**
     * Gets the actual Receive port after binding.
     */
    public int getActualReceivePort() {
        if (receiveServerChannel == null) {
            return config.getReceivePort();
        }
        return ((InetSocketAddress) receiveServerChannel.localAddress()).getPort();
    }

    /**
     * Gets the number of connected clients.
     */
    public int getConnectedClientCount() {
        return (int) clientConnections.values().stream()
                .filter(c -> c.sendChannel != null && c.sendChannel.isActive())
                .count();
    }

    /**
     * Gets all connected client IDs.
     */
    public java.util.Set<String> getConnectedClientIds() {
        return new java.util.HashSet<>(clientConnections.keySet());
    }

    /**
     * Gets a client connection by ID.
     */
    public Optional<ClientConnection> getClient(String clientId) {
        return Optional.ofNullable(clientConnections.get(clientId));
    }

    // ==================== Listener Management ====================

    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    public void addClientListener(ServerClientListener listener) {
        clientListeners.add(listener);
    }

    public void removeClientListener(ServerClientListener listener) {
        clientListeners.remove(listener);
    }

    // ==================== State Management ====================

    private void setState(DualChannelState newState) {
        DualChannelState oldState = this.state;
        this.state = newState;
        log.info("[{}] State changed: {} -> {}", channelId, oldState, newState);
        notifyStateChanged(oldState, newState);
    }

    public boolean isRunning() {
        return state == DualChannelState.BOTH_CONNECTED || state == DualChannelState.SIGNED_ON;
    }

    // ==================== Notifications ====================

    private void notifyConnected() {
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onConnected(channelId);
            } catch (Exception e) {
                log.warn("[{}] Error in connection listener", channelId, e);
            }
        }
    }

    private void notifyDisconnected() {
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onDisconnected(channelId, null);
            } catch (Exception e) {
                log.warn("[{}] Error in connection listener", channelId, e);
            }
        }
    }

    private void notifyStateChanged(DualChannelState oldState, DualChannelState newState) {
        for (ConnectionListener listener : connectionListeners) {
            try {
                ConnectionState oldConnState = mapToConnectionState(oldState);
                ConnectionState newConnState = mapToConnectionState(newState);
                listener.onStateChanged(channelId, oldConnState, newConnState);
            } catch (Exception e) {
                log.warn("[{}] Error in connection listener", channelId, e);
            }
        }
    }

    /**
     * Maps DualChannelState to ConnectionState for listener compatibility.
     */
    private ConnectionState mapToConnectionState(DualChannelState dualState) {
        if (dualState == null) {
            return ConnectionState.DISCONNECTED;
        }
        return switch (dualState) {
            case DISCONNECTED -> ConnectionState.DISCONNECTED;
            case CONNECTING -> ConnectionState.CONNECTING;
            case SEND_ONLY_CONNECTED, RECEIVE_ONLY_CONNECTED -> ConnectionState.CONNECTING;
            case BOTH_CONNECTED -> ConnectionState.CONNECTED;
            case SIGNED_ON -> ConnectionState.SIGNED_ON;
            case RECONNECTING -> ConnectionState.RECONNECTING;
            case CLOSING -> ConnectionState.CLOSING;
            case CLOSED -> ConnectionState.CLOSED;
            case FAILED -> ConnectionState.FAILED;
        };
    }

    private void notifyClientConnected(String clientId, String remoteAddress) {
        for (ServerClientListener listener : clientListeners) {
            try {
                listener.onClientConnected(clientId, remoteAddress);
            } catch (Exception e) {
                log.warn("[{}] Error in client listener", channelId, e);
            }
        }
    }

    private void notifyClientDisconnected(String clientId) {
        for (ServerClientListener listener : clientListeners) {
            try {
                listener.onClientDisconnected(clientId);
            } catch (Exception e) {
                log.warn("[{}] Error in client listener", channelId, e);
            }
        }
    }

    private void notifyMessageReceived(String clientId, Iso8583Message message) {
        for (ServerClientListener listener : clientListeners) {
            try {
                listener.onMessageReceived(clientId, message);
            } catch (Exception e) {
                log.warn("[{}] Error in client listener", channelId, e);
            }
        }
    }

    @Override
    public void close() {
        stop().join();
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a connected client.
     */
    @Getter
    public static class ClientConnection {
        private final String clientId;
        private final String remoteAddress;
        private Channel sendChannel;    // Client's connection to our Send port
        private Channel receiveChannel; // Client's connection to our Receive port
        private final long connectedAt = System.currentTimeMillis();

        public ClientConnection(String clientId, String remoteAddress) {
            this.clientId = clientId;
            this.remoteAddress = remoteAddress;
        }

        public boolean isFullyConnected() {
            return sendChannel != null && sendChannel.isActive()
                    && receiveChannel != null && receiveChannel.isActive();
        }
    }

    /**
     * Handler for Send port (receives requests from clients).
     */
    private class SendPortHandler extends SimpleChannelInboundHandler<Iso8583Message> {
        private String clientId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String remoteAddr = ctx.channel().remoteAddress().toString();
            // Extract IP:port as client ID
            clientId = remoteAddr.replaceAll("[^0-9.:]", "");

            ClientConnection client = clientConnections.computeIfAbsent(clientId,
                    id -> new ClientConnection(id, remoteAddr));
            client.sendChannel = ctx.channel();

            totalClientsConnected.incrementAndGet();
            log.info("[{}] Client connected to Send port: {} (total: {})",
                    channelId, clientId, getConnectedClientCount());
            notifyClientConnected(clientId, remoteAddr);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("[{}] Client disconnected from Send port: {}", channelId, clientId);
            ClientConnection client = clientConnections.get(clientId);
            if (client != null) {
                client.sendChannel = null;
                // If both channels are gone, remove the client
                if (client.receiveChannel == null || !client.receiveChannel.isActive()) {
                    clientConnections.remove(clientId);
                    notifyClientDisconnected(clientId);
                }
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message message) {
            messagesReceived.incrementAndGet();
            log.debug("[{}] Received from client {}: MTI={}, STAN={}",
                    channelId, clientId, message.getMti(), message.getFieldAsString(11));

            notifyMessageReceived(clientId, message);

            // Process message if handler is set
            if (messageHandler != null) {
                try {
                    messageHandler.accept(clientId, message);
                } catch (Exception e) {
                    log.error("[{}] Error processing message from {}", channelId, clientId, e);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[{}] Exception on Send port for client {}", channelId, clientId, cause);
            ctx.close();
        }
    }

    /**
     * Handler for Receive port (sends responses to clients).
     */
    private class ReceivePortHandler extends SimpleChannelInboundHandler<Iso8583Message> {
        private String clientId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String remoteAddr = ctx.channel().remoteAddress().toString();
            clientId = remoteAddr.replaceAll("[^0-9.:]", "");

            ClientConnection client = clientConnections.computeIfAbsent(clientId,
                    id -> new ClientConnection(id, remoteAddr));
            client.receiveChannel = ctx.channel();

            log.info("[{}] Client connected to Receive port: {}", channelId, clientId);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("[{}] Client disconnected from Receive port: {}", channelId, clientId);
            ClientConnection client = clientConnections.get(clientId);
            if (client != null) {
                client.receiveChannel = null;
                // If both channels are gone, remove the client
                if (client.sendChannel == null || !client.sendChannel.isActive()) {
                    clientConnections.remove(clientId);
                    notifyClientDisconnected(clientId);
                }
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message message) {
            // In dual-channel mode, Receive port is for sending, not receiving
            // But handle gracefully if client sends something
            log.warn("[{}] Unexpected message on Receive port from {}: MTI={}",
                    channelId, clientId, message.getMti());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[{}] Exception on Receive port for client {}", channelId, clientId, cause);
            ctx.close();
        }
    }
}
