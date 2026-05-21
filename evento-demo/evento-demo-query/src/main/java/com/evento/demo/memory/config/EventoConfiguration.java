package com.evento.demo.memory.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.v2.ConsumerEngineConfig;
import com.evento.application.manager.LogTracesMessageHandlerInterceptor;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.utils.Context;
import com.evento.common.utils.ProjectorStatus;
import com.evento.demo.DemoQueryApplication;
import com.evento.demo.api.event.UtilEvent;
import com.evento.demo.api.view.enums.FailStage;
import com.evento.demo.memory.query.DemoProjector;
import com.evento.demo.telemetry.SentryTracingAgent;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class EventoConfiguration {

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	public EventoBundle eventoApplication(
			@Value("${evento.server.host}") String eventoServerHost,
			@Value("${evento.server.port}") Integer eventoServerPort,
			@Value("${evento.bundle.id}") String bundleId,
			@Value("${evento.bundle.version}") long bundleVersion,
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
				))
				.setTracingAgent(new SentryTracingAgent(bundleId, bundleVersion, sentryDns))
				.setInjector(factory::getBean)
				.setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
				.setComponentContexts(DemoProjector.class, Context.DEFAULT, "other")
				.setOnEventoStartedHook((eb) -> {
				})
                .setMessageHandlerInterceptor(new LogTracesMessageHandlerInterceptor(){
                    @Override
                    public void beforeProjectorEventHandling(Object projector, PublishedEvent publishedEvent, CommandGateway commandGateway, QueryGateway queryGateway, ProjectorStatus projectorStatus) {
                       if(publishedEvent.getEventMessage().getPayload() instanceof UtilEvent ue){
                           if(ue.getFailStage() == FailStage.BEFORE_HANDLING){
                               throw new RuntimeException("Force Fail Before Handling");
                           }
                       }
                    }

                    @Override
                    public void afterProjectorEventHandling(Object projector, PublishedEvent publishedEvent, CommandGateway commandGateway, QueryGateway queryGateway, ProjectorStatus projectorStatus) {
                        if(publishedEvent.getEventMessage().getPayload() instanceof UtilEvent ue){
                            if(ue.getFailStage() == FailStage.AFTER_HANDLING){
                                throw new RuntimeException("Force Fail After Handling");
                            }
                        }
                        super.afterProjectorEventHandling(projector, publishedEvent, commandGateway, queryGateway, projectorStatus);
                    }

                    @Override
                    public Throwable onExceptionProjectorEventHandling(Object projector, PublishedEvent publishedEvent, CommandGateway commandGateway, QueryGateway queryGateway, ProjectorStatus projectorStatus, Throwable t) {
                        if(publishedEvent.getEventMessage().getPayload() instanceof UtilEvent ue){
                            if(ue.getFailStage() == FailStage.AFTER_HANDLING_EXCEPTION){
                                throw new RuntimeException("Force Fail After Handling Exception");
                            }
                        }
                        return super.onExceptionProjectorEventHandling(projector, publishedEvent, commandGateway, queryGateway, projectorStatus, t);
                    }
                })
				.start();

	}
}
