package com.unicomm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置类.
 *
 * <p>配置跨域资源共享 (Cross-Origin Resource Sharing) 策略，
 * 允许前端应用访问后端 API。</p>
 *
 * <p><strong>当前配置允许:</strong></p>
 * <ul>
 *   <li>所有来源 (*)</li>
 *   <li>所有请求头</li>
 *   <li>所有请求方法</li>
 *   <li>携带认证信息 (cookies, authorization headers)</li>
 *   <li>暴露响应头 unicomm-token (使客户端可访问)</li>
 * </ul>
 *
 * <p><strong>生产环境建议:</strong></p>
 * <ul>
 *   <li>将 addAllowedOriginPattern("*") 改为具体域名</li>
 *   <li>限制允许的请求头和方法</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see CorsConfiguration
 * @see CorsFilter
 */
@Configuration
public class CorsConfig {

    /**
     * 配置 CORS 过滤器.
     *
     * <p>创建并配置 CorsFilter bean，应用到所有路由。</p>
     *
     * @return CorsFilter 实例
     * @since 0.1.0
     * @see CorsConfiguration
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有来源 (生产环境建议指定具体域名，如 "https://example.com")
        config.addAllowedOriginPattern("*");

        // 允许携带认证信息 (Cookie, Authorization Header 等)
        config.setAllowCredentials(true);

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有请求方法 (GET, POST, PUT, DELETE 等)
        config.addAllowedMethod("*");

        // 暴露响应头 - 使客户端 JavaScript 可以读取这些响应头
        // unicomm-token: 自定义响应头，用于返回认证 Token
        config.addExposedHeader("unicomm-token");

        // 预检请求 (OPTIONS) 缓存时间 (秒)
        // 在此时间内，浏览器不会发送预检请求
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 应用 CORS 配置到所有路径
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}