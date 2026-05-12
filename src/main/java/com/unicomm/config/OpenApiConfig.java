package com.unicomm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI 配置类.
 *
 * <p>配置 API 文档信息，Swagger UI 访问地址: /swagger-ui.html</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @see <a href="https://springdoc.org/">SpringDoc OpenAPI 文档</a>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:28080}")
    private int serverPort;

    /**
     * 配置 OpenAPI 文档信息.
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI unicommOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UniComm API")
                        .description("UniComm 统一通讯平台后端 API 文档")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("UniComm Team")
                                .email("dev@unicomm.local"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://unicomm.local/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地开发服务器")
                ));
    }
}
