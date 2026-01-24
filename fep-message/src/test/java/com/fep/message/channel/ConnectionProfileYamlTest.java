package com.fep.message.channel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for YAML parsing of ConnectionProfile with kebab-case properties.
 */
@DisplayName("ConnectionProfile YAML Parsing Tests")
class ConnectionProfileYamlTest {

    private static final String YAML_CONFIG = """
            profile-id: ATM_SERVER
            host: 0.0.0.0
            send-port: 19001
            receive-port: 19001
            dual-channel: false
            connect-timeout: 3000
            response-timeout: 10000
            heartbeat-interval: 30000
            max-retries: 1
            retry-delay: 1000
            ssl-enabled: false
            auto-reconnect: false
            connection-mode: SERVER
            properties:
              institution-id: "822"
            """;

    @Test
    @DisplayName("Should parse kebab-case YAML properties correctly")
    void shouldParseKebabCaseYaml() throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ConnectionProfile profile = yamlMapper.readValue(YAML_CONFIG, ConnectionProfile.class);

        assertThat(profile.getProfileId()).isEqualTo("ATM_SERVER");
        assertThat(profile.getHost()).isEqualTo("0.0.0.0");
        assertThat(profile.getSendPort()).isEqualTo(19001);
        assertThat(profile.getReceivePort()).isEqualTo(19001);
        assertThat(profile.getConnectTimeout()).isEqualTo(3000);
        assertThat(profile.getResponseTimeout()).isEqualTo(10000);
        assertThat(profile.getHeartbeatInterval()).isEqualTo(30000);
        assertThat(profile.getMaxRetries()).isEqualTo(1);
        assertThat(profile.getRetryDelay()).isEqualTo(1000);
        assertThat(profile.isSslEnabled()).isFalse();
        assertThat(profile.isAutoReconnect()).isFalse();
        assertThat(profile.getDualChannelSetting()).isFalse();
        assertThat(profile.getConnectionMode()).isEqualTo("SERVER");
        assertThat(profile.isServerMode()).isTrue();
        assertThat(profile.getProperties()).containsEntry("institution-id", "822");
    }

    @Test
    @DisplayName("Should parse camelCase JSON properties correctly")
    void shouldParseCamelCaseJson() throws Exception {
        String json = """
            {
              "profileId": "TEST_PROFILE",
              "host": "localhost",
              "sendPort": 9001,
              "receivePort": 9002,
              "connectionMode": "CLIENT"
            }
            """;

        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ConnectionProfile profile = jsonMapper.readValue(json, ConnectionProfile.class);

        assertThat(profile.getProfileId()).isEqualTo("TEST_PROFILE");
        assertThat(profile.getHost()).isEqualTo("localhost");
        assertThat(profile.getSendPort()).isEqualTo(9001);
        assertThat(profile.getReceivePort()).isEqualTo(9002);
        assertThat(profile.getConnectionMode()).isEqualTo("CLIENT");
        assertThat(profile.isServerMode()).isFalse();
        assertThat(profile.isClientMode()).isTrue();
    }
}
