package com.unicomm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置类.
 *
 * <p>配置跨域资源共享策略，允许前端应用访问 API.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Configuration
public class CorsConfig {

    /**
     * 配置 CORS 过滤器.
     *
     * @return CorsFilter
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许来源 (生产环境建议指定具体域名)
        config.addAllowedOriginPattern("*");

        // 允许携带认证信息
        config.setAllowCredentials(true);

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有请求方法
        config.addAllowedMethod("*");

        // 暴露响应头 (使客户端可以访问到这些响应头)
        config.addExposedHeader("unicomm-token");

        // 预检请求缓存时间
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
