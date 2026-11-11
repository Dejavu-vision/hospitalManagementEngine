package com.curamatrix.hsm.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Forces the correct DataSource configuration, overriding any
 * stale SPRING_DATASOURCE_URL environment variable injected by Render.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://dpg-dbq51ron74is739oc4m0-a.virginia-postgres.render.com:5432/curamatrix_db?sslmode=require");
        config.setUsername("curamatrix_db_user");
        config.setPassword("ut7UPowmHClmLvbPnjuRUbJ7z0nYMJzF");
        config.setDriverClassName("org.postgresql.Driver");
        config.setConnectionTimeout(60000);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setMaxLifetime(1800000);
        config.setKeepaliveTime(30000);
        config.setLeakDetectionThreshold(0);
        return new HikariDataSource(config);
    }
}
