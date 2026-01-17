package com.fep.application.integration;

import com.fep.message.channel.Channel;
import com.fep.message.channel.ChannelSchemaProperties;
import com.fep.message.channel.ChannelSchemaRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify ChannelSchemaRegistry is properly loaded
 * when the Spring Boot application starts.
 */
@SpringBootTest(properties = {
    "fep.channel.config-file=config/channel-schema-mapping.json",
    "fep.channel.fail-on-missing-config=true"
})
@ActiveProfiles("dev")
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@DisplayName("ChannelSchemaRegistry Application Integration Tests")
class ChannelSchemaRegistryIntegrationTest {

    @Autowired
    private ChannelSchemaRegistry channelSchemaRegistry;

    @Autowired
    private ChannelSchemaProperties channelSchemaProperties;

    @Test
    @DisplayName("ChannelSchemaRegistry bean should be autowired")
    void registryBeanShouldBeAutowired() {
        assertThat(channelSchemaRegistry).isNotNull();
    }

    @Test
    @DisplayName("ChannelSchemaProperties bean should be autowired")
    void propertiesBeanShouldBeAutowired() {
        assertThat(channelSchemaProperties).isNotNull();
        assertThat(channelSchemaProperties.getConfigFile()).isNotBlank();
    }

    @Test
    @DisplayName("Channels should be loaded from config file")
    void channelsShouldBeLoadedFromConfigFile() {
        Collection<Channel> allChannels = channelSchemaRegistry.getAllChannels();

        // Based on config/channel-schema-mapping.json, we expect 9 channels
        assertThat(allChannels).isNotEmpty();

        // Verify some expected channels exist
        assertThat(channelSchemaRegistry.getChannel("ATM_FISC_V1")).isPresent();
        assertThat(channelSchemaRegistry.getChannel("ATM_NCR_V1")).isPresent();
        assertThat(channelSchemaRegistry.getChannel("FISC_INTERBANK_V1")).isPresent();
    }

    @Test
    @DisplayName("Should query channels by type")
    void shouldQueryChannelsByType() {
        List<Channel> atmChannels = channelSchemaRegistry.getChannelsByType("ATM");

        assertThat(atmChannels).isNotEmpty();
        assertThat(atmChannels).allMatch(c -> "ATM".equals(c.getType()));
    }

    @Test
    @DisplayName("Should query channels by vendor")
    void shouldQueryChannelsByVendor() {
        List<Channel> fiscChannels = channelSchemaRegistry.getChannelsByVendor("FISC");

        assertThat(fiscChannels).isNotEmpty();
        assertThat(fiscChannels).allMatch(c -> "FISC".equals(c.getVendor()));
    }

    @Test
    @DisplayName("Should get active channels only")
    void shouldGetActiveChannelsOnly() {
        List<Channel> activeChannels = channelSchemaRegistry.getActiveChannels();

        assertThat(activeChannels).isNotEmpty();
        assertThat(activeChannels).allMatch(Channel::isActive);
    }

    @Test
    @DisplayName("Channel details should be correctly loaded")
    void channelDetailsShouldBeCorrectlyLoaded() {
        Channel fiscChannel = channelSchemaRegistry.getChannel("ATM_FISC_V1").orElse(null);

        assertThat(fiscChannel).isNotNull();
        assertThat(fiscChannel.getName()).isEqualTo("FISC ATM Channel");
        assertThat(fiscChannel.getType()).isEqualTo("ATM");
        assertThat(fiscChannel.getVendor()).isEqualTo("FISC");
        assertThat(fiscChannel.getDefaultRequestSchema()).isEqualTo("FISC ATM Format");
        assertThat(fiscChannel.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should support runtime channel registration")
    void shouldSupportRuntimeChannelRegistration() {
        int initialCount = channelSchemaRegistry.getAllChannels().size();

        // Register a new channel
        Channel testChannel = Channel.builder()
                .id("TEST_RUNTIME_V1")
                .name("Test Runtime Channel")
                .type("TEST")
                .vendor("TEST")
                .version("1.0")
                .active(true)
                .defaultRequestSchema("Test Schema")
                .defaultResponseSchema("Test Schema")
                .build();

        channelSchemaRegistry.registerChannel(testChannel);

        // Verify channel was added
        assertThat(channelSchemaRegistry.getAllChannels()).hasSize(initialCount + 1);
        assertThat(channelSchemaRegistry.getChannel("TEST_RUNTIME_V1")).isPresent();

        // Cleanup
        channelSchemaRegistry.unregisterChannel("TEST_RUNTIME_V1");
        assertThat(channelSchemaRegistry.getAllChannels()).hasSize(initialCount);
    }

    @Test
    @DisplayName("ChannelSchemaRegistry should be singleton")
    void registryShouldBeSingleton() {
        ChannelSchemaRegistry singleton = ChannelSchemaRegistry.getInstance();
        assertThat(channelSchemaRegistry).isSameAs(singleton);
    }
}
