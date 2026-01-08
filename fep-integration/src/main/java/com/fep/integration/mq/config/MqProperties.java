package com.fep.integration.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IBM MQ connection properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "fep.mq")
public class MqProperties {

    /**
     * Queue Manager name.
     */
    private String queueManager = "QM1";

    /**
     * MQ host address.
     */
    private String host = "localhost";

    /**
     * MQ port (default 1414 for IBM MQ).
     */
    private int port = 1414;

    /**
     * Channel name for client connection.
     */
    private String channel = "DEV.APP.SVRCONN";

    /**
     * User name for authentication (optional).
     */
    private String username;

    /**
     * Password for authentication (optional).
     */
    private String password;

    /**
     * Request queue name.
     */
    private String requestQueue = "CBS.REQUEST.QUEUE";

    /**
     * Reply queue name.
     */
    private String replyQueue = "CBS.REPLY.QUEUE";

    /**
     * Connection timeout in milliseconds.
     */
    private int connectionTimeout = 30000;

    /**
     * Request timeout in milliseconds (default 10s).
     */
    private int requestTimeout = 10000;

    /**
     * Maximum number of connections in pool.
     */
    private int maxConnections = 10;

    /**
     * Minimum number of connections in pool.
     */
    private int minConnections = 2;

    /**
     * Enable SSL/TLS connection.
     */
    private boolean sslEnabled = false;

    /**
     * SSL cipher suite.
     */
    private String sslCipherSuite;

    /**
     * Character encoding for message payload (default Big5 for mainframe).
     */
    private String encoding = "Big5";

    /**
     * Enable transaction support.
     */
    private boolean transactional = true;

    /**
     * Message format (default MQSTR for string).
     */
    private String messageFormat = "MQSTR";
}
