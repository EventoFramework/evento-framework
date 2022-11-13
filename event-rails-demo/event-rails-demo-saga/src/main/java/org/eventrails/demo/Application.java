package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;

public class Application {
	public static void main(String[] args) throws Exception {

		String bundleName = "event-rails-bundle-demo-saga";
		String channelName = "event-rails-channel-message";
		String serverName = "event-rails-server";
		MessageBus messageBus = RabbitMqMessageBus.create(bundleName, channelName, "host.docker.internal");
		EventRailsApplication.start(Application.class.getPackage().getName(),
				bundleName,
				serverName,
				messageBus,
				new ThreadCountAutoscalingProtocol(
						bundleName,
						serverName,
						messageBus,
						16,
						4,
						5,
						5));
	}
}
