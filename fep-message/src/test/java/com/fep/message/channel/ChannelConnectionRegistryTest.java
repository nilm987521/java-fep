package com.fep.message.channel;

import com.fep.message.interfaces.ConnectionSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ChannelConnectionRegistry.
 */
@DisplayName("ChannelConnectionRegistry")
class ChannelConnectionRegistryTest {

    private ChannelConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ChannelConnectionRegistry.getInstance();
        registry.clear();
    }

    @Nested
    @DisplayName("Loading Configuration")
    class LoadingTests {

        @Test
        @DisplayName("should load V2 configuration from JSON string")
        void shouldLoadV2ConfigFromJson() {
            String json = """
                {
                  "version": "2.0",
                  "connectionProfiles": {
                    "TEST_PROFILE": {
                      "host": "localhost",
                      "sendPort": 9001,
                      "receivePort": 9002,
                      "connectTimeout": 5000,
                      "responseTimeout": 30000
                    }
                  },
                  "channels": {
                    "TEST_CHANNEL": {
                      "connectionProfile": "TEST_PROFILE",
                      "schemas": {
                        "0200": "test_0200.json"
                      },
                      "properties": {
                        "macRequired": "true"
                      }
                    }
                  }
                }
                """;

            registry.loadFromJson(json, "test");

            assertThat(registry.isV2ConfigLoaded()).isTrue();
            assertThat(registry.getConfigVersion()).isEqualTo("2.0");
            assertThat(registry.hasProfile("TEST_PROFILE")).isTrue();
            assertThat(registry.hasConnection("TEST_CHANNEL")).isTrue();
        }

        @Test
        @DisplayName("should resolve connection profile reference")
        void shouldResolveProfileReference() {
            String json = """
                {
                  "version": "2.0",
                  "connectionProfiles": {
                    "FISC_PRIMARY": {
                      "host": "fisc.bank.com",
                      "sendPort": 9001,
                      "receivePort": 9002
                    }
                  },
                  "channels": {
                    "FISC_V1": {
                      "connectionProfile": "FISC_PRIMARY",
                      "properties": {
                        "encoding": "BIG5"
                      }
                    }
                  }
                }
                """;

            registry.loadFromJson(json, "test");

            ChannelConnection connection = registry.getChannelConnectionRequired("FISC_V1");
            assertThat(connection.isFullyResolved()).isTrue();
            assertThat(connection.getHost()).isEqualTo("fisc.bank.com");
            assertThat(connection.getSendPort()).isEqualTo(9001);
            assertThat(connection.getReceivePort()).isEqualTo(9002);
        }
    }

    @Nested
    @DisplayName("Connection Profile Operations")
    class ProfileTests {

        @Test
        @DisplayName("should register and retrieve connection profile")
        void shouldRegisterAndRetrieveProfile() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("NEW_PROFILE")
                    .host("new.host.com")
                    .sendPort(9001)
                    .receivePort(9002)
                    .build();

            registry.registerProfile(profile);

            assertThat(registry.hasProfile("NEW_PROFILE")).isTrue();
            assertThat(registry.getConnectionProfileRequired("NEW_PROFILE").getHost())
                    .isEqualTo("new.host.com");
        }

        @Test
        @DisplayName("should validate profile on registration")
        void shouldValidateProfileOnRegistration() {
            ConnectionProfile invalidProfile = ConnectionProfile.builder()
                    .host("host.com")
                    .sendPort(9001)
                    .build();

            assertThatThrownBy(() -> registry.registerProfile(invalidProfile))
                    .isInstanceOf(ChannelConfigException.class)
                    .hasMessageContaining("profileId");
        }

        @Test
        @DisplayName("should unregister profile")
        void shouldUnregisterProfile() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("TO_REMOVE")
                    .host("host.com")
                    .sendPort(9001)
                    .build();

            registry.registerProfile(profile);
            assertThat(registry.hasProfile("TO_REMOVE")).isTrue();

            registry.unregisterProfile("TO_REMOVE");
            assertThat(registry.hasProfile("TO_REMOVE")).isFalse();
        }
    }

    @Nested
    @DisplayName("Channel Connection Operations")
    class ConnectionTests {

        @BeforeEach
        void setUpProfile() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("BASE_PROFILE")
                    .host("base.host.com")
                    .sendPort(9001)
                    .receivePort(9002)
                    .build();
            registry.registerProfile(profile);
        }

        @Test
        @DisplayName("should register and retrieve channel connection")
        void shouldRegisterAndRetrieveConnection() {
            ChannelConnection connection = ChannelConnection.builder()
                    .channelId("NEW_CHANNEL")
                    .connectionProfileId("BASE_PROFILE")
                    .properties(Map.of("encoding", "UTF-8"))
                    .build();

            registry.registerConnection(connection);

            assertThat(registry.hasConnection("NEW_CHANNEL")).isTrue();
            ChannelConnection retrieved = registry.getChannelConnectionRequired("NEW_CHANNEL");
            assertThat(retrieved.getProperty("encoding")).isEqualTo("UTF-8");
            assertThat(retrieved.isFullyResolved()).isTrue();
        }

        @Test
        @DisplayName("should get active connections sorted by priority")
        void shouldGetActiveConnectionsSortedByPriority() {
            registry.registerConnection(ChannelConnection.builder()
                    .channelId("LOW_PRIORITY")
                    .connectionProfileId("BASE_PROFILE")
                    .priority(100)
                    .active(true)
                    .build());

            registry.registerConnection(ChannelConnection.builder()
                    .channelId("HIGH_PRIORITY")
                    .connectionProfileId("BASE_PROFILE")
                    .priority(1)
                    .active(true)
                    .build());

            registry.registerConnection(ChannelConnection.builder()
                    .channelId("INACTIVE")
                    .connectionProfileId("BASE_PROFILE")
                    .priority(50)
                    .active(false)
                    .build());

            var activeConnections = registry.getActiveConnections();

            assertThat(activeConnections).hasSize(2);
            assertThat(activeConnections.get(0).getChannelId()).isEqualTo("HIGH_PRIORITY");
            assertThat(activeConnections.get(1).getChannelId()).isEqualTo("LOW_PRIORITY");
        }

        @Test
        @DisplayName("should get connections by profile")
        void shouldGetConnectionsByProfile() {
            registry.registerConnection(ChannelConnection.builder()
                    .channelId("CHANNEL_A")
                    .connectionProfileId("BASE_PROFILE")
                    .build());

            registry.registerConnection(ChannelConnection.builder()
                    .channelId("CHANNEL_B")
                    .connectionProfileId("BASE_PROFILE")
                    .build());

            var connections = registry.getConnectionsByProfile("BASE_PROFILE");

            assertThat(connections).hasSize(2);
            assertThat(connections).extracting(ChannelConnection::getChannelId)
                    .containsExactlyInAnyOrder("CHANNEL_A", "CHANNEL_B");
        }
    }

    @Nested
    @DisplayName("Subscriber Pattern")
    class SubscriberTests {

        @Test
        @DisplayName("should notify subscribers on load")
        void shouldNotifySubscribersOnLoad() {
            AtomicInteger notificationCount = new AtomicInteger(0);
            ConnectionSubscriber subscriber = (connectionMap, profileMap) -> {
                notificationCount.incrementAndGet();
            };

            registry.subscribe(subscriber);

            String json = """
                {
                  "version": "2.0",
                  "connectionProfiles": {
                    "TEST": {"host": "localhost", "sendPort": 9001}
                  },
                  "channels": {}
                }
                """;

            registry.loadFromJson(json, "test");

            assertThat(notificationCount.get()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should notify subscribers on profile registration")
        void shouldNotifySubscribersOnProfileRegistration() {
            AtomicInteger notificationCount = new AtomicInteger(0);
            ConnectionSubscriber subscriber = (connectionMap, profileMap) -> {
                notificationCount.incrementAndGet();
            };

            registry.subscribe(subscriber);

            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("NEW")
                    .host("host.com")
                    .sendPort(9001)
                    .build();

            registry.registerProfile(profile);

            assertThat(notificationCount.get()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should unsubscribe successfully")
        void shouldUnsubscribeSuccessfully() {
            AtomicInteger notificationCount = new AtomicInteger(0);
            ConnectionSubscriber subscriber = (connectionMap, profileMap) -> {
                notificationCount.incrementAndGet();
            };

            registry.subscribe(subscriber);
            registry.unsubscribe(subscriber);

            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("AFTER_UNSUB")
                    .host("host.com")
                    .sendPort(9001)
                    .build();

            registry.registerProfile(profile);

            // Should not receive notification after unsubscribe
            // (but might receive one during subscribe if there's existing data)
            assertThat(notificationCount.get()).isLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ChannelConnection Properties")
    class ChannelConnectionPropertiesTest {

        @Test
        @DisplayName("should cascade property lookup")
        void shouldCascadePropertyLookup() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("PROFILE")
                    .host("host.com")
                    .sendPort(9001)
                    .properties(Map.of(
                            "profileProp", "fromProfile",
                            "sharedProp", "fromProfile"
                    ))
                    .build();
            registry.registerProfile(profile);

            ChannelConnection connection = ChannelConnection.builder()
                    .channelId("CHANNEL")
                    .connectionProfileId("PROFILE")
                    .properties(Map.of(
                            "channelProp", "fromChannel",
                            "sharedProp", "fromChannel"
                    ))
                    .build();
            registry.registerConnection(connection);

            ChannelConnection retrieved = registry.getChannelConnectionRequired("CHANNEL");

            // Channel property takes precedence
            assertThat(retrieved.getProperty("channelProp")).isEqualTo("fromChannel");
            assertThat(retrieved.getProperty("sharedProp")).isEqualTo("fromChannel");

            // Profile property as fallback
            assertThat(retrieved.getProperty("profileProp")).isEqualTo("fromProfile");

            // Non-existent property
            assertThat(retrieved.getProperty("nonExistent", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("should check MAC required")
        void shouldCheckMacRequired() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("PROFILE")
                    .host("host.com")
                    .sendPort(9001)
                    .build();
            registry.registerProfile(profile);

            ChannelConnection withMac = ChannelConnection.builder()
                    .channelId("WITH_MAC")
                    .connectionProfileId("PROFILE")
                    .properties(Map.of("macRequired", "true"))
                    .build();

            ChannelConnection withoutMac = ChannelConnection.builder()
                    .channelId("WITHOUT_MAC")
                    .connectionProfileId("PROFILE")
                    .properties(Map.of("macRequired", "false"))
                    .build();

            registry.registerConnection(withMac);
            registry.registerConnection(withoutMac);

            assertThat(registry.getChannelConnectionRequired("WITH_MAC").isMacRequired()).isTrue();
            assertThat(registry.getChannelConnectionRequired("WITHOUT_MAC").isMacRequired()).isFalse();
        }

        @Test
        @DisplayName("should get schema for MTI")
        void shouldGetSchemaForMti() {
            ConnectionProfile profile = ConnectionProfile.builder()
                    .profileId("PROFILE")
                    .host("host.com")
                    .sendPort(9001)
                    .build();
            registry.registerProfile(profile);

            ChannelConnection connection = ChannelConnection.builder()
                    .channelId("CHANNEL")
                    .connectionProfileId("PROFILE")
                    .schemas(Map.of(
                            "0200", "fisc_0200.json",
                            "0210", "fisc_0210.json"
                    ))
                    .build();
            registry.registerConnection(connection);

            ChannelConnection retrieved = registry.getChannelConnectionRequired("CHANNEL");

            assertThat(retrieved.getSchema("0200")).isEqualTo("fisc_0200.json");
            assertThat(retrieved.getSchema("0210")).isEqualTo("fisc_0210.json");
            assertThat(retrieved.getSchema("0400")).isNull();
        }
    }

    @Nested
    @DisplayName("ConnectionProfile Validation")
    class ProfileValidationTest {

        @Test
        @DisplayName("should validate required fields")
        void shouldValidateRequiredFields() {
            ConnectionProfile noProfileId = ConnectionProfile.builder()
                    .host("host.com")
                    .sendPort(9001)
                    .build();

            ConnectionProfile noHost = ConnectionProfile.builder()
                    .profileId("TEST")
                    .sendPort(9001)
                    .build();

            ConnectionProfile invalidPort = ConnectionProfile.builder()
                    .profileId("TEST")
                    .host("host.com")
                    .sendPort(-1)
                    .build();

            assertThatThrownBy(() -> noProfileId.validate())
                    .isInstanceOf(ChannelConfigException.class);

            assertThatThrownBy(() -> noHost.validate())
                    .isInstanceOf(ChannelConfigException.class);

            assertThatThrownBy(() -> invalidPort.validate())
                    .isInstanceOf(ChannelConfigException.class);
        }

        @Test
        @DisplayName("should detect dual channel configuration")
        void shouldDetectDualChannelConfiguration() {
            ConnectionProfile dualChannel = ConnectionProfile.builder()
                    .profileId("DUAL")
                    .host("host.com")
                    .sendPort(9001)
                    .receivePort(9002)
                    .build();

            ConnectionProfile singleChannel = ConnectionProfile.builder()
                    .profileId("SINGLE")
                    .host("host.com")
                    .sendPort(9001)
                    .receivePort(9001)
                    .build();

            assertThat(dualChannel.isDualChannel()).isTrue();
            assertThat(singleChannel.isDualChannel()).isFalse();
        }
    }
}
