package com.fep.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FEP System - Main Application Entry Point
 *
 * <p>Front-End Processor for FISC Interbank Transactions</p>
 *
 * <p>Modules included:</p>
 * <ul>
 *   <li>fep-message: ISO 8583 message parsing</li>
 *   <li>fep-communication: FISC TCP/IP connection management</li>
 *   <li>fep-transaction: Transaction processing</li>
 *   <li>fep-security: HSM/PIN/MAC security</li>
 *   <li>fep-settlement: Reconciliation and clearing</li>
 * </ul>
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.fep")
@EnableJpaRepositories(basePackages = "com.fep")
@EntityScan(basePackages = "com.fep")
@EnableAsync
@EnableScheduling
public class FepApplication {

    public static void main(String[] args) {
        SpringApplication.run(FepApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("==============================================");
        log.info("  FEP System Started Successfully");
        log.info("  Version: 1.0.0-SNAPSHOT");
        log.info("==============================================");
        log.info("  API Documentation: http://localhost:8080/api/swagger-ui.html");
        log.info("  Health Check: http://localhost:8080/api/actuator/health");
        log.info("==============================================");
    }
}
