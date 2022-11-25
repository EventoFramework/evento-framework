package org.eventrails.cli;

import org.junit.jupiter.api.Test;

class PublishBundleTest {

	@Test
	public void test() throws Exception {
		var serverUrl = "http://localhost:3000";
		PublishBundle.main(new String[]{ "../event-rails-demo/event-rails-demo-api", serverUrl});
		PublishBundle.main(new String[]{ "../event-rails-demo/event-rails-demo-command", serverUrl});
		PublishBundle.main(new String[]{ "../event-rails-demo/event-rails-demo-query", serverUrl});
		PublishBundle.main(new String[]{ "../event-rails-demo/event-rails-demo-saga", serverUrl});
		PublishBundle.main(new String[]{"../event-rails-demo/event-rails-demo-web-domain", serverUrl});
		PublishBundle.main(new String[]{ "../event-rails-demo/event-rails-demo-agent", serverUrl});
	}

	@Test
	public void versionUpdateTest() throws Exception {
		UpdateVersion.main(new String[]{ "../event-rails-demo/event-rails-demo-api/src"});
	}


}