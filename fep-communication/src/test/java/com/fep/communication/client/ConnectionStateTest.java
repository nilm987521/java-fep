package com.fep.communication.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConnectionState enum.
 */
@DisplayName("Connection State Tests")
class ConnectionStateTest {

    @Test
    @DisplayName("Should have all expected states")
    void shouldHaveAllExpectedStates() {
        ConnectionState[] states = ConnectionState.values();

        assertEquals(8, states.length);
        assertNotNull(ConnectionState.DISCONNECTED);
        assertNotNull(ConnectionState.CONNECTING);
        assertNotNull(ConnectionState.CONNECTED);
        assertNotNull(ConnectionState.SIGNED_ON);
        assertNotNull(ConnectionState.RECONNECTING);
        assertNotNull(ConnectionState.CLOSING);
        assertNotNull(ConnectionState.CLOSED);
        assertNotNull(ConnectionState.FAILED);
    }

    @Test
    @DisplayName("Should convert from string")
    void shouldConvertFromString() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionState.valueOf("DISCONNECTED"));
        assertEquals(ConnectionState.CONNECTED, ConnectionState.valueOf("CONNECTED"));
        assertEquals(ConnectionState.SIGNED_ON, ConnectionState.valueOf("SIGNED_ON"));
    }
}
