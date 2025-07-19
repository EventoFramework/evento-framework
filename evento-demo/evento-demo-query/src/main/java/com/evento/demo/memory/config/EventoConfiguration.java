package com.evento.demo.memory.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.common.performance.ThreadCountAutoscalingProtocol;
import com.evento.common.utils.Context;
import com.evento.consumer.state.store.postgres.PostgresConsumerStateStore;
import com.evento.demo.DemoQueryApplication;
import com.evento.demo.memory.query.DemoProjector;
import com.evento.demo.telemetry.SentryTracingAgent;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
public class EventoConfiguration {

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	public EventoBundle eventoApplication(
			@Value("${evento.server.host}") String eventoServerHost,
			@Value("${evento.server.port}") Integer eventoServerPort,
			@Value("${evento.bundle.id}") String bundleId,
			@Value("${evento.bundle.version}") long bundleVersion,
			@Value("${evento.cluster.autoscaling.max.threads}") int maxThreads,
			@Value("${evento.cluster.autoscaling.max.overflow}") int maxOverflow,
			@Value("${evento.cluster.autoscaling.min.threads}") int minThreads,
			@Value("${evento.cluster.autoscaling.max.underflow}") int maxUnderflow,
			@Value("${spring.postgres.datasource.url}") String pgConnectionUrl,
			@Value("${spring.postgres.datasource.username}") String pgUsername,
			@Value("${spring.postgres.datasource.password}") String pgPassword,
			@Value("${spring.mysql.datasource.url}") String myConnectionUrl,
			@Value("${spring.mysql.datasource.username}") String myUsername,
			@Value("${spring.mysql.datasource.password}") String myPassword,
			BeanFactory factory,
			@Value("${sentry.dns}") String sentryDns
	) throws Exception {
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoQueryApplication.class.getPackage())

				.setInjector(factory::getBean)
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
						new ClusterNodeAddress(eventoServerHost, eventoServerPort)
				).setDisableDelayMillis(1000).setMaxDisableAttempts(3)
						.setMaxReconnectAttempts(30)
						.setReconnectDelayMillis(5000))
				.setTracingAgent(new SentryTracingAgent(bundleId, bundleVersion, sentryDns))
				.setAutoscalingProtocolBuilder((es) -> new ThreadCountAutoscalingProtocol(
						es,
						maxThreads,
						minThreads,
						maxOverflow,
						maxUnderflow, 60 * 1000))
				.setInjector(factory::getBean)
				//.setConsumerStateStoreBuilder(InMemoryConsumerStateStore::new)
    .setConsumerStateStoreBuilder((es, ps) ->{
						return PostgresConsumerStateStore.builder(es, ps, () -> {
                            try {
                                return DriverManager.getConnection(
                                        pgConnectionUrl, pgUsername, pgPassword);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }).build();
				})/*
				.setConsumerStateStoreBuilder((es, ps) ->{
					try {
						return MysqlConsumerStateStore.builder(es, ps, () -> {
								return DriverManager.getConnection(
										myConnectionUrl, myUsername, myPassword);
						}).build();
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}
				})*/
				.setComponentContexts(DemoProjector.class, Context.DEFAULT, "other")
				.setOnEventoStartedHook((eb) -> {
					/*
					while (true) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        eb.getProjectorManager().getProjectorEvenConsumers()
								.forEach(c -> {
									try {
										c.consumeDeadEventQueue();
									} catch (Exception e) {
										e.printStackTrace();
									}
								});
					}*/
				})
				.start();

	}
}
