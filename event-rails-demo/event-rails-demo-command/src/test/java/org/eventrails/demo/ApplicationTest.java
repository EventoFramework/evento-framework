package org.eventrails.demo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.server.jgroups.JGroupsCommandGateway;
import org.eventrails.demo.api.command.*;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.command.aggregate.DemoAggregateState;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.invocation.AggregateCommandHandlerInvocation;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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
}