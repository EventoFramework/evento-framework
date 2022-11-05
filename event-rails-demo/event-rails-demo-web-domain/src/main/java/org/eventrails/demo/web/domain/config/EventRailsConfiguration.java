package org.eventrails.demo.web.domain.config;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.demo.web.domain.Application;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventRailsConfiguration {

	@Bean
	public EventRailsApplication eventRailsApplication(
			@Value("${eventrails.cluster.message.channel.name}") String channelName,
			@Value("${eventrails.cluster.node.server.name}") String serverName,
			@Value("${eventrails.cluster.bundle.name}") String bundleName
			){
		return EventRailsApplication.start(Application.class.getPackage().getName(),
				bundleName,
				channelName,
				serverName,
				new String[0]);
	}

	@Bean
	public CommandGateway commandGateway(EventRailsApplication eventRailsApplication){
		return eventRailsApplication.getCommandGateway();
	}

	@Bean
	public QueryGateway queryGateway(EventRailsApplication eventRailsApplication){
		return eventRailsApplication.getQueryGateway();
	}
}
