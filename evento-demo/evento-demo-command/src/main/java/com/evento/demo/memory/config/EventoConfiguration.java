package com.evento.demo.memory.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.manager.LogTracesMessageHandlerInterceptor;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.performance.ThreadCountAutoscalingProtocol;
import com.evento.demo.DemoCommandApplication;
import com.evento.demo.api.command.UtilFailCommand;
import com.evento.demo.api.error.InvalidCommandException;
import com.evento.demo.api.view.enums.FailStage;
import com.evento.demo.telemetry.SentryTracingAgent;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
			@Value("${sentry.dns}") String sentryDns,
			BeanFactory factory
	) throws Exception {
		return EventoBundle.Builder.builder()
				.setBasePackage(DemoCommandApplication.class.getPackage())
				.setBundleId(bundleId)
				.setBundleVersion(bundleVersion)
				.setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
						new ClusterNodeAddress(eventoServerHost,eventoServerPort)
				).setDisableDelayMillis(1000).setMaxDisableAttempts(3)
						.setMaxReconnectAttempts(30)
						.setReconnectDelayMillis(5000)
								.setRetryDelayMillis(3000)
								.setMaxRetryAttempts(5)
				)
				.setTracingAgent(new SentryTracingAgent(bundleId, bundleVersion, sentryDns))
				.setAutoscalingProtocolBuilder((es) -> new ThreadCountAutoscalingProtocol(
						es,
						maxThreads,
						minThreads,
						maxOverflow,
						maxUnderflow, 60 * 1000))
				.setInjector(factory::getBean)
                .setMessageHandlerInterceptor(new LogTracesMessageHandlerInterceptor(){
                    @Override
                    public void beforeServiceCommandHandling(Object service, CommandMessage<?> commandMessage, CommandGateway commandGateway, QueryGateway queryGateway) {
                        if(commandMessage.getPayload() instanceof UtilFailCommand fc){
                            if(fc.getFailStage() == FailStage.BEFORE_HANDLING){
                                throw new InvalidCommandException("Failed before handling");
                            }
                        }
                    }

                    @Override
                    public ServiceEvent afterServiceCommandHandling(Object service, CommandMessage<?> commandMessage, CommandGateway commandGateway, QueryGateway queryGateway, ServiceEvent event) {
                        if(commandMessage.getPayload() instanceof UtilFailCommand fc){
                            if(fc.getFailStage() == FailStage.AFTER_HANDLING){
                                throw new InvalidCommandException("Failed after handling");
                            }
                        }
                        return super.afterServiceCommandHandling(service, commandMessage, commandGateway, queryGateway, event);
                    }

                    @Override
                    public Throwable onExceptionServiceCommandHandling(Object service, CommandMessage<?> commandMessage, CommandGateway commandGateway, QueryGateway queryGateway, Throwable throwable) {
                        if(commandMessage.getPayload() instanceof UtilFailCommand fc){
                            if(fc.getFailStage() == FailStage.AFTER_HANDLING_EXCEPTION){
                                throw new InvalidCommandException("Failed after handling exception");
                            }
                        }

                        return super.onExceptionServiceCommandHandling(service, commandMessage, commandGateway, queryGateway, throwable);
                    }
                })
				.start();
	}
}
