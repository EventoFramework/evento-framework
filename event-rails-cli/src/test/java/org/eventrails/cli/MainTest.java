package org.eventrails.cli;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

	@Test
	public void test() throws IOException {
		Main.main(new String[]{"event-rails-node-demo-api","../event-rails-demo/event-rails-demo-api"});
		Main.main(new String[]{"event-rails-node-demo-command","../event-rails-demo/event-rails-demo-command"});
		Main.main(new String[]{"event-rails-node-demo-query","../event-rails-demo/event-rails-demo-query"});
		Main.main(new String[]{"event-rails-node-demo-saga","../event-rails-demo/event-rails-demo-saga"});
	}

}