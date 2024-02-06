package com.evento.demo.saga;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.demo.api.command.NotificationSendCommand;
import com.evento.demo.api.command.NotificationSendSilentCommand;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.event.DemoDeletedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.event.NotificationSentEvent;
import com.evento.demo.api.query.DemoRichViewFindByIdQuery;
import com.evento.demo.api.utils.Utils;

import java.util.concurrent.ExecutionException;

@Saga(version = 1)
public class DemoSaga {

	@SagaEventHandler(init = true, associationProperty = "demoId")
	public DemoSagaState on(DemoCreatedEvent event,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		DemoSagaState demoSagaState = new DemoSagaState();
		demoSagaState.setAssociation("demoId", event.getDemoId());
		demoSagaState.setLastValue(event.getValue());
		Utils.logMethodFlow(this, "on", event, "END");
		return demoSagaState;
	}

	@SagaEventHandler(associationProperty = "demoId")
	public DemoSagaState on(DemoUpdatedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) throws ExecutionException, InterruptedException {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		if (event.getValue() == 12)
		{
			var demo = queryGateway.query(new DemoRichViewFindByIdQuery(event.getDemoId())).get();
			System.out.println(jump(commandGateway, demo.getData().toString()));
		}
		demoSagaState.setLastValue(event.getValue());
		Utils.logMethodFlow(this, "on", event, "END");
		return demoSagaState;
	}

	@SagaEventHandler(associationProperty = "demoId")
	public DemoSagaState on(DemoDeletedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) throws ExecutionException, InterruptedException {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		System.out.println(this.getClass() + " - on(DemoDeletedEvent)");
		var demo = queryGateway.query(new DemoRichViewFindByIdQuery(event.getDemoId())).get();
		var resp = commandGateway.send(new NotificationSendSilentCommand("lol" + demo.getData().toString())).get();
		System.out.println(resp);
		demoSagaState.setEnded(true);
		Utils.logMethodFlow(this, "on", event, "END");
		return demoSagaState;
	}

	public NotificationSentEvent jump(CommandGateway commandGateway, String msg) {
		return sendNotification(commandGateway, msg);
	}

	public NotificationSentEvent sendNotification(CommandGateway commandGateway, String msg) {
		return commandGateway.sendAndWait(new NotificationSendCommand(msg));
	}


}
