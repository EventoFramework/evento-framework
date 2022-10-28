package org.eventrails.server;

import org.eventrails.parser.java.JavaBundleParser;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.service.BundleService;
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
	BundleService bundleService;
	@Test
	void test() throws IOException {
		JavaBundleParser applicationParser = new JavaBundleParser();

		bundleService.unregister("event-rails-bundle-demo-api");
		bundleService.unregister("event-rails-bundle-demo-command");
		bundleService.unregister("event-rails-bundle-demo-query");
		bundleService.unregister("event-rails-bundle-demo-saga");

		var components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-api\\src\\main\\java\\org\\eventrails\\demo"));


		bundleService.register(
				"event-rails-bundle-demo-api",
				BucketType.LibraryOnly,
				null,
				null,
				components
		);
		
		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-command\\src\\main\\java\\org\\eventrails\\demo"));


		bundleService.register(
				"event-rails-bundle-demo-command",
				BucketType.LiveServer,
				null,
				null,
				components
		);

		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-query\\src\\main\\java\\org\\eventrails\\demo"));


		bundleService.register(
				"event-rails-bundle-demo-query",
				BucketType.LiveServer,
				null,
				null,
				components
		);

		components = applicationParser.parseDirectory(
				new File(System.getProperty("user.dir") + "\\..\\event-rails-demo\\event-rails-demo-saga\\src\\main\\java\\org\\eventrails\\demo"));


		bundleService.register(
				"event-rails-bundle-demo-saga",
				BucketType.LiveServer,
				null,
				null,
				components
		);




	}
}