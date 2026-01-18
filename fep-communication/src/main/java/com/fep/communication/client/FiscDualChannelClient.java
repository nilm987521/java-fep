package com.fep.communication.client;

import com.fep.communication.codec.FiscMessageDecoder;
import com.fep.communication.codec.FiscMessageEncoder;
import com.fep.communication.config.ChannelFailureStrategy;
import com.fep.communication.config.DualChannelConfig;
import com.fep.communication.exception.CommunicationException;
import com.fep.communication.handler.ReceiveChannelHandler;
import com.fep.communication.handler.SendChannelHandler;
import com.fep.communication.manager.PendingRequestManager;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.service.ChannelMessageService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * FISC Dual-Channel (Send/Receive separated) TCP/IP client.
 *
 * <p>Implements the FISC dual-channel architecture where:
 * <ul>
 *   <li><b>Send Channel</b>: Dedicated connection for sending all requests</li>
 *   <li><b>Receive Channel</b>: Dedicated connection for receiving all responses</li>
 * </ul>
 *
 * <p>Key features:
 * <ul>
 *   <li>True asynchronous communication</li>
 *   <li>STAN-based request/response matching across channels</li>
 *   <li>Independent channel management and failover</li>
 *   <li>Configurable failure strategies</li>
 *   <li>Automatic heartbeat and reconnection</li>
 * </ul>
 *
 * <p>Message flow:
 * <pre>
 *     Send Request:
 *     1. Call sendAndReceive(request)
 *     2. Register in PendingRequestManager with STAN
 *     3. Send via Send Channel (fire-and-forget)
 *     4. Return Future to caller
 *
 *     Receive Response:
 *     1. Receive Channel gets response
 *     2. Extract STAN from response
 *     3. Complete matching Future in PendingRequestManager
 *     4. Caller's Future completes
 * </pre>
 *
 * <p>Example usage:
 * <pre>{@code
 * DualChannelConfig config = DualChannelConfig.builder()
 *     .sendHost("fisc-send.example.com")
 *     .sendPort(9001)
 *     .receiveHost("fisc-recv.example.com")
 *     .receivePort(9002)
 *     .connectionName("FISC-MAIN")
 *     .build();
 *
 * FiscDualChannelClient client = new FiscDualChannelClient(config);
 * client.connect().get();
 * client.signOn().get();
 *
 * // Send transaction (async)
 * Iso8583Message request = ...;
 * Iso8583Message response = client.sendAndReceive(request).get();
 * }</pre>
 */
@Slf4j
public class FiscDualChannelClient implements AutoCloseable {

    private final DualChannelConfig config;
    private final Iso8583MessageFactory messageFactory;
    private final PendingRequestManager pendingRequestManager;
    private final EventLoopGroup workerGroup;
    private final ScheduledExecutorService scheduler;
    private final ChannelMessageService channelMessageService;

    // Send Channel
    private volatile Channel sendChannel;
    private volatile SendChannelHandler sendHandler;
    private final Bootstrap sendBootstrap;

    // Receive Channel
    private volatile Channel receiveChannel;
    private volatile ReceiveChannelHandler receiveHandler;
    private final Bootstrap receiveBootstrap;

    // State management
    @Getter
    private volatile DualChannelState state = DualChannelState.DISCONNECTED;
    private volatile ConnectionListener listener;
    private volatile BiConsumer<String, Iso8583Message> unsolicitedMessageHandler;

    // Reconnection management
    private final AtomicInteger sendReconnectAttempts = new AtomicInteger(0);
    private final AtomicInteger receiveReconnectAttempts = new AtomicInteger(0);
    private volatile boolean usingSendBackup = false;
    private volatile boolean usingReceiveBackup = false;

    // Heartbeat management
    private volatile ScheduledFuture<?> heartbeatTask;

    /**
     * Creates a new FISC dual-channel client.
     *
     * @param config the dual-channel configuration
     */
    public FiscDualChannelClient(DualChannelConfig config) {
        this(config, null);
    }

    /**
     * Creates a new FISC dual-channel client with ChannelMessageService.
     *
     * <p>When ChannelMessageService is provided and config.enableGenericMessageTransform is true,
     * incoming messages will be transformed to GenericMessage using the configured channel schema.
     *
     * @param config the dual-channel configuration
     * @param channelMessageService the channel message service for schema-based message processing (may be null)
     */
    public FiscDualChannelClient(DualChannelConfig config, ChannelMessageService channelMessageService) {
        config.validate();
        this.config = config;
        this.channelMessageService = channelMessageService;
        this.messageFactory = new Iso8583MessageFactory();
        this.messageFactory.setInstitutionId(config.getInstitutionId());
        this.pendingRequestManager = new PendingRequestManager(
            config.getConnectionName(), config.getReadTimeoutMs());
        this.workerGroup = new NioEventLoopGroup();
        this.scheduler = workerGroup;

        this.sendBootstrap = createBootstrap(ChannelRole.SEND);
        this.receiveBootstrap = createBootstrap(ChannelRole.RECEIVE);

        log.info("[{}] FiscDualChannelClient initialized (channelId={}, genericMessageTransform={})",
            config.getConnectionName(), config.getChannelId(), config.isEnableGenericMessageTransform());
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
     * Sets the handler for unsolicited messages from FISC.
     *
     * @param handler the handler (connectionName, message)
     */
    public void setUnsolicitedMessageHandler(BiConsumer<String, Iso8583Message> handler) {
        this.unsolicitedMessageHandler = handler;
    }

    /**
     * Creates a Netty bootstrap for the specified channel role.
     */
    private Bootstrap createBootstrap(ChannelRole role) {
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

                    // Idle state handler
                    int idleSeconds = config.getIdleTimeoutMs() / 1000;
                    if (role == ChannelRole.SEND) {
                        // Send channel: monitor write idle for heartbeat
                        pipeline.addLast("idleStateHandler",
                            new IdleStateHandler(0, idleSeconds, 0));
                    } else {
                        // Receive channel: monitor read idle for connection health
                        pipeline.addLast("idleStateHandler",
                            new IdleStateHandler(idleSeconds * 2, 0, 0));
                    }

                    // Message codec
                    pipeline.addLast("decoder", new FiscMessageDecoder());
                    pipeline.addLast("encoder", new FiscMessageEncoder());

                    // Business logic handler
                    if (role == ChannelRole.SEND) {
                        sendHandler = new SendChannelHandler(
                            config.getSendChannelName(),
                            listener,
                            FiscDualChannelClient.this::onSendChannelStateChanged,
                            channelMessageService,
                            config.getChannelId()
                        );
                        pipeline.addLast("handler", sendHandler);
                    } else {
                        receiveHandler = new ReceiveChannelHandler(
                            config.getReceiveChannelName(),
                            pendingRequestManager,
                            listener,
                            FiscDualChannelClient.this::onReceiveChannelStateChanged,
                            unsolicitedMessageHandler,
                            channelMessageService,
                            config.getChannelId(),
                            config.isEnableGenericMessageTransform()
                        );
                        pipeline.addLast("handler", receiveHandler);
                    }
                }
            });
    }

    // ==================== Connection Management ====================

    /**
     * Connects both channels to FISC.
     *
     * @return CompletableFuture that completes when both channels are connected
     */
    public CompletableFuture<Void> connect() {
        log.info("[{}] Connecting dual channels", config.getConnectionName());
        updateState(DualChannelState.CONNECTING);

        return CompletableFuture.allOf(
            connectSendChannel(),
            connectReceiveChannel()
        ).thenRun(() -> {
            updateState(DualChannelState.BOTH_CONNECTED);
            log.info("[{}] Both channels connected", config.getConnectionName());
        }).exceptionally(ex -> {
            log.error("[{}] Failed to connect channels: {}", config.getConnectionName(), ex.getMessage());
            handleConnectionFailure();
            throw new RuntimeException(ex);
        });
    }

    /**
     * Connects the Send channel.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> connectSendChannel() {
        return connectChannel(ChannelRole.SEND, config.getSendHost(), config.getSendPort(), false);
    }

    /**
     * Connects the Receive channel.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Void> connectReceiveChannel() {
        return connectChannel(ChannelRole.RECEIVE, config.getReceiveHost(), config.getReceivePort(), false);
    }

    /**
     * Connects a specific channel.
     */
    private CompletableFuture<Void> connectChannel(ChannelRole role, String host, int port, boolean isBackup) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String channelName = role == ChannelRole.SEND ? config.getSendChannelName() : config.getReceiveChannelName();

        log.info("[{}] Connecting to {}:{} (backup={})", channelName, host, port, isBackup);

        Bootstrap bootstrap = role == ChannelRole.SEND ? sendBootstrap : receiveBootstrap;

        bootstrap.connect(host, port).addListener((ChannelFutureListener) connectFuture -> {
            if (connectFuture.isSuccess()) {
                Channel channel = connectFuture.channel();
                if (role == ChannelRole.SEND) {
                    sendChannel = channel;
                    usingSendBackup = isBackup;
                    sendReconnectAttempts.set(0);
                } else {
                    receiveChannel = channel;
                    usingReceiveBackup = isBackup;
                    receiveReconnectAttempts.set(0);
                }
                log.info("[{}] Connected successfully", channelName);
                future.complete(null);
            } else {
                Throwable cause = connectFuture.cause();
                log.error("[{}] Connection failed: {}", channelName, cause.getMessage());

                // Try backup if available
                boolean hasBackup = role == ChannelRole.SEND ? config.hasSendBackupHost() : config.hasReceiveBackupHost();
                if (!isBackup && hasBackup) {
                    String backupHost = role == ChannelRole.SEND ? config.getSendBackupHost() : config.getReceiveBackupHost();
                    int backupPort = role == ChannelRole.SEND ? config.getSendBackupPort() : config.getReceiveBackupPort();
                    log.info("[{}] Trying backup host {}:{}", channelName, backupHost, backupPort);
                    connectChannel(role, backupHost, backupPort, true)
                        .whenComplete((v, ex) -> {
                            if (ex != null) {
                                future.completeExceptionally(
                                    CommunicationException.connectionFailed(host, port, cause));
                            } else {
                                future.complete(null);
                            }
                        });
                } else {
                    future.completeExceptionally(CommunicationException.connectionFailed(host, port, cause));
                }
            }
        });

        return future;
    }

    /**
     * Performs sign-on via both channels.
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
                    if (sendHandler != null) sendHandler.setSignedOn();
                    if (receiveHandler != null) receiveHandler.setSignedOn();
                    updateState(DualChannelState.SIGNED_ON);
                    startHeartbeat();
                } else {
                    log.error("[{}] Sign-on failed with response code: {}",
                        config.getConnectionName(), responseCode);
                }
                return response;
            });
    }

    /**
     * Performs sign-off.
     *
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> signOff() {
        log.info("[{}] Signing off from FISC", config.getConnectionName());
        stopHeartbeat();

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

    // ==================== Message Sending ====================

    /**
     * Sends a message and waits for response.
     *
     * <p>The request is sent via Send Channel and the response
     * is received via Receive Channel, matched by STAN.
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
        if (sendChannel == null || !sendChannel.isActive()) {
            return CompletableFuture.failedFuture(
                CommunicationException.channelClosed("Send channel is not connected"));
        }

        // Ensure transaction fields are set
        if (!request.hasField(11)) {
            messageFactory.setTransactionFields(request);
        }

        String stan = request.getFieldAsString(11);

        // Register pending request BEFORE sending
        CompletableFuture<Iso8583Message> responseFuture =
            pendingRequestManager.register(stan, timeoutMs);

        // Send via Send Channel (fire-and-forget)
        sendChannel.writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
            if (!writeFuture.isSuccess()) {
                // Cancel pending request on send failure
                pendingRequestManager.cancel(stan, writeFuture.cause());
                log.error("[{}] Failed to send message STAN={}: {}",
                    config.getSendChannelName(), stan, writeFuture.cause().getMessage());
            } else {
                log.debug("[{}] Message sent: MTI={}, STAN={}",
                    config.getSendChannelName(), request.getMti(), stan);
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
        if (sendChannel == null || !sendChannel.isActive()) {
            return CompletableFuture.failedFuture(
                CommunicationException.channelClosed("Send channel is not connected"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        sendChannel.writeAndFlush(message).addListener((ChannelFutureListener) writeFuture -> {
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
     * Sends a heartbeat (echo test).
     *
     * @return CompletableFuture that completes with the response
     */
    public CompletableFuture<Iso8583Message> sendHeartbeat() {
        log.debug("[{}] Sending heartbeat", config.getConnectionName());
        Iso8583Message echoRequest = messageFactory.createEchoTestMessage();
        return sendAndReceive(echoRequest, 10000); // 10 second timeout for heartbeat
    }

    // ==================== Heartbeat Management ====================

    /**
     * Starts the heartbeat scheduler.
     */
    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        int intervalMs = config.getHeartbeatIntervalMs();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (state == DualChannelState.SIGNED_ON) {
                sendHeartbeat()
                    .exceptionally(ex -> {
                        log.warn("[{}] Heartbeat failed: {}", config.getConnectionName(), ex.getMessage());
                        return null;
                    });
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        log.debug("[{}] Heartbeat started with interval {}ms", config.getConnectionName(), intervalMs);
    }

    /**
     * Stops the heartbeat scheduler.
     */
    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
            log.debug("[{}] Heartbeat stopped", config.getConnectionName());
        }
    }

    // ==================== State Management ====================

    /**
     * Called when Send channel state changes.
     */
    private void onSendChannelStateChanged(ConnectionState newState) {
        log.debug("[{}] Send channel state: {}", config.getSendChannelName(), newState);

        if (newState == ConnectionState.DISCONNECTED) {
            handleChannelFailure(ChannelRole.SEND);
        }
    }

    /**
     * Called when Receive channel state changes.
     */
    private void onReceiveChannelStateChanged(ConnectionState newState) {
        log.debug("[{}] Receive channel state: {}", config.getReceiveChannelName(), newState);

        if (newState == ConnectionState.DISCONNECTED) {
            handleChannelFailure(ChannelRole.RECEIVE);
        }
    }

    /**
     * Handles a channel failure based on the configured strategy.
     */
    private void handleChannelFailure(ChannelRole role) {
        log.warn("[{}] {} channel failure detected", config.getConnectionName(), role);

        switch (config.getFailureStrategy()) {
            case FAIL_WHEN_BOTH_DOWN:
                if (!isSendChannelConnected() && !isReceiveChannelConnected()) {
                    log.error("[{}] Both channels down, marking as FAILED", config.getConnectionName());
                    updateState(DualChannelState.FAILED);
                    pendingRequestManager.cancelAll(
                        CommunicationException.channelClosed("Both channels disconnected"));
                } else {
                    // Update state to partial
                    if (isSendChannelConnected()) {
                        updateState(DualChannelState.SEND_ONLY_CONNECTED);
                    } else {
                        updateState(DualChannelState.RECEIVE_ONLY_CONNECTED);
                    }
                }
                break;

            case FAIL_WHEN_ANY_DOWN:
                log.error("[{}] {} channel down, marking as FAILED", config.getConnectionName(), role);
                updateState(DualChannelState.FAILED);
                pendingRequestManager.cancelAll(
                    CommunicationException.channelClosed(role + " channel disconnected"));
                break;

            case FALLBACK_TO_SINGLE:
                // Not implemented yet - would require protocol changes
                log.warn("[{}] FALLBACK_TO_SINGLE not yet implemented, using FAIL_WHEN_BOTH_DOWN",
                    config.getConnectionName());
                handleChannelFailure(role); // Recursive with default strategy
                break;
        }

        // Schedule reconnection if enabled
        if (config.isAutoReconnect() && state != DualChannelState.CLOSED) {
            scheduleReconnect(role);
        }
    }

    /**
     * Handles overall connection failure.
     */
    private void handleConnectionFailure() {
        updateState(DualChannelState.FAILED);
        pendingRequestManager.cancelAll(
            CommunicationException.channelClosed("Connection failed"));
    }

    /**
     * Schedules a reconnection attempt for a specific channel.
     */
    private void scheduleReconnect(ChannelRole role) {
        AtomicInteger attempts = role == ChannelRole.SEND ? sendReconnectAttempts : receiveReconnectAttempts;
        int attempt = attempts.incrementAndGet();

        if (attempt > config.getMaxRetryAttempts()) {
            log.error("[{}] Max reconnect attempts ({}) exceeded for {} channel",
                config.getConnectionName(), config.getMaxRetryAttempts(), role);
            return;
        }

        String channelName = role == ChannelRole.SEND ? config.getSendChannelName() : config.getReceiveChannelName();
        log.info("[{}] Scheduling reconnect attempt {} in {}ms",
            channelName, attempt, config.getRetryDelayMs());

        if (listener != null) {
            listener.onReconnecting(channelName, attempt);
        }

        scheduler.schedule(() -> {
            boolean usingBackup = role == ChannelRole.SEND ? usingSendBackup : usingReceiveBackup;
            String host, backupHost;
            int port, backupPort;

            if (role == ChannelRole.SEND) {
                host = usingBackup ? config.getSendBackupHost() : config.getSendHost();
                port = usingBackup ? config.getSendBackupPort() : config.getSendPort();
            } else {
                host = usingBackup ? config.getReceiveBackupHost() : config.getReceiveHost();
                port = usingBackup ? config.getReceiveBackupPort() : config.getReceivePort();
            }

            connectChannel(role, host, port, usingBackup)
                .thenRun(() -> {
                    // If both channels are now connected, re-sign-on
                    if (isSendChannelConnected() && isReceiveChannelConnected()) {
                        signOn().exceptionally(ex -> {
                            log.error("[{}] Re-sign-on failed: {}", config.getConnectionName(), ex.getMessage());
                            return null;
                        });
                    }
                })
                .exceptionally(ex -> {
                    log.error("[{}] Reconnect failed: {}", channelName, ex.getMessage());
                    scheduleReconnect(role);
                    return null;
                });
        }, config.getRetryDelayMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the dual-channel state.
     */
    private void updateState(DualChannelState newState) {
        DualChannelState oldState = this.state;
        this.state = newState;
        log.info("[{}] State changed: {} -> {}", config.getConnectionName(), oldState, newState);

        if (listener != null) {
            // Map to single-channel state for listener compatibility
            ConnectionState mappedState = mapToConnectionState(newState);
            listener.onStateChanged(config.getConnectionName(),
                mapToConnectionState(oldState), mappedState);
        }
    }

    /**
     * Maps dual-channel state to single-channel state for listener compatibility.
     */
    private ConnectionState mapToConnectionState(DualChannelState dualState) {
        return switch (dualState) {
            case DISCONNECTED -> ConnectionState.DISCONNECTED;
            case CONNECTING -> ConnectionState.CONNECTING;
            case SEND_ONLY_CONNECTED, RECEIVE_ONLY_CONNECTED, BOTH_CONNECTED -> ConnectionState.CONNECTED;
            case SIGNED_ON -> ConnectionState.SIGNED_ON;
            case RECONNECTING -> ConnectionState.RECONNECTING;
            case CLOSING -> ConnectionState.CLOSING;
            case CLOSED -> ConnectionState.CLOSED;
            case FAILED -> ConnectionState.FAILED;
        };
    }

    // ==================== Status Methods ====================

    /**
     * Checks if Send channel is connected.
     *
     * @return true if connected
     */
    public boolean isSendChannelConnected() {
        return sendChannel != null && sendChannel.isActive();
    }

    /**
     * Checks if Receive channel is connected.
     *
     * @return true if connected
     */
    public boolean isReceiveChannelConnected() {
        return receiveChannel != null && receiveChannel.isActive();
    }

    /**
     * Checks if both channels are connected.
     *
     * @return true if both connected
     */
    public boolean isConnected() {
        return isSendChannelConnected() && isReceiveChannelConnected();
    }

    /**
     * Checks if signed on.
     *
     * @return true if signed on
     */
    public boolean isSignedOn() {
        return state == DualChannelState.SIGNED_ON;
    }

    /**
     * Gets the connection name.
     *
     * @return connection name
     */
    public String getConnectionName() {
        return config.getConnectionName();
    }

    /**
     * Gets the pending request manager.
     *
     * @return pending request manager
     */
    public PendingRequestManager getPendingRequestManager() {
        return pendingRequestManager;
    }

    /**
     * Gets the message factory.
     *
     * @return message factory
     */
    public Iso8583MessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * Gets the channel message service.
     *
     * @return channel message service (may be null if not configured)
     */
    public ChannelMessageService getChannelMessageService() {
        return channelMessageService;
    }

    // ==================== Disconnect and Close ====================

    /**
     * Disconnects both channels.
     *
     * @return CompletableFuture that completes when disconnected
     */
    public CompletableFuture<Void> disconnect() {
        log.info("[{}] Disconnecting dual channels", config.getConnectionName());
        updateState(DualChannelState.CLOSING);
        stopHeartbeat();

        CompletableFuture<Void> sendClose = disconnectChannel(sendChannel, config.getSendChannelName());
        CompletableFuture<Void> recvClose = disconnectChannel(receiveChannel, config.getReceiveChannelName());

        return CompletableFuture.allOf(sendClose, recvClose)
            .thenRun(() -> {
                updateState(DualChannelState.DISCONNECTED);
                log.info("[{}] Both channels disconnected", config.getConnectionName());
            });
    }

    /**
     * Disconnects a specific channel.
     */
    private CompletableFuture<Void> disconnectChannel(Channel channel, String name) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (channel == null || !channel.isActive()) {
            future.complete(null);
            return future;
        }

        channel.close().addListener((ChannelFutureListener) closeFuture -> {
            log.info("[{}] Disconnected", name);
            future.complete(null);
        });

        return future;
    }

    @Override
    public void close() {
        log.info("[{}] Closing dual-channel client", config.getConnectionName());
        updateState(DualChannelState.CLOSING);

        try {
            disconnect().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[{}] Error during disconnect: {}", config.getConnectionName(), e.getMessage());
        }

        pendingRequestManager.close();
        workerGroup.shutdownGracefully();
        updateState(DualChannelState.CLOSED);

        log.info("[{}] Dual-channel client closed", config.getConnectionName());
    }
}
