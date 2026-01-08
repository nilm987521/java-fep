package com.fep.integration.mq.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

import jakarta.jms.ConnectionFactory;

/**
 * IBM MQ connection configuration.
 * Uses IBM MQ Spring Boot Starter for auto-configuration.
 *
 * <p>Configure IBM MQ connection in application.yml:
 * <pre>
 * ibm:
 *   mq:
 *     queue-manager: QM1
 *     channel: SVRCONN.CHANNEL
 *     conn-name: localhost(1414)
 *     user: mquser
 *     password: mqpassword
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqConnectionConfig {

    private final MqProperties mqProperties;

    /**
     * Creates caching connection factory for connection pooling.
     * The ConnectionFactory is auto-configured by IBM MQ Spring Boot Starter.
     */
    @Bean
    public CachingConnectionFactory cachingConnectionFactory(ConnectionFactory connectionFactory) {
        CachingConnectionFactory cachingFactory = new CachingConnectionFactory(connectionFactory);
        cachingFactory.setSessionCacheSize(mqProperties.getMaxConnections());
        cachingFactory.setCacheProducers(true);
        cachingFactory.setCacheConsumers(true);
        cachingFactory.setReconnectOnException(true);

        log.info("CachingConnectionFactory configured with sessionCacheSize={}",
                mqProperties.getMaxConnections());

        return cachingFactory;
    }

    /**
     * Creates JmsTemplate for synchronous request-reply messaging.
     */
    @Bean
    public JmsTemplate jmsTemplate(CachingConnectionFactory cachingConnectionFactory) {
        JmsTemplate template = new JmsTemplate(cachingConnectionFactory);
        template.setReceiveTimeout(mqProperties.getRequestTimeout());
        template.setDefaultDestinationName(mqProperties.getRequestQueue());

        // Enable explicit QoS (Quality of Service)
        template.setExplicitQosEnabled(true);

        // Set time to live for messages (optional)
        template.setTimeToLive(mqProperties.getRequestTimeout());

        log.info("JmsTemplate configured with defaultQueue={}, timeout={}ms",
                mqProperties.getRequestQueue(),
                mqProperties.getRequestTimeout());

        return template;
    }
}
