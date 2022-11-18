package org.eventrails.demo;

import ch.qos.logback.core.encoder.EchoEncoder;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.demo.api.command.*;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

class DemoCommandApplicationTest {



	@Test
	public void testServiceCommandJGroup2() throws Exception {

		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test", "event-rails-channel-message",
						"localhost").enabled(),
				"event-rails-server");
		Thread.sleep(1500);
		var resp = commandGateway.sendAndWait(new NotificationSendSilentCommand("hola_cicos_4"));
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
		Thread.sleep(3000);
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
	public void generatePerformances() throws Exception {
		CommandGateway commandGateway = new CommandGateway(
				RabbitMqMessageBus.create("event-rails-demo-command-test",
						"event-rails-channel-message", "localhost").enabled(),
				"event-rails-server");
		Thread.sleep(1500);
		record Report(
				String uid,
				long createTime,
				long meanUpdateTime,
				long deleteTime, boolean success){
			@Override
			public String toString() {
				return Report.this.uid + "\t" + Report.this.createTime + "\t" + Report.this.meanUpdateTime + "\t" + Report.this.deleteTime + "\t" + Report.this.success;
			}
		}

		IntFunction<Report> spawner = (int i) -> {
			String id = UUID.randomUUID().toString();

			System.out.println("["+i+"] - START");
			var createdAtStart = Instant.now().toEpochMilli();
			try {
				var resp = commandGateway.sendAndWait(new DemoCreateCommand(id, id, 0));
				var createTime = Instant.now().toEpochMilli() - createdAtStart;
				System.out.println("[" + i + "] - " + resp);
				try {
					var updateAtStart = Instant.now().toEpochMilli();
					var r = new Random().nextInt(6, 11);
					for (int j = 1; j < r; j++) {
						resp = commandGateway.sendAndWait(new DemoUpdateCommand(id, id, j));
						System.out.println("[" + i + "] - " + resp);
					}
					var updateMeanTime = (Instant.now().toEpochMilli() - updateAtStart) / r;
					try {
						var deleteAtStart = Instant.now().toEpochMilli();
						resp = commandGateway.sendAndWait(new DemoDeleteCommand(id));
						var deleteTime = (Instant.now().toEpochMilli() - deleteAtStart);
						System.out.println("[" + i + "] - " + resp);
						System.out.println("[" + i + "] - END");
						return new Report(id, createTime, updateMeanTime, deleteTime, true);
					}catch (Exception e){
						return new Report(id, createTime, updateMeanTime, 0, false);
					}
				}catch (Exception e){
					return new Report(id, createTime, 0, 0, false);
				}
			}catch (Exception e){
				return new Report(id, 0, 0, 0, false);
			}
		};
		var listStart = IntStream.range(0, 10).parallel().mapToObj(spawner).toList();
		var listMiddle = IntStream.range(0, 30).parallel().mapToObj(spawner).toList();
		var listEnd = IntStream.range(0, 10).parallel().mapToObj(spawner).toList();
		System.out.println("listStart (10) MeanCreateTime: "+listStart.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listStart (10) MeanMeanUpdateTime: "+listStart.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listStart (10) MeanDeleteTime: "+listStart.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));
		System.out.println("listMiddle (30) MeanCreateTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listMiddle (30) MeanMeanUpdateTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listMiddle (30) MeanDeleteTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));
		System.out.println("listEnd (10) MeanCreateTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listEnd (10) MeanMeanUpdateTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listEnd (10) MeanDeleteTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));
		listStart.stream().filter(r -> !r.success).forEach(System.out::println);
		listMiddle.stream().filter(r -> !r.success).forEach(System.out::println);
		listEnd.stream().filter(r -> !r.success).forEach(System.out::println);

		/*
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
*/
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