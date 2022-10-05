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
		var components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-demo-command",
				BucketType.LiveServer,
				"http://localhost:9001",
				components
		);

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-demo-query",
				BucketType.LiveServer,
				"http://localhost:9002",
				components
		);

		components = applicationParser.parseDirectory(
				new File("D:\\Gabor\\DIDATTICA\\UNI\\TESI_MAGISTRALE\\event-rails\\event-rails-demo\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));


		ranchApplicationService.register(
				"event-rails-demo-saga",
				BucketType.LiveServer,
				"http://localhost:9003",
				components
		);




	}
}