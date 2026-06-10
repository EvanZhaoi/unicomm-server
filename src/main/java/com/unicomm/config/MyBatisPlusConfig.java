package com.unicomm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础配置。
 *
 * <p>当前项目仍保留大量手写 SQL，因为 Memo 列表、权限和聚合查询已经比较复杂。
 * MyBatis-Plus 先用于低风险实体和 Mapper 的迁移，后续按模块逐步替换重复 CRUD。</p>
 */
@Configuration
@MapperScan("com.unicomm.module.**.mapper")
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
