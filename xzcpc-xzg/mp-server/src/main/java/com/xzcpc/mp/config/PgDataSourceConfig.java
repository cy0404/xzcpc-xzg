package com.xzcpc.mp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Configuration
public class PgDataSourceConfig {

    @Primary
    @Bean
    public JdbcTemplate mysqlJdbcTemplate(javax.sql.DataSource dataSource) {
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
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("PgHikariPool");
        pgDs = new HikariDataSource(config);
        return new JdbcTemplate(pgDs);
    }

    @PreDestroy
    public void destroy() {
        if (pgDs != null) pgDs.close();
    }
}
