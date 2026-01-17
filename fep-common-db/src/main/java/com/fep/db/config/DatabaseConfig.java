package com.fep.db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Database configuration for Oracle connectivity.
 * Activated when 'oracle' profile is enabled.
 */
@Configuration
@Profile({"oracle", "oracle-prod"})
@EnableTransactionManagement
public class DatabaseConfig {

    /**
     * Creates a NamedParameterJdbcTemplate with optimized settings for FEP.
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(100);
        jdbcTemplate.setQueryTimeout(30);
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Creates a standard JdbcTemplate for simple queries.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(100);
        jdbcTemplate.setQueryTimeout(30);
        return jdbcTemplate;
    }
}
