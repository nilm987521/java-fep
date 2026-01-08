package com.fep.communication;

import com.fep.communication.config.FiscConnectionConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FiscConnectionConfig.
 */
@DisplayName("FISC Connection Config Tests")
class FiscConnectionConfigTest {

    @Test
    @DisplayName("Should create config with builder")
    void shouldCreateConfigWithBuilder() {
        FiscConnectionConfig config = FiscConnectionConfig.builder()
            .primaryHost("192.168.1.100")
            .primaryPort(9000)
            .backupHost("192.168.1.101")
            .backupPort(9000)
            .connectionName("FISC-MAIN")
            .institutionId("012")
            .build();

        assertEquals("192.168.1.100", config.getPrimaryHost());
        assertEquals(9000, config.getPrimaryPort());
        assertEquals("192.168.1.101", config.getBackupHost());
        assertEquals(9000, config.getBackupPort());
        assertEquals("FISC-MAIN", config.getConnectionName());
        assertEquals("012", config.getInstitutionId());
    }

    @Test
    @DisplayName("Should have default values")
    void shouldHaveDefaultValues() {
        FiscConnectionConfig config = FiscConnectionConfig.builder()
            .primaryHost("localhost")
            .primaryPort(9000)
            .build();

        assertEquals(10000, config.getConnectTimeoutMs());
        assertEquals(30000, config.getReadTimeoutMs());
        assertEquals(10000, config.getWriteTimeoutMs());
        assertEquals(30000, config.getIdleTimeoutMs());
        assertEquals(30000, config.getHeartbeatIntervalMs());
        assertEquals(3, config.getMaxRetryAttempts());
        assertEquals(5000, config.getRetryDelayMs());
        assertTrue(config.isAutoReconnect());
        assertTrue(config.isUseBackupOnFailure());
        assertTrue(config.isTcpKeepAlive());
        assertTrue(config.isTcpNoDelay());
        assertEquals(65536, config.getReceiveBufferSize());
        assertEquals(65536, config.getSendBufferSize());
        assertEquals(2, config.getPoolMinSize());
        assertEquals(10, config.getPoolMaxSize());
        assertEquals(5000, config.getPoolMaxWaitMs());
    }

    @Test
    @DisplayName("Should detect if backup host is configured")
    void shouldDetectBackupHost() {
        FiscConnectionConfig configWithBackup = FiscConnectionConfig.builder()
            .primaryHost("localhost")
            .primaryPort(9000)
            .backupHost("backup.example.com")
            .backupPort(9000)
            .build();

        FiscConnectionConfig configWithoutBackup = FiscConnectionConfig.builder()
            .primaryHost("localhost")
            .primaryPort(9000)
            .build();

        assertTrue(configWithBackup.hasBackupHost());
        assertFalse(configWithoutBackup.hasBackupHost());
    }

    @Test
    @DisplayName("Should create default config")
    void shouldCreateDefaultConfig() {
        FiscConnectionConfig config = FiscConnectionConfig.defaultConfig();

        assertEquals("localhost", config.getPrimaryHost());
        assertEquals(9000, config.getPrimaryPort());
        assertEquals("default", config.getConnectionName());
    }

    @Test
    @DisplayName("Should allow custom pool settings")
    void shouldAllowCustomPoolSettings() {
        FiscConnectionConfig config = FiscConnectionConfig.builder()
            .primaryHost("localhost")
            .primaryPort(9000)
            .poolMinSize(5)
            .poolMaxSize(20)
            .poolMaxWaitMs(10000)
            .build();

        assertEquals(5, config.getPoolMinSize());
        assertEquals(20, config.getPoolMaxSize());
        assertEquals(10000, config.getPoolMaxWaitMs());
    }
}
