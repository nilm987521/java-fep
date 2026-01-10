package com.fep.communication.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DualChannelConfig.
 */
@DisplayName("DualChannelConfig Tests")
class DualChannelConfigTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create config with required fields")
        void shouldCreateConfigWithRequiredFields() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("send.fisc.com.tw")
                .sendPort(8001)
                .receiveHost("receive.fisc.com.tw")
                .receivePort(8002)
                .build();

            assertThat(config.getSendHost()).isEqualTo("send.fisc.com.tw");
            assertThat(config.getSendPort()).isEqualTo(8001);
            assertThat(config.getReceiveHost()).isEqualTo("receive.fisc.com.tw");
            assertThat(config.getReceivePort()).isEqualTo(8002);
        }

        @Test
        @DisplayName("Should use default values when not specified")
        void shouldUseDefaultValues() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.getConnectTimeoutMs()).isEqualTo(10000);
            assertThat(config.getReadTimeoutMs()).isEqualTo(30000);
            assertThat(config.getWriteTimeoutMs()).isEqualTo(10000);
            assertThat(config.getIdleTimeoutMs()).isEqualTo(30000);
            assertThat(config.getHeartbeatIntervalMs()).isEqualTo(30000);
            assertThat(config.getMaxRetryAttempts()).isEqualTo(3);
            assertThat(config.getRetryDelayMs()).isEqualTo(5000);
            assertThat(config.isAutoReconnect()).isTrue();
            assertThat(config.isTcpKeepAlive()).isTrue();
            assertThat(config.isTcpNoDelay()).isTrue();
            assertThat(config.getReceiveBufferSize()).isEqualTo(65536);
            assertThat(config.getSendBufferSize()).isEqualTo(65536);
            assertThat(config.getFailureStrategy()).isEqualTo(ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN);
            assertThat(config.getHealthCheckIntervalMs()).isEqualTo(10000);
            assertThat(config.isDualChannelMode()).isTrue();
        }

        @Test
        @DisplayName("Should override default values")
        void shouldOverrideDefaultValues() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .connectTimeoutMs(5000)
                .readTimeoutMs(15000)
                .writeTimeoutMs(5000)
                .maxRetryAttempts(5)
                .autoReconnect(false)
                .tcpKeepAlive(false)
                .tcpNoDelay(false)
                .failureStrategy(ChannelFailureStrategy.FAIL_WHEN_ANY_DOWN)
                .build();

            assertThat(config.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(config.getReadTimeoutMs()).isEqualTo(15000);
            assertThat(config.getWriteTimeoutMs()).isEqualTo(5000);
            assertThat(config.getMaxRetryAttempts()).isEqualTo(5);
            assertThat(config.isAutoReconnect()).isFalse();
            assertThat(config.isTcpKeepAlive()).isFalse();
            assertThat(config.isTcpNoDelay()).isFalse();
            assertThat(config.getFailureStrategy()).isEqualTo(ChannelFailureStrategy.FAIL_WHEN_ANY_DOWN);
        }

        @Test
        @DisplayName("Should set backup hosts")
        void shouldSetBackupHosts() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("primary-send.fisc.com.tw")
                .sendPort(8001)
                .sendBackupHost("backup-send.fisc.com.tw")
                .sendBackupPort(8003)
                .receiveHost("primary-receive.fisc.com.tw")
                .receivePort(8002)
                .receiveBackupHost("backup-receive.fisc.com.tw")
                .receiveBackupPort(8004)
                .build();

            assertThat(config.getSendBackupHost()).isEqualTo("backup-send.fisc.com.tw");
            assertThat(config.getSendBackupPort()).isEqualTo(8003);
            assertThat(config.getReceiveBackupHost()).isEqualTo("backup-receive.fisc.com.tw");
            assertThat(config.getReceiveBackupPort()).isEqualTo(8004);
        }
    }

    @Nested
    @DisplayName("Backup Host Check Tests")
    class BackupHostCheckTests {

        @Test
        @DisplayName("Should return true when send backup is configured")
        void shouldReturnTrueWhenSendBackupConfigured() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .sendBackupHost("backup.host")
                .sendBackupPort(8003)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.hasSendBackupHost()).isTrue();
        }

        @Test
        @DisplayName("Should return false when send backup is not configured")
        void shouldReturnFalseWhenSendBackupNotConfigured() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.hasSendBackupHost()).isFalse();
        }

        @Test
        @DisplayName("Should return false when send backup host is empty")
        void shouldReturnFalseWhenSendBackupHostEmpty() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .sendBackupHost("")
                .sendBackupPort(8003)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.hasSendBackupHost()).isFalse();
        }

        @Test
        @DisplayName("Should return false when send backup port is zero")
        void shouldReturnFalseWhenSendBackupPortZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .sendBackupHost("backup.host")
                .sendBackupPort(0)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.hasSendBackupHost()).isFalse();
        }

        @Test
        @DisplayName("Should return true when receive backup is configured")
        void shouldReturnTrueWhenReceiveBackupConfigured() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .receiveBackupHost("backup.host")
                .receiveBackupPort(8004)
                .build();

            assertThat(config.hasReceiveBackupHost()).isTrue();
        }

        @Test
        @DisplayName("Should return false when receive backup is not configured")
        void shouldReturnFalseWhenReceiveBackupNotConfigured() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.hasReceiveBackupHost()).isFalse();
        }

        @Test
        @DisplayName("Should return false when receive backup host is empty")
        void shouldReturnFalseWhenReceiveBackupHostEmpty() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .receiveBackupHost("")
                .receiveBackupPort(8004)
                .build();

            assertThat(config.hasReceiveBackupHost()).isFalse();
        }

        @Test
        @DisplayName("Should return false when receive backup port is zero")
        void shouldReturnFalseWhenReceiveBackupPortZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .receiveBackupHost("backup.host")
                .receiveBackupPort(0)
                .build();

            assertThat(config.hasReceiveBackupHost()).isFalse();
        }
    }

    @Nested
    @DisplayName("Channel Name Tests")
    class ChannelNameTests {

        @Test
        @DisplayName("Should generate send channel name with connection name")
        void shouldGenerateSendChannelNameWithConnectionName() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .connectionName("FISC-PROD")
                .build();

            assertThat(config.getSendChannelName()).isEqualTo("FISC-PROD-SEND");
        }

        @Test
        @DisplayName("Should generate receive channel name with connection name")
        void shouldGenerateReceiveChannelNameWithConnectionName() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .connectionName("FISC-PROD")
                .build();

            assertThat(config.getReceiveChannelName()).isEqualTo("FISC-PROD-RECV");
        }

        @Test
        @DisplayName("Should use default prefix when connection name is null")
        void shouldUseDefaultPrefixWhenConnectionNameNull() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThat(config.getSendChannelName()).isEqualTo("FISC-SEND");
            assertThat(config.getReceiveChannelName()).isEqualTo("FISC-RECV");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation with valid config")
        void shouldPassValidationWithValidConfig() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw when send host is null")
        void shouldThrowWhenSendHostNull() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Send host is required");
        }

        @Test
        @DisplayName("Should throw when send host is empty")
        void shouldThrowWhenSendHostEmpty() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Send host is required");
        }

        @Test
        @DisplayName("Should throw when send port is zero")
        void shouldThrowWhenSendPortZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(0)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid send port");
        }

        @Test
        @DisplayName("Should throw when send port exceeds max")
        void shouldThrowWhenSendPortExceedsMax() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(70000)
                .receiveHost("localhost")
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid send port");
        }

        @Test
        @DisplayName("Should throw when receive host is null")
        void shouldThrowWhenReceiveHostNull() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Receive host is required");
        }

        @Test
        @DisplayName("Should throw when receive host is empty")
        void shouldThrowWhenReceiveHostEmpty() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("")
                .receivePort(8002)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Receive host is required");
        }

        @Test
        @DisplayName("Should throw when receive port is zero")
        void shouldThrowWhenReceivePortZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(0)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid receive port");
        }

        @Test
        @DisplayName("Should throw when receive port exceeds max")
        void shouldThrowWhenReceivePortExceedsMax() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(70000)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid receive port");
        }

        @Test
        @DisplayName("Should throw when connect timeout is zero")
        void shouldThrowWhenConnectTimeoutZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .connectTimeoutMs(0)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connect timeout must be positive");
        }

        @Test
        @DisplayName("Should throw when read timeout is zero")
        void shouldThrowWhenReadTimeoutZero() {
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(8001)
                .receiveHost("localhost")
                .receivePort(8002)
                .readTimeoutMs(0)
                .build();

            assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Read timeout must be positive");
        }
    }

    @Nested
    @DisplayName("Static Factory Tests")
    class StaticFactoryTests {

        @Test
        @DisplayName("Should create default config")
        void shouldCreateDefaultConfig() {
            DualChannelConfig config = DualChannelConfig.defaultConfig();

            assertThat(config.getSendHost()).isEqualTo("localhost");
            assertThat(config.getSendPort()).isEqualTo(9001);
            assertThat(config.getReceiveHost()).isEqualTo("localhost");
            assertThat(config.getReceivePort()).isEqualTo(9002);
            assertThat(config.getConnectionName()).isEqualTo("default");
        }

        @Test
        @DisplayName("Should create from single config")
        void shouldCreateFromSingleConfig() {
            FiscConnectionConfig singleConfig = FiscConnectionConfig.builder()
                .primaryHost("fisc.host")
                .primaryPort(8000)
                .backupHost("backup.fisc.host")
                .backupPort(8001)
                .connectTimeoutMs(5000)
                .readTimeoutMs(15000)
                .institutionId("BANK001")
                .connectionName("FISC-SINGLE")
                .build();

            DualChannelConfig dualConfig = DualChannelConfig.fromSingleConfig(singleConfig);

            assertThat(dualConfig.getSendHost()).isEqualTo("fisc.host");
            assertThat(dualConfig.getSendPort()).isEqualTo(8000);
            assertThat(dualConfig.getSendBackupHost()).isEqualTo("backup.fisc.host");
            assertThat(dualConfig.getSendBackupPort()).isEqualTo(8001);
            assertThat(dualConfig.getReceiveHost()).isEqualTo("fisc.host");
            assertThat(dualConfig.getReceivePort()).isEqualTo(8000);
            assertThat(dualConfig.getReceiveBackupHost()).isEqualTo("backup.fisc.host");
            assertThat(dualConfig.getReceiveBackupPort()).isEqualTo(8001);
            assertThat(dualConfig.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(dualConfig.getReadTimeoutMs()).isEqualTo(15000);
            assertThat(dualConfig.getInstitutionId()).isEqualTo("BANK001");
            assertThat(dualConfig.getConnectionName()).isEqualTo("FISC-SINGLE");
            assertThat(dualConfig.isDualChannelMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("Channel Config Conversion Tests")
    class ChannelConfigConversionTests {

        @Test
        @DisplayName("Should convert to send channel config")
        void shouldConvertToSendChannelConfig() {
            DualChannelConfig dualConfig = DualChannelConfig.builder()
                .sendHost("send.host")
                .sendPort(8001)
                .sendBackupHost("send.backup")
                .sendBackupPort(8003)
                .receiveHost("receive.host")
                .receivePort(8002)
                .connectTimeoutMs(5000)
                .readTimeoutMs(10000)
                .institutionId("BANK001")
                .connectionName("FISC")
                .build();

            FiscConnectionConfig sendConfig = dualConfig.toSendChannelConfig();

            assertThat(sendConfig.getPrimaryHost()).isEqualTo("send.host");
            assertThat(sendConfig.getPrimaryPort()).isEqualTo(8001);
            assertThat(sendConfig.getBackupHost()).isEqualTo("send.backup");
            assertThat(sendConfig.getBackupPort()).isEqualTo(8003);
            assertThat(sendConfig.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(sendConfig.getReadTimeoutMs()).isEqualTo(10000);
            assertThat(sendConfig.getInstitutionId()).isEqualTo("BANK001");
            assertThat(sendConfig.getConnectionName()).isEqualTo("FISC-SEND");
        }

        @Test
        @DisplayName("Should convert to receive channel config")
        void shouldConvertToReceiveChannelConfig() {
            DualChannelConfig dualConfig = DualChannelConfig.builder()
                .sendHost("send.host")
                .sendPort(8001)
                .receiveHost("receive.host")
                .receivePort(8002)
                .receiveBackupHost("receive.backup")
                .receiveBackupPort(8004)
                .connectTimeoutMs(5000)
                .readTimeoutMs(10000)
                .institutionId("BANK001")
                .connectionName("FISC")
                .build();

            FiscConnectionConfig receiveConfig = dualConfig.toReceiveChannelConfig();

            assertThat(receiveConfig.getPrimaryHost()).isEqualTo("receive.host");
            assertThat(receiveConfig.getPrimaryPort()).isEqualTo(8002);
            assertThat(receiveConfig.getBackupHost()).isEqualTo("receive.backup");
            assertThat(receiveConfig.getBackupPort()).isEqualTo(8004);
            assertThat(receiveConfig.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(receiveConfig.getReadTimeoutMs()).isEqualTo(10000);
            assertThat(receiveConfig.getInstitutionId()).isEqualTo("BANK001");
            assertThat(receiveConfig.getConnectionName()).isEqualTo("FISC-RECV");
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("Should update config values using setters")
        void shouldUpdateConfigValuesUsingSetters() {
            DualChannelConfig config = DualChannelConfig.defaultConfig();

            config.setSendHost("new.send.host");
            config.setSendPort(9999);
            config.setReceiveHost("new.receive.host");
            config.setReceivePort(9998);
            config.setInstitutionId("NEWBANK");
            config.setConnectionName("NEW-CONNECTION");
            config.setDualChannelMode(false);

            assertThat(config.getSendHost()).isEqualTo("new.send.host");
            assertThat(config.getSendPort()).isEqualTo(9999);
            assertThat(config.getReceiveHost()).isEqualTo("new.receive.host");
            assertThat(config.getReceivePort()).isEqualTo(9998);
            assertThat(config.getInstitutionId()).isEqualTo("NEWBANK");
            assertThat(config.getConnectionName()).isEqualTo("NEW-CONNECTION");
            assertThat(config.isDualChannelMode()).isFalse();
        }
    }
}
