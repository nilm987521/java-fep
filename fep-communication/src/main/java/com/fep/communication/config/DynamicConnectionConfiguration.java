package com.fep.communication.config;

import com.fep.communication.manager.DynamicConnectionManager;
import com.fep.message.channel.ChannelConnectionConfiguration;
import com.fep.message.channel.ChannelConnectionRegistry;
import com.fep.message.channel.ChannelSchemaConfiguration;
import com.fep.message.service.ChannelMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Spring Configuration for Dynamic Connection Management.
 *
 * <p>This configuration sets up the {@link DynamicConnectionManager} which
 * provides runtime management of FISC TCP/IP connections.
 *
 * <p>To enable this feature, set the following property:
 * <pre>
 * fep.connection.dynamic-management.enabled=true
 * </pre>
 *
 * <p>Configuration properties:
 * <ul>
 *   <li><code>fep.connection.auto-connect</code>: Auto-connect on startup (default: true)</li>
 *   <li><code>fep.connection.auto-sign-on</code>: Auto-sign-on after connect (default: true)</li>
 *   <li><code>fep.connection.graceful-shutdown-timeout-ms</code>: Graceful shutdown timeout (default: 10000)</li>
 * </ul>
 *
 * <p>Note: The {@link ChannelConnectionRegistry} bean is created by
 * {@code ChannelConnectionConfiguration} in fep-message module. This configuration
 * only creates the {@link DynamicConnectionManager} and injects the registry.
 *
 * <p>Example application.yml:
 * <pre>
 * fep:
 *   connection:
 *     dynamic-management:
 *       enabled: true
 *     auto-connect: true
 *     auto-sign-on: true
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "fep.connection.dynamic-management.enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter({ChannelSchemaConfiguration.class, ChannelConnectionConfiguration.class})
public class DynamicConnectionConfiguration {

    @Value("${fep.connection.auto-connect:true}")
    private boolean autoConnect;

    @Value("${fep.connection.auto-sign-on:true}")
    private boolean autoSignOn;

    @Value("${fep.connection.graceful-shutdown-timeout-ms:10000}")
    private long gracefulShutdownTimeoutMs;

    /**
     * Creates the DynamicConnectionManager bean.
     *
     * <p>The manager is automatically subscribed to the registry for
     * configuration change notifications.
     *
     * @param registry the channel connection registry
     * @param channelMessageService the channel message service (optional, may be null)
     * @return configured DynamicConnectionManager
     */
    @Bean
    @DependsOn("channelSchemaRegistry")
    public DynamicConnectionManager dynamicConnectionManager(
            ChannelConnectionRegistry registry,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ChannelMessageService channelMessageService) {

        DynamicConnectionManager manager = new DynamicConnectionManager(registry, channelMessageService);

        manager.setAutoConnect(autoConnect);
        manager.setAutoSignOn(autoSignOn);
        manager.setGracefulShutdownTimeoutMs(gracefulShutdownTimeoutMs);

        log.info("DynamicConnectionManager configured: autoConnect={}, autoSignOn={}, " +
                 "gracefulShutdownTimeoutMs={}, channelMessageService={}",
                autoConnect, autoSignOn, gracefulShutdownTimeoutMs,
                channelMessageService != null ? "available" : "not available");

        return manager;
    }
}
