package com.fep.application.controller;

import com.fep.application.dto.ApiResponse;
import com.fep.application.dto.ConnectionStatusDto;
import com.fep.communication.client.DualChannelState;
import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.manager.DynamicConnectionManager;
import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.channel.ChannelConnection;
import com.fep.message.channel.ChannelConnectionRegistry;
import com.fep.message.channel.ConnectionProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for managing FISC TCP/IP connections at runtime.
 *
 * <p>This controller provides endpoints to:
 * <ul>
 *   <li>List all connections and their status</li>
 *   <li>Dynamically add new connections</li>
 *   <li>Dynamically remove connections</li>
 *   <li>Reconnect existing connections</li>
 *   <li>Query connection health status</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * # List all connections
 * GET /api/v1/connections
 *
 * # Get specific connection status
 * GET /api/v1/connections/FISC_INTERBANK_V1/status
 *
 * # Add new connection
 * POST /api/v1/connections/NEW_CHANNEL
 *
 * # Remove connection
 * DELETE /api/v1/connections/OLD_CHANNEL
 *
 * # Reconnect
 * POST /api/v1/connections/FISC_INTERBANK_V1/reconnect
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/v1/connections")
@RequiredArgsConstructor
@Tag(name = "Connection Management", description = "FISC TCP/IP 連線管理 API")
//@ConditionalOnBean(DynamicConnectionManager.class) // Temporarily disabled for debugging
public class ConnectionManagementController {

    private final DynamicConnectionManager connectionManager;
    private final ChannelConnectionRegistry registry;

    /**
     * Lists all connections with their current status (both clients and servers).
     */
    @GetMapping
    @Operation(summary = "列出所有連線", description = "取得所有 FISC 連線的狀態 (包含 CLIENT 和 SERVER 模式)")
    public ResponseEntity<ApiResponse<List<ConnectionStatusDto>>> listConnections() {
        log.debug("Listing all connections");

        Set<String> clientIds = connectionManager.getClientConnectionIds();
        Set<String> serverIds = connectionManager.getServerConnectionIds();
        Set<String> allManagedIds = connectionManager.getAllConnectionIds();
        Map<String, DualChannelState> clientStates = connectionManager.getAllConnectionStates();

        List<ConnectionStatusDto> statusList = new ArrayList<>();

        // Add client connections
        for (String channelId : clientIds) {
            ConnectionStatusDto status = buildClientConnectionStatus(channelId, clientStates.get(channelId));
            statusList.add(status);
        }

        // Add server connections
        for (String channelId : serverIds) {
            ConnectionStatusDto status = buildServerConnectionStatus(channelId);
            statusList.add(status);
        }

        // Add configured but not connected channels
        for (ChannelConnection config : registry.getActiveConnections()) {
            if (!allManagedIds.contains(config.getChannelId())) {
                ConnectionStatusDto status = buildConfiguredStatus(config);
                statusList.add(status);
            }
        }

        // Sort by priority
        statusList.sort(Comparator.comparingInt(ConnectionStatusDto::getPriority));

        return ResponseEntity.ok(ApiResponse.success(statusList));
    }

    /**
     * Gets the status of a specific connection.
     */
    @GetMapping("/{channelId}/status")
    @Operation(summary = "取得連線狀態", description = "取得指定通道的連線狀態")
    public ResponseEntity<ApiResponse<ConnectionStatusDto>> getConnectionStatus(
            @Parameter(description = "通道 ID") @PathVariable String channelId) {
        log.debug("Getting status for channel: {}", channelId);

        // Check if it's a client connection
        if (connectionManager.hasClientConnection(channelId)) {
            Optional<DualChannelState> state = connectionManager.getConnectionState(channelId);
            ConnectionStatusDto status = buildClientConnectionStatus(channelId, state.orElse(null));
            return ResponseEntity.ok(ApiResponse.success(status));
        }

        // Check if it's a server connection
        if (connectionManager.hasServerConnection(channelId)) {
            ConnectionStatusDto status = buildServerConnectionStatus(channelId);
            return ResponseEntity.ok(ApiResponse.success(status));
        }

        // Check if it's configured but not connected
        Optional<ChannelConnection> config = registry.getChannelConnection(channelId);
        if (config.isPresent()) {
            ConnectionStatusDto status = buildConfiguredStatus(config.get());
            return ResponseEntity.ok(ApiResponse.success(status));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("CONN_NOT_FOUND", "Connection not found: " + channelId));
    }

    /**
     * Dynamically adds a new connection.
     */
    @PostMapping("/{channelId}")
    @Operation(summary = "新增連線", description = "動態新增 FISC 連線")
    public ResponseEntity<ApiResponse<ConnectionStatusDto>> addConnection(
            @Parameter(description = "通道 ID") @PathVariable String channelId) {
        log.info("Adding connection: {}", channelId);

        // Check if channel is configured
        Optional<ChannelConnection> config = registry.getChannelConnection(channelId);
        if (config.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("CHANNEL_NOT_CONFIGURED",
                            "Channel not configured in registry: " + channelId));
        }

        // Check if already connected
        if (connectionManager.hasConnection(channelId)) {
            ConnectionStatusDto status = buildStatusForExisting(channelId);
            return ResponseEntity.ok(ApiResponse.success(status, "Connection already exists"));
        }

        // Check if this is server mode
        ConnectionProfile profile = config.get().getResolvedConnectionProfile();
        boolean isServerMode = profile != null && profile.isServerMode();

        try {
            if (isServerMode) {
                // For server mode, addConnection returns null, so we build status differently
                connectionManager.addConnection(channelId);
                ConnectionStatusDto status = buildServerConnectionStatus(channelId);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(status, "Server started successfully"));
            } else {
                FiscDualChannelClient client = connectionManager.addConnection(channelId);
                ConnectionStatusDto status = buildClientConnectionStatus(channelId, client.getState());
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(status, "Connection created successfully"));
            }
        } catch (Exception e) {
            log.error("Failed to add connection {}: {}", channelId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("CONN_FAILED", "Failed to create connection: " + e.getMessage()));
        }
    }

    /**
     * Dynamically removes a connection.
     */
    @DeleteMapping("/{channelId}")
    @Operation(summary = "移除連線", description = "動態移除 FISC 連線")
    public ResponseEntity<ApiResponse<Void>> removeConnection(
            @Parameter(description = "通道 ID") @PathVariable String channelId) {
        log.info("Removing connection: {}", channelId);

        if (!connectionManager.hasConnection(channelId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("CONN_NOT_FOUND", "Connection not found: " + channelId));
        }

        try {
            boolean removed = connectionManager.removeConnection(channelId);
            if (removed) {
                return ResponseEntity.ok(ApiResponse.success(null, "Connection removed successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("REMOVE_FAILED", "Failed to remove connection"));
            }
        } catch (Exception e) {
            log.error("Failed to remove connection {}: {}", channelId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("REMOVE_FAILED", "Failed to remove connection: " + e.getMessage()));
        }
    }

    /**
     * Reconnects an existing connection.
     */
    @PostMapping("/{channelId}/reconnect")
    @Operation(summary = "重新連線", description = "重新建立 FISC 連線")
    public ResponseEntity<ApiResponse<ConnectionStatusDto>> reconnect(
            @Parameter(description = "通道 ID") @PathVariable String channelId) {
        log.info("Reconnecting channel: {}", channelId);

        // Check if channel is configured
        Optional<ChannelConnection> config = registry.getChannelConnection(channelId);
        if (config.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("CHANNEL_NOT_CONFIGURED",
                            "Channel not configured in registry: " + channelId));
        }

        // Check if this is server mode
        ConnectionProfile profile = config.get().getResolvedConnectionProfile();
        boolean isServerMode = profile != null && profile.isServerMode();

        if (isServerMode) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("SERVER_MODE", "Server connections cannot be reconnected. Stop and start instead."));
        }

        try {
            FiscDualChannelClient client = connectionManager.reconnect(channelId);
            ConnectionStatusDto status = buildClientConnectionStatus(channelId, client.getState());
            return ResponseEntity.ok(ApiResponse.success(status, "Reconnection successful"));
        } catch (Exception e) {
            log.error("Failed to reconnect {}: {}", channelId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("RECONNECT_FAILED", "Failed to reconnect: " + e.getMessage()));
        }
    }

    /**
     * Gets summary statistics of all connections.
     */
    @GetMapping("/summary")
    @Operation(summary = "連線摘要", description = "取得連線統計摘要")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConnectionSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        int totalClients = connectionManager.getClientConnectionCount();
        int totalServers = connectionManager.getServerConnectionCount();
        int total = connectionManager.getConnectionCount();
        int active = connectionManager.getActiveConnectionIds().size();
        int connected = connectionManager.getConnectedConnectionIds().size();
        int configuredTotal = registry.getActiveConnections().size();

        summary.put("totalManaged", total);
        summary.put("totalClients", totalClients);
        summary.put("totalServers", totalServers);
        summary.put("signedOn", active);
        summary.put("connected", connected);
        summary.put("disconnected", total - connected);
        summary.put("configuredTotal", configuredTotal);
        summary.put("unconfigured", configuredTotal - total);

        // Count connected clients across all servers
        int totalConnectedClients = 0;
        for (String serverId : connectionManager.getServerConnectionIds()) {
            connectionManager.getServerConnection(serverId).ifPresent(server -> {
                // Note: getConnectedClientCount() should be added to FiscDualChannelServer
            });
        }
        summary.put("serverConnectedClients", totalConnectedClients);

        Map<String, DualChannelState> states = connectionManager.getAllConnectionStates();
        Map<String, Long> stateDistribution = states.values().stream()
                .collect(Collectors.groupingBy(Enum::name, Collectors.counting()));
        summary.put("clientStateDistribution", stateDistribution);

        summary.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * Lists all configured channels (from registry).
     */
    @GetMapping("/configured")
    @Operation(summary = "已配置通道", description = "列出所有已配置的通道")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listConfiguredChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();

        for (ChannelConnection config : registry.getActiveConnections()) {
            Map<String, Object> channel = new LinkedHashMap<>();
            channel.put("channelId", config.getChannelId());
            channel.put("connectionProfileId", config.getConnectionProfileId());
            channel.put("active", config.isActive());
            channel.put("priority", config.getPriority());
            channel.put("description", config.getDescription());

            ConnectionProfile profile = config.getResolvedConnectionProfile();
            if (profile != null) {
                channel.put("host", profile.getHost());
                channel.put("sendPort", profile.getSendPort());
                channel.put("receivePort", profile.getEffectiveReceivePort());
                channel.put("isDualChannel", profile.isDualChannel());
                channel.put("connectionMode", profile.isServerMode() ? "SERVER" : "CLIENT");
            }

            channel.put("isConnected", connectionManager.hasConnection(config.getChannelId()));
            channel.put("isClient", connectionManager.hasClientConnection(config.getChannelId()));
            channel.put("isServer", connectionManager.hasServerConnection(config.getChannelId()));
            channels.add(channel);
        }

        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    // ==================== Helper Methods ====================

    /**
     * Builds connection status for an existing connection (client or server).
     */
    private ConnectionStatusDto buildStatusForExisting(String channelId) {
        if (connectionManager.hasClientConnection(channelId)) {
            Optional<DualChannelState> state = connectionManager.getConnectionState(channelId);
            return buildClientConnectionStatus(channelId, state.orElse(null));
        } else if (connectionManager.hasServerConnection(channelId)) {
            return buildServerConnectionStatus(channelId);
        }
        return ConnectionStatusDto.builder()
                .channelId(channelId)
                .state("UNKNOWN")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Builds connection status for a CLIENT mode connection.
     */
    private ConnectionStatusDto buildClientConnectionStatus(String channelId, DualChannelState state) {
        Optional<FiscDualChannelClient> clientOpt = connectionManager.getConnection(channelId);
        Optional<ChannelConnection> configOpt = registry.getChannelConnection(channelId);

        ConnectionStatusDto.ConnectionStatusDtoBuilder builder = ConnectionStatusDto.builder()
                .channelId(channelId)
                .state(state != null ? state.name() : "UNKNOWN")
                .connectionMode("CLIENT")
                .timestamp(LocalDateTime.now());

        clientOpt.ifPresent(client -> {
            builder.sendChannelConnected(client.isSendChannelConnected());
            builder.receiveChannelConnected(client.isReceiveChannelConnected());
            builder.signedOn(client.isSignedOn());
        });

        configOpt.ifPresent(config -> {
            builder.connectionProfileId(config.getConnectionProfileId());
            builder.active(config.isActive());
            builder.priority(config.getPriority());

            ConnectionProfile profile = config.getResolvedConnectionProfile();
            if (profile != null) {
                builder.host(profile.getHost());
                builder.sendPort(profile.getSendPort());
                builder.receivePort(profile.getEffectiveReceivePort());
            }
        });

        return builder.build();
    }

    /**
     * Builds connection status for a SERVER mode connection.
     */
    private ConnectionStatusDto buildServerConnectionStatus(String channelId) {
        Optional<FiscDualChannelServer> serverOpt = connectionManager.getServerConnection(channelId);
        Optional<ChannelConnection> configOpt = registry.getChannelConnection(channelId);

        ConnectionStatusDto.ConnectionStatusDtoBuilder builder = ConnectionStatusDto.builder()
                .channelId(channelId)
                .connectionMode("SERVER")
                .timestamp(LocalDateTime.now());

        serverOpt.ifPresent(server -> {
            builder.state(server.getState() != null ? server.getState().name() : "UNKNOWN");
            builder.sendChannelConnected(server.isRunning());
            builder.receiveChannelConnected(server.isRunning());
            builder.signedOn(false); // Servers don't sign on
            builder.connectedClients(server.getConnectedClientCount());
            builder.sendPort(server.getActualSendPort());
            builder.receivePort(server.getActualReceivePort());
        });

        configOpt.ifPresent(config -> {
            builder.connectionProfileId(config.getConnectionProfileId());
            builder.active(config.isActive());
            builder.priority(config.getPriority());

            ConnectionProfile profile = config.getResolvedConnectionProfile();
            if (profile != null) {
                builder.host(profile.getHost());
            }
        });

        return builder.build();
    }

    /**
     * Builds status for a configured but not connected channel.
     */
    private ConnectionStatusDto buildConfiguredStatus(ChannelConnection config) {
        ConnectionProfile profile = config.getResolvedConnectionProfile();
        String mode = (profile != null && profile.isServerMode()) ? "SERVER" : "CLIENT";

        ConnectionStatusDto.ConnectionStatusDtoBuilder builder = ConnectionStatusDto.builder()
                .channelId(config.getChannelId())
                .state("NOT_CONNECTED")
                .connectionMode(mode)
                .connectionProfileId(config.getConnectionProfileId())
                .active(config.isActive())
                .priority(config.getPriority())
                .sendChannelConnected(false)
                .receiveChannelConnected(false)
                .signedOn(false)
                .timestamp(LocalDateTime.now());

        if (profile != null) {
            builder.host(profile.getHost());
            builder.sendPort(profile.getSendPort());
            builder.receivePort(profile.getEffectiveReceivePort());
        }

        return builder.build();
    }
}
