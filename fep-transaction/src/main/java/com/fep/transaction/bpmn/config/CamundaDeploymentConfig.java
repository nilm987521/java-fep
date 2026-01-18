package com.fep.transaction.bpmn.config;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

/**
 * Camunda BPMN 自動部署配置
 *
 * <p>在應用程式啟動時自動部署 BPMN 流程定義
 */
@Slf4j
@Configuration
public class CamundaDeploymentConfig {

    /**
     * 自動部署所有 BPMN 流程
     */
    @Bean
    public CommandLineRunner deployBpmnProcesses(RepositoryService repositoryService) {
        return args -> {
            log.info("Starting BPMN auto-deployment...");

            try {
                // 搜尋所有 BPMN 檔案
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("classpath*:bpmn/*.bpmn");

                if (resources.length == 0) {
                    log.info("No BPMN files found in classpath:bpmn/");
                    return;
                }

                for (Resource resource : resources) {
                    deployBpmnResource(repositoryService, resource);
                }

                log.info("BPMN auto-deployment completed. Total: {} processes", resources.length);

            } catch (IOException e) {
                log.error("Failed to scan BPMN resources: {}", e.getMessage());
            }
        };
    }

    private void deployBpmnResource(RepositoryService repositoryService, Resource resource) {
        String resourceName = resource.getFilename();

        try {
            // 檢查是否已部署相同的流程
            long existingCount = repositoryService.createDeploymentQuery()
                .deploymentName(resourceName)
                .count();

            if (existingCount > 0) {
                log.info("BPMN already deployed: {} (skipping)", resourceName);
                return;
            }

            // 部署 BPMN
            DeploymentBuilder builder = repositoryService.createDeployment()
                .name(resourceName)
                .addInputStream(resourceName, resource.getInputStream())
                .enableDuplicateFiltering(true);

            Deployment deployment = builder.deploy();

            log.info("Deployed BPMN: {} -> deploymentId={}", resourceName, deployment.getId());

        } catch (Exception e) {
            log.error("Failed to deploy BPMN {}: {}", resourceName, e.getMessage());
        }
    }
}
