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
		Main.main(new String[]{"event-rails-bundle-demo-api", "../event-rails-demo/event-rails-demo-api", "http://localhost:3000"});
		//Main.main(new String[]{"event-rails-bundle-demo-command", "../event-rails-demo/event-rails-demo-command", "http://localhost:3000"});
		//Main.main(new String[]{"event-rails-bundle-demo-query", "../event-rails-demo/event-rails-demo-query", "http://localhost:3000"});
		//Main.main(new String[]{"event-rails-bundle-demo-saga", "../event-rails-demo/event-rails-demo-saga", "http://localhost:3000"});
		//Main.main(new String[]{"event-rails-bundle-demo-web-domain", "../event-rails-demo/event-rails-demo-web-domain", "http://localhost:3000"});
	}


}