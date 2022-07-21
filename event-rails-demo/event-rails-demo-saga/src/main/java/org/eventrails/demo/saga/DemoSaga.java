package org.eventrails.demo.saga;

import org.eventrails.demo.api.command.NotificationSendCommand;
import org.eventrails.demo.api.command.NotificationSendSilentCommand;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.demo.api.event.NotificationSentEvent;
import org.eventrails.demo.api.query.DemoFindByIdQuery;
import org.eventrails.demo.api.view.DemoView;
import org.eventrails.modeling.annotations.component.Saga;
import org.eventrails.modeling.annotations.handler.SagaEventHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.EventMessage;

import java.util.concurrent.ExecutionException;

@Saga
public class DemoSaga {

	@SagaEventHandler(init = true)
	public DemoSagaState on(DemoCreatedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage message) {
		demoSagaState.setAssociation("demoId", event.getDemoId());
		demoSagaState.setLastValue(event.getValue());
		return demoSagaState;
	}

	@SagaEventHandler()
	public DemoSagaState on(DemoUpdatedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage message) throws ExecutionException, InterruptedException {
		if (event.getValue() - demoSagaState.getLastValue() > 10)
		{
			var demo = queryGateway.query(new DemoFindByIdQuery(event.getDemoId()), DemoView.class).get();

			System.out.println(jump(commandGateway, demo.toString()));
		}

		demoSagaState.setLastValue(event.getValue());
		return demoSagaState;
	}

	public NotificationSentEvent jump(CommandGateway commandGateway, String msg) {
		return sendNotification(commandGateway, msg);
	}

	public NotificationSentEvent sendNotification(CommandGateway commandGateway, String msg){
		return commandGateway.sendAndWait(new NotificationSendCommand(msg));
	}

	@SagaEventHandler()
	public DemoSagaState on(DemoDeletedEvent event,
							DemoSagaState demoSagaState,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							EventMessage message) throws ExecutionException, InterruptedException {

		var demo = queryGateway.query(new DemoFindByIdQuery(event.getDemoId()), DemoView.class).get();
		commandGateway.send(new NotificationSendSilentCommand(demo.toString()));

		demoSagaState.setEnded(true);
		return demoSagaState;
	}
}
