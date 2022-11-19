package org.eventrails.cli;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

	@Test
	public void test() throws Exception {
		var serverUrl = "http://localhost:3000";
		Main.main(new String[]{ "../event-rails-demo/event-rails-demo-api", serverUrl});
		Main.main(new String[]{ "../event-rails-demo/event-rails-demo-command", serverUrl});
		Main.main(new String[]{ "../event-rails-demo/event-rails-demo-query", serverUrl});
		Main.main(new String[]{ "../event-rails-demo/event-rails-demo-saga", serverUrl});
		Main.main(new String[]{"../event-rails-demo/event-rails-demo-web-domain", serverUrl});
		Main.main(new String[]{ "../event-rails-demo/event-rails-demo-agent", serverUrl});
	}


}