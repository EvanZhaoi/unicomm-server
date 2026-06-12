package com.unicomm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ManualDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "unicomm.datasource")
    public DataSourceProperties unicommDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource dataSource(DataSourceProperties unicommDataSourceProperties) {
        return unicommDataSourceProperties.initializeDataSourceBuilder().build();
    }
}
