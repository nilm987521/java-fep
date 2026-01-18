package com.fep.transaction.bpmn;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for BPMN workflow integration tests.
 * Provides a minimal Spring Boot context with Camunda BPM.
 */
@SpringBootApplication
@EnableProcessApplication
@ComponentScan(basePackages = {
    "com.fep.transaction.bpmn"
})
public class BpmnTestConfiguration {

    // Camunda services are auto-configured by camunda-bpm-spring-boot-starter
    // No additional bean definitions needed for basic tests
}
