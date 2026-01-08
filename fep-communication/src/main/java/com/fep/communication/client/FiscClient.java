package com.fep.communication.client;

import com.fep.communication.codec.FiscMessageDecoder;
import com.fep.communication.codec.FiscMessageEncoder;
import com.fep.communication.config.FiscConnectionConfig;
import com.fep.communication.exception.CommunicationException;
import com.fep.communication.handler.FiscClientHandler;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FISC TCP/IP client for connecting to Financial Information Service Center.
 *
 * <p>Features:
 * <ul>
 *   <li>TCP/IP connection with configurable timeouts</li>
 *   <li>Automatic heartbeat (echo test)</li>
 *   <li>Auto-reconnection on disconnection</li>
 *   <li>Failover to backup host</li>
 *   <li>Request/response matching via STAN</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * FiscConnectionConfig config = FiscConnectionConfig.builder()
 *     .primaryHost("fisc.example.com")
 *     .primaryPort(9000)
 *     .connectionName("FISC-MAIN")
 *     .build();
 *
 * FiscClient client = new FiscClient(config);
 * client.connect().get();
 * client.signOn().get();
 *
 * // Send transaction
 * Iso8583Message request = ...;
 * Iso8583Message response = client.sendAndReceive(request).get();
 * }</pre>
 */
@Slf4j
public class FiscClient implements AutoCloseable {

    private final FiscConnectionConfig config;
    private final Iso8583MessageFactory messageFactory;
    private final EventLoopGroup workerGroup;
    private final Bootstrap bootstrap;

    @Getter
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private volatile Channel channel;
    private volatile FiscClientHandler clientHandler;
    private volatile ConnectionListener listener;
    private volatile boolean usingBackup = false;

    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    /**
     * Creates a new FISC client with the specified configuration.
     *
     * @param config the connection configuration
     */
    public FiscClient(FiscConnectionConfig config) {
        this.config = config;
        this.messageFactory = new Iso8583MessageFactory();
        this.messageFactory.setInstitutionId(config.getInstitutionId());
        this.workerGroup = new NioEventLoopGroup();
        this.bootstrap = createBootstrap();
    }

    /**
     * Sets the connection event listener.
     *
     * @param listener the listener
     */
    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Creates the Netty bootstrap.
     */
    private Bootstrap createBootstrap() {
        return new Bootstrap()
            .group(workerGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive())
            .option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
            .option(ChannelOption.SO_RCVBUF, config.getReceiveBufferSize())
            .option(ChannelOption.SO_SNDBUF, config.getSendBufferSize())
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();

                    // Idle state handler for heartbeat
                    int idleSeconds = config.getIdleTimeoutMs() / 1000;
                    pipeline.addLast("idleStateHandler",
                        new IdleStateHandler(idleSeconds * 2, idleSeconds, 0));

                    // Message codec
                    pipeline.addLast("decoder", new FiscMessageDecoder());
                    pipeline.addLast("encoder", new FiscMessageEncoder());

                    // Business logic handler
                    clientHandler = new FiscClientHandler(
                        config.getConnectionName(),
                        listener,
                        FiscClient.this::onStateChanged
                    );
                    pipeline.addLast("handler", clientHandler);
                }
            });
    }

    /**
     * Connects to the FISC server.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> connect() {
        return connect(config.getPrimaryHost(), config.getPrimaryPort(), false);
    }

    /**
     * Connects to a specific host and port.
     */
    private CompletableFuture<Void> connect(String host, int port, boolean isBackup) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (state == ConnectionState.CONNECTED || state == ConnectionState.SIGNED_ON) {
            future.complete(null);
            return future;
        }

        updateState(ConnectionState.CONNECTING);
        log.info("[{}] Connecting to {}:{} (backup={})",
            config.getConnectionName(), host, port, isBackup);

        bootstrap.connect(host, port).addListener((ChannelFutureListener) connectFuture -> {
            if (connectFuture.isSuccess()) {
                channel = connectFuture.channel();
                usingBackup = isBackup;
                reconnectAttempts.set(0);
                log.info("[{}] Connected successfully to {}:{}",
                    config.getConnectionName(), host, port);
                future.complete(null);
            } else {
                Throwable cause = connectFuture.cause();
                log.error("[{}] Failed to connect to {}:{}: {}",
                    config.getConnectionName(), host, port, cause.getMessage());

                // Try backup if available and not already using it
                if (!isBackup && config.isUseBackupOnFailure() && config.hasBackupHost()) {
                    log.info("[{}] Trying backup host", config.getConnectionName());
                    connect(config.getBackupHost(), config.getBackupPort(), true)
                        .whenComplete((v, ex) -> {
                            if (ex != null) {
                                updateState(ConnectionState.FAILED);
                                future.completeExceptionally(
                                    CommunicationException.connectionFailed(host, port, cause));
                            } else {
                                future.complete(null);
                            }
                        });
                } else {
                    updateState(ConnectionState.FAILED);
                    future.completeExceptionally(
                        CommunicationException.connectionFailed(host, port, cause));
                }
            }
        });

        return future;
    }

    /**
     * Performs sign-on to FISC.
     *
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> signOn() {
        log.info("[{}] Signing on to FISC", config.getConnectionName());
        Iso8583Message signOnRequest = messageFactory.createSignOnMessage();
        return sendAndReceive(signOnRequest)
            .thenApply(response -> {
                String responseCode = response.getFieldAsString(39);
                if ("00".equals(responseCode)) {
                    log.info("[{}] Sign-on successful", config.getConnectionName());
                    if (clientHandler != null) {
                        clientHandler.setSignedOn();
                    }
                } else {
                    log.error("[{}] Sign-on failed with response code: {}",
                        config.getConnectionName(), responseCode);
                }
                return response;
            });
    }

    /**
     * Performs sign-off from FISC.
     *
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> signOff() {
        log.info("[{}] Signing off from FISC", config.getConnectionName());
        Iso8583Message signOffRequest = messageFactory.createSignOffMessage();
        return sendAndReceive(signOffRequest)
            .thenApply(response -> {
                log.info("[{}] Sign-off completed", config.getConnectionName());
                if (listener != null) {
                    listener.onSignedOff(config.getConnectionName());
                }
                return response;
            });
    }

    /**
     * Sends an echo test (heartbeat) message.
     *
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> sendEchoTest() {
        log.debug("[{}] Sending echo test", config.getConnectionName());
        Iso8583Message echoRequest = messageFactory.createEchoTestMessage();
        return sendAndReceive(echoRequest);
    }

    /**
     * Sends a message and waits for response.
     *
     * @param request the request message
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> sendAndReceive(Iso8583Message request) {
        return sendAndReceive(request, config.getReadTimeoutMs());
    }

    /**
     * Sends a message and waits for response with custom timeout.
     *
     * @param request the request message
     * @param timeoutMs timeout in milliseconds
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> sendAndReceive(Iso8583Message request, long timeoutMs) {
        if (channel == null || !channel.isActive()) {
            return CompletableFuture.failedFuture(CommunicationException.channelClosed());
        }

        // Ensure transaction fields are set
        if (!request.hasField(11)) {
            messageFactory.setTransactionFields(request);
        }

        String stan = request.getFieldAsString(11);

        // Register pending request before sending
        CompletableFuture<Iso8583Message> responseFuture =
            clientHandler.registerPendingRequest(stan, timeoutMs);

        // Send the request
        channel.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
            if (!writeFuture.isSuccess()) {
                responseFuture.completeExceptionally(
                    CommunicationException.sendFailed(
                        writeFuture.cause().getMessage(), writeFuture.cause()));
            }
        });

        return responseFuture;
    }

    /**
     * Sends a message without waiting for response (fire and forget).
     *
     * @param message the message to send
     * @return CompletableFuture that completes when sent
     */
    public CompletableFuture<Void> send(Iso8583Message message) {
        if (channel == null || !channel.isActive()) {
            return CompletableFuture.failedFuture(CommunicationException.channelClosed());
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.writeAndFlush(message).addListener((ChannelFutureListener) writeFuture -> {
            if (writeFuture.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(
                    CommunicationException.sendFailed(
                        writeFuture.cause().getMessage(), writeFuture.cause()));
            }
        });
        return future;
    }

    /**
     * Disconnects from the server.
     *
     * @return CompletableFuture that completes when disconnected
     */
    public CompletableFuture<Void> disconnect() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (channel == null || !channel.isActive()) {
            updateState(ConnectionState.DISCONNECTED);
            future.complete(null);
            return future;
        }

        updateState(ConnectionState.CLOSING);
        log.info("[{}] Disconnecting", config.getConnectionName());

        channel.close().addListener((ChannelFutureListener) closeFuture -> {
            updateState(ConnectionState.DISCONNECTED);
            log.info("[{}] Disconnected", config.getConnectionName());
            future.complete(null);
        });

        return future;
    }

    /**
     * Called when connection state changes.
     */
    private void onStateChanged(ConnectionState newState) {
        this.state = newState;

        // Handle auto-reconnect
        if (newState == ConnectionState.DISCONNECTED && config.isAutoReconnect()) {
            scheduleReconnect();
        }
    }

    /**
     * Updates the connection state.
     */
    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.state;
        this.state = newState;
        if (listener != null) {
            listener.onStateChanged(config.getConnectionName(), oldState, newState);
        }
    }

    /**
     * Schedules a reconnection attempt.
     */
    private void scheduleReconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > config.getMaxRetryAttempts()) {
            log.error("[{}] Max reconnect attempts ({}) exceeded",
                config.getConnectionName(), config.getMaxRetryAttempts());
            updateState(ConnectionState.FAILED);
            return;
        }

        log.info("[{}] Scheduling reconnect attempt {} in {}ms",
            config.getConnectionName(), attempt, config.getRetryDelayMs());

        updateState(ConnectionState.RECONNECTING);
        if (listener != null) {
            listener.onReconnecting(config.getConnectionName(), attempt);
        }

        workerGroup.schedule(() -> {
            // Try primary first, then backup
            String host = usingBackup ? config.getBackupHost() : config.getPrimaryHost();
            int port = usingBackup ? config.getBackupPort() : config.getPrimaryPort();

            connect(host, port, usingBackup)
                .thenCompose(v -> signOn())
                .exceptionally(ex -> {
                    log.error("[{}] Reconnect failed: {}", config.getConnectionName(), ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
        }, config.getRetryDelayMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Checks if the connection is active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * Checks if signed on.
     *
     * @return true if signed on
     */
    public boolean isSignedOn() {
        return state == ConnectionState.SIGNED_ON;
    }

    /**
     * Gets the connection name.
     *
     * @return connection name
     */
    public String getConnectionName() {
        return config.getConnectionName();
    }

    @Override
    public void close() {
        log.info("[{}] Closing client", config.getConnectionName());
        try {
            disconnect().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[{}] Error during disconnect: {}", config.getConnectionName(), e.getMessage());
        }
        workerGroup.shutdownGracefully();
    }
}
