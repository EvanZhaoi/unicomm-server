package com.unicomm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类.
 *
 * <p>配置 MyBatis Plus 插件和相关功能。</p>
 *
 * <p><strong>当前配置:</strong></p>
 * <ul>
 *   <li>PaginationInnerInterceptor - 分页插件，支持 MySQL 分页查询</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see <a href="https://baomidou.com/">MyBatis Plus 官方文档</a>
 * @see MybatisPlusInterceptor
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis Plus 插件链.
     *
     * <p>在此方法中注册所需的插件，如分页插件、乐观锁插件等。</p>
     *
     * @return MybatisPlusInterceptor 实例，包含已配置的插件
     * @since 0.1.0
     * @see MybatisPlusInterceptor
     * @see PaginationInnerInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件 - 支持 MySQL 分页
        // 使用 Page<?> 作为方法参数时自动启用分页
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}