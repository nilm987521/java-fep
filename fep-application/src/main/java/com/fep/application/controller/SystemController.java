package com.fep.application.controller;

import com.fep.application.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * System Status API Controller
 */
@Slf4j
@RestController
@RequestMapping("/v1/system")
@RequiredArgsConstructor
@Tag(name = "System API", description = "系統狀態 API")
public class SystemController {

    @GetMapping("/status")
    @Operation(summary = "系統狀態", description = "取得系統運行狀態")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Basic info
        status.put("application", "FEP System");
        status.put("version", "1.0.0-SNAPSHOT");
        status.put("status", "RUNNING");
        status.put("timestamp", LocalDateTime.now());

        // Runtime info
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtimeBean.getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        status.put("uptime", formatDuration(uptime));
        status.put("uptimeMs", uptimeMs);

        // Memory info
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        status.put("heapUsedMB", heapUsed / (1024 * 1024));
        status.put("heapMaxMB", heapMax / (1024 * 1024));
        status.put("heapUsagePercent", String.format("%.1f%%", (double) heapUsed / heapMax * 100));

        // CPU info
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        status.put("availableProcessors", availableProcessors);

        // JVM info
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("javaVendor", System.getProperty("java.vendor"));

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/health")
    @Operation(summary = "健康檢查", description = "系統健康狀態檢查")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());

        // Component health checks
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", "UP");      // TODO: Check actual DB connection
        components.put("fisc", "UP");          // TODO: Check FISC connection
        components.put("hsm", "UP");           // TODO: Check HSM connection
        components.put("redis", "UP");         // TODO: Check Redis connection
        health.put("components", components);

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/metrics")
    @Operation(summary = "效能指標", description = "取得系統效能指標")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Transaction metrics (mock data)
        Map<String, Object> transactions = new LinkedHashMap<>();
        transactions.put("totalCount", 125000L);
        transactions.put("successCount", 124500L);
        transactions.put("failureCount", 500L);
        transactions.put("successRate", "99.6%");
        transactions.put("currentTps", 150);
        transactions.put("peakTps", 2500);
        transactions.put("avgResponseTimeMs", 45);
        transactions.put("p95ResponseTimeMs", 120);
        transactions.put("p99ResponseTimeMs", 250);
        metrics.put("transactions", transactions);

        // Connection metrics (mock data)
        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("fiscActive", 5);
        connections.put("fiscMax", 20);
        connections.put("dbActive", 10);
        connections.put("dbMax", 50);
        metrics.put("connections", connections);

        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) {
            return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
