package com.fep.communication.client;

/**
 * Connection state enumeration.
 */
public enum ConnectionState {

    /** Initial state, not connected */
    DISCONNECTED,

    /** Currently connecting */
    CONNECTING,

    /** Connected and ready */
    CONNECTED,

    /** Connection signed on (authenticated) */
    SIGNED_ON,

    /** Reconnecting after disconnection */
    RECONNECTING,

    /** Closing connection */
    CLOSING,

    /** Connection closed */
    CLOSED,

    /** Connection failed */
    FAILED
}
