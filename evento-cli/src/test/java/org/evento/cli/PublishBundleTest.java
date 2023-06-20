package org.evento.cli;

import org.junit.jupiter.api.Test;

class PublishBundleTest {

	@Test
	public void test() throws Exception {
		var serverUrl = "http://localhost:3000";
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-api", serverUrl});
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-command", serverUrl});
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-query", serverUrl});
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-saga", serverUrl});
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-web-domain", serverUrl});
		PublishBundle.main(new String[]{"../evento-demo/evento-demo-agent", serverUrl});
	}

	@Test
	public void versionUpdateTest() throws Exception {
		UpdateVersion.main(new String[]{"../evento-demo/evento-demo-agent/src"});
	}


}