package org.eventrails.cli;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

	@Test
	public void test() throws IOException {
		Main.main(new String[]{"event-rails-bundle-demo-api","../event-rails-demo/event-rails-demo-api"});
		Main.main(new String[]{"event-rails-bundle-demo-command","../event-rails-demo/event-rails-demo-command"});
		Main.main(new String[]{"event-rails-bundle-demo-query","../event-rails-demo/event-rails-demo-query"});
		Main.main(new String[]{"event-rails-bundle-demo-saga","../event-rails-demo/event-rails-demo-saga"});

		Files.move(Path.of("../event-rails-demo/event-rails-demo-api/build/bundle-dist/event-rails-bundle-demo-api.bundle"),
				Path.of("../00_temp/event-rails-bundle-demo-api.bundle"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-command/build/bundle-dist/event-rails-bundle-demo-command.bundle"),
				Path.of("../00_temp/event-rails-bundle-demo-command.bundle"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-query/build/bundle-dist/event-rails-bundle-demo-query.bundle"),
				Path.of("../00_temp/event-rails-bundle-demo-query.bundle"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-saga/build/bundle-dist/event-rails-bundle-demo-saga.bundle"),
				Path.of("../00_temp/event-rails-bundle-demo-saga.bundle"));
	}
	@Test
	public void move() throws IOException {


	}



}