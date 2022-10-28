package org.eventrails.server;

import org.eventrails.parser.java.JavaRanchApplicationParser;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.service.RanchApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;

@SpringBootTest
@ActiveProfiles("test")
class EventRailsServerApplicationTest {

	@Autowired
	RanchApplicationService ranchApplicationService;
	@Test
	void test() throws IOException {
		JavaRanchApplicationParser applicationParser = new JavaRanchApplicationParser();

		ranchApplicationService.unregister("event-rails-node-demo-api");
		ranchApplicationService.unregister("event-rails-node-demo-command");
		ranchApplicationService.unregister("event-rails-node-demo-query");
		ranchApplicationService.unregister("event-rails-node-demo-saga");

		var components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-api\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-node-demo-api",
				BucketType.LibraryOnly,
				null,
				null,
				components
		);
		
		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-node-demo-command",
				BucketType.LiveServer,
				null,
				null,
				components
		);

		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-node-demo-query",
				BucketType.LiveServer,
				null,
				null,
				components
		);

		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-node-demo-saga",
				BucketType.LiveServer,
				null,
				null,
				components
		);




	}
}