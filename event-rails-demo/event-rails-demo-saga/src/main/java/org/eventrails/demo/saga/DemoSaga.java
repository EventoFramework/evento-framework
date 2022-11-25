package org.eventrails.demo.saga;

import org.eventrails.demo.api.command.NotificationSendCommand;
import org.eventrails.demo.api.command.NotificationSendSilentCommand;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.demo.api.event.NotificationSentEvent;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.eventrails.common.modeling.annotations.component.Saga;
import org.eventrails.common.modeling.annotations.handler.SagaEventHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.EventMessage;
import org.eventrails.demo.api.utils.Utils;

import java.util.concurrent.ExecutionException;

@Saga(version = 1)
public class DemoSaga {

	@SagaEventHandler(init = true, associationProperty = "demoId")
	public DemoSagaState on(DemoCreatedEvent event,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) {
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		DemoSagaState demoSagaState = new DemoSagaState();
		demoSagaState.setAssociation("demoId", event.getDemoId());
		demoSagaState.setLastValue(event.getValue());
		Utils.logMethodFlow(this,"on", event, "END");
		return demoSagaState;
	}

	@SagaEventHandler(associationProperty = "demoId")
	public DemoSagaState on(DemoUpdatedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) throws ExecutionException, InterruptedException {
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		if (event.getValue() - demoSagaState.getLastValue() > 10)
		{
			var demo = queryGateway.query(new DemoViewFindByIdQuery(event.getDemoId())).get();
			System.out.println(jump(commandGateway, demo.toString()));
		}
		demoSagaState.setLastValue(event.getValue());
		Utils.logMethodFlow(this,"on", event, "END");
		return demoSagaState;
	}

	@SagaEventHandler(associationProperty = "demoId")
	public DemoSagaState on(DemoDeletedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage<?> message) throws ExecutionException, InterruptedException {
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		System.out.println(this.getClass() + " - on(DemoDeletedEvent)");
		try {
			var demo = queryGateway.query(new DemoViewFindByIdQuery(event.getDemoId())).get();
			var resp = commandGateway.send(new NotificationSendSilentCommand("lol" + demo.getData().getDemoId())).get();
			System.out.println(resp);
		}catch (Exception e) {
			e.printStackTrace();
		}
		demoSagaState.setEnded(true);
		Utils.logMethodFlow(this,"on", event, "END");
		return demoSagaState;
	}
	public NotificationSentEvent jump(CommandGateway commandGateway, String msg) {
		return sendNotification(commandGateway, msg);
	}

	public NotificationSentEvent sendNotification(CommandGateway commandGateway, String msg){
		return commandGateway.sendAndWait(new NotificationSendCommand(msg));
	}


}
