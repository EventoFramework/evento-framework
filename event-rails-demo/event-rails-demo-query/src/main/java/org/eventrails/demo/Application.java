package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;

public class Application {
	public static void main(String[] args) {
		EventRailsApplication.start(Application.class.getPackage().getName(),
				"event-rails-demo-query",
				"http://localhost:3000",
				9002,
				args);
	}
}
