package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;

public class Application {
	public static void main(String[] args) {

		EventRailsApplication.start(Application.class.getPackage().getName(),
				"event-rails-bundle-demo-saga",
				"event-rails-channel-message",
				"event-rails-server",
				args);
	}
}
