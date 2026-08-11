package com.xzcpc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgDataSourceConfig {

    @Primary
    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private HikariDataSource pgDs;

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://119.45.162.160:5433/tea_chain");
        config.setUsername("postgres");
        config.setPassword("xzcp123456");
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(120000);
        config.setMaxLifetime(300000);
        config.setKeepaliveTime(60000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("PgHikariPool");
        pgDs = new HikariDataSource(config);
        return new JdbcTemplate(pgDs);
    }

    @PreDestroy
    public void destroy() {
        if (pgDs != null) pgDs.close();
    }
}
