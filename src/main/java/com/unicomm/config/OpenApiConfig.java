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
 * <p>配置 API 文档信息，用于生成 Swagger UI 和 OpenAPI 规范文档。</p>
 *
 * <p><strong>访问地址:</strong></p>
 * <ul>
 *   <li>Swagger UI: http://localhost:28080/swagger-ui.html</li>
 *   <li>OpenAPI JSON: http://localhost:28080/v3/api-docs</li>
 *   <li>OpenAPI YAML: http://localhost:28080/v3/api-docs.yaml</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see <a href="https://springdoc.org/">SpringDoc OpenAPI 官方文档</a>
 * @see OpenAPI
 */
@Configuration
public class OpenApiConfig {

    /**
     * 服务器端口.
     *
     * <p>从 server.port 配置项读取，默认 28080。</p>
     */
    @Value("${server.port:28080}")
    private int serverPort;

    /**
     * 配置 OpenAPI 文档信息.
     *
     * <p>包括 API 标题、描述、版本、联系方式、许可证，
     * 以及服务器信息。</p>
     *
     * @return OpenAPI 文档配置
     * @since 0.1.0
     * @see Info
     * @see Server
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