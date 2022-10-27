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
		Main.main(new String[]{"event-rails-node-demo-api","../event-rails-demo/event-rails-demo-api"});
		Main.main(new String[]{"event-rails-node-demo-command","../event-rails-demo/event-rails-demo-command"});
		Main.main(new String[]{"event-rails-node-demo-query","../event-rails-demo/event-rails-demo-query"});
		Main.main(new String[]{"event-rails-node-demo-saga","../event-rails-demo/event-rails-demo-saga"});
	}
	@Test
	public void move() throws IOException {

		Files.move(Path.of("../event-rails-demo/event-rails-demo-api/build/ranch-dist/event-rails-node-demo-api.ranch"),
				Path.of("../00_temp/event-rails-node-demo-api.ranch"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-command/build/ranch-dist/event-rails-node-demo-command.ranch"),
				Path.of("../00_temp/event-rails-node-demo-command.ranch"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-query/build/ranch-dist/event-rails-node-demo-query.ranch"),
				Path.of("../00_temp/event-rails-node-demo-query.ranch"));
		Files.move(Path.of("../event-rails-demo/event-rails-demo-saga/build/ranch-dist/event-rails-node-demo-saga.ranch"),
				Path.of("../00_temp/event-rails-node-demo-saga.ranch"));
	}



}