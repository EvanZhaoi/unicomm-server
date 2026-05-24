package com.unicomm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * UniComm 应用启动类.
 *
 * <p>Spring Boot 应用程序入口，负责启动整个后端服务。</p>
 * <p>访问地址示例:
 * <ul>
 *   <li>本地开发: http://localhost:28080</li>
 *   <li>API 文档: http://localhost:28080/swagger-ui.html</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see <a href="https://spring.io/projects/spring-boot">Spring Boot</a>
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class UniCommApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniCommApplication.class, args);
    }
}
