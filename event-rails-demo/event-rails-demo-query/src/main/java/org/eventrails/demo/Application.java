package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.bus.jgroups.JGroupsMessageBus;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.performance.ThreadCountAutoscalingProtocol;

public class Application {
	public static void main(String[] args) throws Exception {

		String bundleName = "event-rails-bundle-demo-query";
		String channelName = "event-rails-channel-message";
		String serverName = "event-rails-server";
		MessageBus messageBus = JGroupsMessageBus.create(bundleName, channelName);
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
