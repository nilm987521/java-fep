package com.fep.communication.handler;

import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.message.iso8583.Iso8583Message;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Netty handler for FISC client connections.
 * Handles message routing, response matching, and connection events.
 */
@Slf4j
public class FiscClientHandler extends ChannelDuplexHandler {

    private final String connectionName;
    private final ConnectionListener listener;
    private final Consumer<ConnectionState> stateCallback;

    /** Pending requests waiting for responses, keyed by STAN */
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    @Getter
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    public FiscClientHandler(String connectionName, ConnectionListener listener,
                            Consumer<ConnectionState> stateCallback) {
        this.connectionName = connectionName;
        this.listener = listener;
        this.stateCallback = stateCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Channel active: {}", connectionName, ctx.channel().remoteAddress());
        updateState(ConnectionState.CONNECTED);
        if (listener != null) {
            listener.onConnected(connectionName);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Channel inactive", connectionName);
        updateState(ConnectionState.DISCONNECTED);

        // Complete all pending requests with error
        pendingRequests.forEach((stan, pending) -> {
            pending.future.completeExceptionally(
                new RuntimeException("Channel disconnected"));
        });
        pendingRequests.clear();

        if (listener != null) {
            listener.onDisconnected(connectionName, null);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Iso8583Message message) {
            handleMessage(message);
        } else {
            log.warn("[{}] Unexpected message type: {}", connectionName, msg.getClass());
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Iso8583Message message) {
            log.debug("[{}] Sending message: MTI={}, STAN={}",
                connectionName, message.getMti(), message.getFieldAsString(11));
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            handleIdleEvent(ctx, idleEvent);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("[{}] Exception caught: {}", connectionName, cause.getMessage(), cause);
        if (listener != null) {
            listener.onError(connectionName, cause);
        }
        ctx.close();
    }

    /**
     * Handles an incoming message.
     */
    private void handleMessage(Iso8583Message message) {
        log.debug("[{}] Received message: MTI={}, STAN={}",
            connectionName, message.getMti(), message.getFieldAsString(11));

        // Notify listener
        if (listener != null) {
            listener.onMessageReceived(connectionName, message);
        }

        // Check if this is a response to a pending request
        String stan = message.getFieldAsString(11);
        if (stan != null) {
            PendingRequest pending = pendingRequests.remove(stan);
            if (pending != null) {
                pending.future.complete(message);
                return;
            }
        }

        // Handle unsolicited messages (e.g., network management from FISC)
        handleUnsolicitedMessage(message);
    }

    /**
     * Handles unsolicited messages from FISC.
     */
    private void handleUnsolicitedMessage(Iso8583Message message) {
        String mti = message.getMti();
        log.info("[{}] Received unsolicited message: MTI={}", connectionName, mti);

        // Handle specific unsolicited messages
        if ("0800".equals(mti)) {
            // Network management request from FISC (e.g., heartbeat check)
            // This should be handled by the connection manager to send response
            log.debug("[{}] Received network management request", connectionName);
        }
    }

    /**
     * Handles idle state events.
     */
    private void handleIdleEvent(ChannelHandlerContext ctx, IdleStateEvent event) {
        if (event.state() == IdleState.READER_IDLE) {
            log.warn("[{}] Read idle timeout, no data received", connectionName);
        } else if (event.state() == IdleState.WRITER_IDLE) {
            log.debug("[{}] Write idle, heartbeat may be needed", connectionName);
        } else if (event.state() == IdleState.ALL_IDLE) {
            log.debug("[{}] All idle, connection may need heartbeat", connectionName);
        }
    }

    /**
     * Updates the connection state.
     */
    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.state;
        this.state = newState;
        log.debug("[{}] State changed: {} -> {}", connectionName, oldState, newState);
        if (stateCallback != null) {
            stateCallback.accept(newState);
        }
        if (listener != null) {
            listener.onStateChanged(connectionName, oldState, newState);
        }
    }

    /**
     * Sets the state to signed on.
     */
    public void setSignedOn() {
        updateState(ConnectionState.SIGNED_ON);
        if (listener != null) {
            listener.onSignedOn(connectionName);
        }
    }

    /**
     * Registers a pending request for response matching.
     *
     * @param stan the STAN of the request
     * @param timeoutMs timeout in milliseconds
     * @return CompletableFuture that will complete with the response
     */
    public CompletableFuture<Iso8583Message> registerPendingRequest(String stan, long timeoutMs) {
        CompletableFuture<Iso8583Message> future = new CompletableFuture<>();

        PendingRequest pending = new PendingRequest(future, System.currentTimeMillis());
        pendingRequests.put(stan, pending);

        // Schedule timeout
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((result, ex) -> {
                pendingRequests.remove(stan);
                if (ex != null) {
                    log.warn("[{}] Request timeout for STAN={}", connectionName, stan);
                }
            });

        return future;
    }

    /**
     * Gets the number of pending requests.
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    /**
     * Internal class to track pending requests.
     */
    private record PendingRequest(CompletableFuture<Iso8583Message> future, long timestamp) {}
}
