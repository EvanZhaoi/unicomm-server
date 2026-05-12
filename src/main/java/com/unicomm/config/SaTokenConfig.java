package com.unicomm.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类.
 *
 * <p>配置桌面端 Token 认证，使用 stateless 模式.</p>
 * <p>Sa-Token 在此项目中作为桌面客户端的会话令牌管理器，
 * 用于验证客户端携带的 Token 而非传统的登录表单认证.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @see <a href="https://sa-token.dev33.cn/">Sa-Token 文档</a>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器.
     * <p>Phase 1: 暂不启用全局拦截，所有接口公开.
     * Phase 2+ 将对特定接口启用认证拦截.</p>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Phase 1: 公开所有接口，暂不启用认证拦截
        // Phase 2+ 示例:
        // registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
        //         .addPathPatterns("/api/v1/**")
        //         .excludePathPatterns(
        //             "/api/v1/auth/desktop/verify",
        //             "/swagger-ui/**",
        //             "/v3/api-docs/**"
        //         );
    }
}
