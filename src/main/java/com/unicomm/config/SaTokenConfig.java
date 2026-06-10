package com.unicomm.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类.
 *
 * <p>配置桌面端 Token 认证相关的拦截器。</p>
 *
 * <p><strong>关于 Sa-Token:</strong></p>
 * <ul>
 *   <li>Sa-Token 是轻量级 Java 权限认证框架</li>
 *   <li>在此项目中作为桌面客户端的会话令牌管理器</li>
 *   <li>用于验证客户端携带的 Token 而非传统的登录表单认证</li>
 * </ul>
 *
 * <p><strong>认证流程:</strong></p>
 * <ol>
 *   <li>桌面客户端通过 {@code /api/v1/auth/desktop/verify} 获取 Token</li>
 *   <li>客户端在后续请求的 Header 中携带 Token</li>
 *   <li>Sa-Token 拦截器验证 Token 有效性</li>
 *   <li>验证通过后可以通过 {@code StpUtil.getLoginId()} 获取当前用户 ID</li>
 * </ol>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see <a href="https://sa-token.dev33.cn/">Sa-Token 官方文档</a>
 * @see SaInterceptor
 * @see StpUtil
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器.
     *
     * @param registry 拦截器注册表
     * @since 0.1.0
     * @see SaInterceptor
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/desktop/verify",
                        "/api/v1/auth/desktop/device/verify",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                );
    }
}
