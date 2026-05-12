package com.unicomm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类.
 *
 * <p>配置分页插件等常用功能.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @see <a href="https://baomidou.com/">MyBatis Plus 文档</a>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis Plus 插件链.
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
