package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {

	@Test
	void main() {
		var app = EventRailsApplication.start(Application.class.getPackage().getName(),
				"event-rails-demo-saga",
				"event_rails_cluster", 8083,
				new String[0]);
		System.out.println(app);
	}
}