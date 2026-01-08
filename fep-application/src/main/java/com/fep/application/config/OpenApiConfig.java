package com.fep.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fepOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FEP System API")
                        .description("前端處理器（FEP）系統 API 文件\n\n" +
                                "用於處理與財金公司（FISC）的跨行交易，包括：\n" +
                                "- ATM 跨行提款/轉帳/餘額查詢\n" +
                                "- 代收代付/繳費交易\n" +
                                "- 行動支付/台灣 Pay\n" +
                                "- 系統監控與管理")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FEP System Team")
                                .email("fep-team@bank.com.tw"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://bank.com.tw")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("Development Server"),
                        new Server()
                                .url("https://fep.bank.com.tw/api")
                                .description("Production Server")
                ));
    }
}
