package org.evento.demo.saga;

import org.evento.demo.api.command.NotificationSendCommand;
import org.evento.demo.api.command.NotificationSendSilentCommand;
import org.evento.demo.api.event.DemoCreatedEvent;
import org.evento.demo.api.event.DemoDeletedEvent;
import org.evento.demo.api.event.DemoUpdatedEvent;
import org.evento.demo.api.event.NotificationSentEvent;
import org.evento.demo.api.query.DemoViewFindByIdQuery;
import org.evento.common.modeling.annotations.component.Saga;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.demo.api.utils.Utils;

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
