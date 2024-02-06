package com.evento.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.LockRegistry;

import javax.sql.DataSource;

@Configuration
public class LockConfiguration {
	@Bean
	DefaultLockRepository defaultLockRepository(DataSource dataSource) {
		return new DefaultLockRepository(dataSource);
	}

	@Bean
	JdbcLockRegistry jdbcLockRegistry(LockRepository repository) {
		return new JdbcLockRegistry(repository);
	}

	@Bean
	LockRegistryLeaderInitiator leaderInitiator(LockRegistry lockRegistry) {
		return new LockRegistryLeaderInitiator(lockRegistry);
	}
}
