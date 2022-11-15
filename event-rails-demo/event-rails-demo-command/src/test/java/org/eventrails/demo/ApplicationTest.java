package org.eventrails.demo;

import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.demo.api.command.*;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

class ApplicationTest {





	@Test
	public void testServiceCommandJGroup2() throws Exception {

		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");
		var resp = commandGateway.sendAndWait(new NotificationSendCommand("hola_cicos_4"));
		System.out.println(resp);
		System.out.println("end");
	}
	@Test
	public void testServiceCommandJGroup3() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create(
						"event-rails-demo-command-test",
						"event-rails-channel-message",
						"localhost").enabled(),
				"event-rails-server");
		String id = UUID.randomUUID().toString();
		Thread.sleep(1500);
		var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
		System.out.println(resp);
		resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, 1));
		System.out.println(resp);
		resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
		System.out.println(resp);
		System.out.println("end");
	}

	@Test
	public void generatePerformances() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost").enabled(),
				"event-rails-server");
		Thread.sleep(5000);
		var list = IntStream.range(0, 50).parallel().mapToObj(i -> {
			String id = UUID.randomUUID().toString();

			System.out.println("["+i+"] - START");
			var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
			System.out.println("["+i+"] - " + resp);
			for (int j = 1; j < new Random().nextInt(6, 11); j++)
			{
				resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, j));
				System.out.println("["+i+"] - " + resp);
			}
			resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
			System.out.println("["+i+"] - " + resp);
			System.out.println("["+i+"] - END");
			return resp;
		}).toList();
		list = IntStream.range(0, 10).parallel().mapToObj(i -> {
			String id = UUID.randomUUID().toString();

			System.out.println("["+i+"] - START");
			var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
			System.out.println("["+i+"] - " + resp);
			for (int j = 1; j < new Random().nextInt(6, 11); j++)
			{
				resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, j));
				System.out.println("["+i+"] - " + resp);
			}
			resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
			System.out.println("["+i+"] - " + resp);
			System.out.println("["+i+"] - END");
			return resp;
		}).toList();
		list = IntStream.range(0, 5).parallel().mapToObj(i -> {
			String id = UUID.randomUUID().toString();

			System.out.println("["+i+"] - START");
			var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
			System.out.println("["+i+"] - " + resp);
			for (int j = 1; j < new Random().nextInt(6, 11); j++)
			{
				resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, j));
				System.out.println("["+i+"] - " + resp);
			}
			resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
			System.out.println("["+i+"] - " + resp);
			System.out.println("["+i+"] - END");
			return resp;
		}).toList();

		System.out.println(list);
	}

	@Test
	public void testServiceCommandJGroup4() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");
		String id = UUID.randomUUID().toString();
		var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
		System.out.println(resp);
		System.out.println("end");
	}


	@Test
	public void benchmark() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");
		Thread.sleep(1000);
		var start = System.currentTimeMillis();
		for(int i = 0; i<300; i++)
		{
			String id = UUID.randomUUID().toString();
			commandGateway.sendAndWait(new DemoCreateCommand("test_" + id, id, 0));
		}
		var time = System.currentTimeMillis() - start;
		System.out.println(time+ " msc");
	}

	@Test
	public void benchmarkAggregate() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");

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
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");

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
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message", "localhost"),
				"event-rails-server");
		var start = System.currentTimeMillis();
		System.out.println(start);
		for(int i = 0; i<1200; i++)
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