package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;

public class Application {

	public static void main(String[] args) {
		EventRailsApplication.start(Application.class.getPackage().getName(),
				"event-rails-demo-command",
				"http://localhost:3000",
				9001,
				args);
	}
}
