package com.evento.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Replaces Spring Boot's SQL initializer with {@link AdvisoryLockedSchemaInitializer}, which
 * serialises {@code schema.sql} across concurrently starting servers. Boot's
 * {@code DataSourceInitializationAutoConfiguration} backs off because this bean is an
 * {@link ApplicationScriptDatabaseInitializer}; the {@code spring.sql.init.*} properties and the
 * "initialise before JPA" ordering it provided are re-established here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlInitializationProperties.class)
@Import(DatabaseInitializationDependencyConfigurer.class)
public class SchemaInitializationConfiguration {

    @Bean
    AdvisoryLockedSchemaInitializer dataSourceScriptDatabaseInitializer(
            DataSource dataSource,
            SqlInitializationProperties properties,
            @Value("${evento.es.schema.enforce-primary-key:true}") boolean enforcePrimaryKey) {
        return new AdvisoryLockedSchemaInitializer(
                determineDataSource(dataSource, properties.getUsername(), properties.getPassword()),
                ApplicationScriptDatabaseInitializer.getSettings(properties),
                enforcePrimaryKey);
    }

    /** Same rule as Boot's auto-configuration: dedicated init credentials when both are set. */
    private static DataSource determineDataSource(DataSource dataSource, String username, String password) {
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            return DataSourceBuilder.derivedFrom(dataSource)
                    .username(username)
                    .password(password)
                    .type(SimpleDriverDataSource.class)
                    .build();
        }
        return dataSource;
    }
}
