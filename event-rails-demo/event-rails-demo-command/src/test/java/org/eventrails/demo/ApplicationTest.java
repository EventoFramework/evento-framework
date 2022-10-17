package org.eventrails.demo;

import org.eventrails.application.server.jgroups.JGroupsCommandGateway;
import org.eventrails.demo.api.command.*;
import org.eventrails.modeling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class ApplicationTest {



	@Test
	public void testServiceCommandJGroup() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		commandGateway.sendAndWait(new NotificationSendSilentCommand("hola_cicos2"));
		System.out.println("end");
	}

	@Test
	public void testServiceCommandJGroup2() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		var resp = commandGateway.sendAndWait(new NotificationSendCommand("hola_cicos_4"));
		System.out.println(resp);
		System.out.println("end");
	}
	@Test
	public void testServiceCommandJGroup3() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		String id = UUID.randomUUID().toString();
		var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
		System.out.println(resp);
		resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, 1));
		System.out.println(resp);
		resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
		System.out.println(resp);
		System.out.println("end");
	}

	@Test
	public void testServiceCommandJGroup4() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		String id = UUID.randomUUID().toString();
		var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
		System.out.println(resp);
		System.out.println("end");
	}


	@Test
	public void benchmark() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		var start = System.currentTimeMillis();
		for(int i = 0; i<1000; i++)
		{
			String id = UUID.randomUUID().toString();
			commandGateway.sendAndWait(new DemoCreateCommand("test_" + id, id, 0));
		}
		var time = System.currentTimeMillis() - start;
		System.out.println(time+ " msc");
	}

	@Test
	public void benchmarkAggregate() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");

		String id = UUID.randomUUID().toString();
		var start = System.currentTimeMillis();
		commandGateway.sendAndWait(new DemoCreateCommand("test_" + id, id, -1));
		for(int i = 0; i<198; i++)
		{
			commandGateway.sendAndWait(new DemoUpdateCommand("test_" + id, id, i));
		}
		commandGateway.sendAndWait(new DemoDeleteCommand("test_" + id));
		var time = System.currentTimeMillis() - start;
		System.out.println(time+ " msc");
	}


	@Test
	public void benchmarkAggregateAsync() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");

		String id = UUID.randomUUID().toString();
		var start = System.currentTimeMillis();
		commandGateway.sendAndWait(new DemoCreateCommand("test_" + id, id, -1));
		for(int i = 0; i<198; i++)
		{
			commandGateway.send(new DemoUpdateCommand("test_" + id, id, i));
		}
		var time = System.currentTimeMillis() - start;
		System.out.println(time+ " msc");
	}

	@Test
	public void benchmarkAsync() throws Exception {
		CommandGateway commandGateway = new JGroupsCommandGateway(
				"event-rails-channel-message",
				"event-rails-demo-command-test",
				"event-rails-node-server");
		var start = System.currentTimeMillis();
		System.out.println(start);
		for(int i = 0; i<3000; i++)
		{
			String id = UUID.randomUUID().toString();
			commandGateway.send(new DemoCreateCommand("test_" + id, id, 0));
		}
		var end = System.currentTimeMillis();
		System.out.println(end);
		var time = end - start;
		System.out.println(time+ " msc");
	}
}