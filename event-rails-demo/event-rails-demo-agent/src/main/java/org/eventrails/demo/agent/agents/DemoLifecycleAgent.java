package org.eventrails.demo.agent.agents;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.annotations.component.Invoker;
import org.eventrails.common.modeling.annotations.handler.InvocationHandler;
import org.eventrails.demo.api.command.DemoCreateCommand;
import org.eventrails.demo.api.command.DemoDeleteCommand;
import org.eventrails.demo.api.command.DemoUpdateCommand;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

@Invoker
public class DemoLifecycleAgent {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	public DemoLifecycleAgent(EventRailsApplication eventRailsApplication) {
		commandGateway = eventRailsApplication.getCommandGateway();
		queryGateway = eventRailsApplication.getQueryGateway();
	}


	@InvocationHandler
	public Report action(int i){
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
	}

}