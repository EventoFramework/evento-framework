package org.eventrails.demo;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.server.http.HttpQueryGateway;
import org.eventrails.demo.api.query.DemoRichViewFindAllQuery;
import org.eventrails.demo.api.query.DemoRichViewFindByIdQuery;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

class ApplicationTest {

	@Test
	void main() {
		var app = EventRailsApplication.start(Application.class.getPackage().getName(),
				"event-rails-demo-query",
				"event_rails_cluster", 8082,
				new String[0]);
		System.out.println(app);
	}

	@Test
	public void serverTestRefl(){
		System.out.println(new DemoRichViewFindAllQuery(10,0).getResponseType());
		System.out.println(new DemoRichViewFindByIdQuery("1").getResponseType());
	}
	@Test
	public void serverTest() throws ExecutionException, InterruptedException {
		HttpQueryGateway queryGateway = new HttpQueryGateway("http://localhost:3000");
		var resp = queryGateway.query(new DemoViewFindByIdQuery("demo_1")).get();
		System.out.println(resp.getData());
	}

	@Test
	public void serverTestMulti() throws ExecutionException, InterruptedException {
		HttpQueryGateway queryGateway = new HttpQueryGateway("http://localhost:3000");
		var resp = queryGateway.query(new DemoRichViewFindAllQuery(0,10)).get();
		System.out.println(resp.getData());
	}
}