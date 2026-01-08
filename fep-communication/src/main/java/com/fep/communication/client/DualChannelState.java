package com.fep.communication.client;

/**
 * State enumeration for dual-channel FISC connection.
 *
 * <p>Tracks the combined state of both Send and Receive channels.
 */
public enum DualChannelState {

    /** Both channels are disconnected */
    DISCONNECTED,

    /** Currently connecting one or both channels */
    CONNECTING,

    /** Only Send channel is connected */
    SEND_ONLY_CONNECTED,

    /** Only Receive channel is connected */
    RECEIVE_ONLY_CONNECTED,

    /** Both channels are connected but not signed on */
    BOTH_CONNECTED,

    /** Both channels are signed on and ready for transactions */
    SIGNED_ON,

    /** Reconnecting after disconnection */
    RECONNECTING,

    /** Closing connections */
    CLOSING,

    /** Both connections closed */
    CLOSED,

    /** Connection failed */
    FAILED;

    /**
     * Checks if the state indicates operational status.
     *
     * @return true if at least one channel is connected
     */
    public boolean isOperational() {
        return this == SEND_ONLY_CONNECTED
            || this == RECEIVE_ONLY_CONNECTED
            || this == BOTH_CONNECTED
            || this == SIGNED_ON;
    }

    /**
     * Checks if the state indicates full operational status.
     *
     * @return true if both channels are connected and signed on
     */
    public boolean isFullyOperational() {
        return this == SIGNED_ON;
    }

    /**
     * Checks if the state indicates a terminal state.
     *
     * @return true if connection is closed or failed
     */
    public boolean isTerminal() {
        return this == CLOSED || this == FAILED;
    }
}
