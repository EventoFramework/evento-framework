package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.ServerHandleInvocationMessage;
import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Message;

import java.util.concurrent.CompletableFuture;

public class JGroupsCommandGateway implements CommandGateway {

	private final MessageBus messageBus;
	private final String serverName;

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public JGroupsCommandGateway(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
	}

	public JGroupsCommandGateway(String messageChannelName, String nodeName, String serverName) throws Exception {
		JChannel jChannel = new JChannel() {
			@Override
			public Object up(Message msg) {
				System.out.println("UP MSG - " + msg);
				return super.up(msg);
			}

			@Override
			public Object up(Event evt) {
				System.out.println("UP EVT - " + evt);
				return super.up(evt);
			}

			@Override
			public Object down(Event evt) {
				System.out.println("DOWN EVT - " + evt);
				return super.down(evt);
			}

			@Override
			public Object down(Message evt) {
				System.out.println("DOWN MSG - " + evt);
				return super.down(evt);
			}
		};

		this.messageBus = new JGroupsMessageBus(jChannel,
				message -> {
				},
				(request, response) -> {
				});

		jChannel.setName(nodeName);
		jChannel.connect(messageChannelName);
		this.serverName = serverName;

	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command) {
		var future = new CompletableFuture<R>();
		try
		{
			messageBus.cast(
					messageBus.findNodeAddress(serverName),
					new ServerHandleInvocationMessage(payloadMapper.writeValueAsString(command instanceof DomainCommand ? new DomainCommandMessage((DomainCommand) command) : new ServiceCommandMessage((ServiceCommand) command))),
					response -> {
						try{
							future.complete((R) response);
						}catch (Exception e){
							future.completeExceptionally(e);
						}
					},
					error -> {
						future.completeExceptionally(error.toThrowable());
					}
			);
			return future;
		} catch (Exception e)
		{
			future.completeExceptionally(e);
			return future;
		}
	}

}
