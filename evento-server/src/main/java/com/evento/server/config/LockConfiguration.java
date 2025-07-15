package com.evento.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class LockConfiguration {
	private static final Logger logger = LogManager.getLogger(LockConfiguration.class);
	@Value("${evento.bundle.lock.ttl:60000}")
	private int timeToLive;

	@Bean
	public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
		if (dataSource == null) {
			throw new IllegalStateException("DataSource must not be null for LockRepository");
		}

		DefaultLockRepository repository = new DefaultLockRepository(dataSource);
		repository.setTimeToLive(timeToLive);
		repository.setTransactionManager(new DataSourceTransactionManager(dataSource));

		logger.info("Initialized DefaultLockRepository TTL={} ms",  timeToLive);
		return repository;
	}

	@Bean
	public JdbcLockRegistry jdbcLockRegistry(LockRepository repository) {
		logger.info("Creating JdbcLockRegistry with repository: {}", repository.getClass().getSimpleName());
		return new JdbcLockRegistry(repository);
	}

	@Bean
	public LockRegistryLeaderInitiator leaderInitiator(LockRegistry lockRegistry) {
		logger.info("Initializing LockRegistryLeaderInitiator");
		return new LockRegistryLeaderInitiator(lockRegistry);
	}
}
