package com.evento.demo.agent.agents;

import com.evento.application.performance.Track;
import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.demo.api.command.DemoCreateCommand;
import com.evento.demo.api.command.DemoDeleteCommand;
import com.evento.demo.api.command.DemoUpdateCommand;
import com.evento.demo.api.query.DemoViewFindAllQuery;
import com.evento.demo.api.query.DemoViewFindByIdQuery;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Invoker
public class DemoLifecycleAgent extends InvokerWrapper {


	@Track
	@InvocationHandler
	public void action(int i) throws ExecutionException, InterruptedException {
		var random = new Random();
		String id = UUID.randomUUID().toString();

		System.out.println("[" + i + "] - START");
		var resp = getCommandGateway().sendAndWait(new DemoCreateCommand(id, id, 0));
		System.out.println("[" + i + "] - DemoCreateCommand: " + resp);
		var r = 3;
		for (int j = 1; j < r; j++)
		{
			resp = getCommandGateway().sendAndWait(new DemoUpdateCommand(id, id, j));
			System.out.println("[" + i + "] - DemoUpdateCommand: " + resp);
		}
		getCommandGateway().send(new DemoUpdateCommand(id, id, 1)).thenAccept(o -> {
		}).exceptionally(e -> null);
		if (random.nextDouble(0, 1) < 0.7)
		{
			resp = getQueryGateway().query(new DemoViewFindByIdQuery(id)).exceptionally(e -> null).get();
			System.out.println("[" + i + "] - DemoViewFindByIdQuery: " + resp);
			if (resp == null)
			{
				Thread.sleep(3000);
				resp = getQueryGateway().query(new DemoViewFindByIdQuery(id)).exceptionally(e -> null).get();
				System.out.println("[" + i + "] - DemoViewFindByIdQuery(3000ms): " + resp);
			}
		}
		if (random.nextDouble(0, 1) < 0.3)
		{
			resp = getQueryGateway().query(new DemoViewFindAllQuery(10, 0)).get();
			System.out.println("[" + i + "] - DemoViewFindAllQuery: " + resp);
		}
		resp = getCommandGateway().sendAndWait(new DemoDeleteCommand(id));
		System.out.println("[" + i + "] - DemoDeleteCommand: " + resp);
		System.out.println("[" + i + "] - END");

		if (random.nextDouble(0, 1) < 0.2)
			throw new RuntimeException("Demo exception");

	}

}
