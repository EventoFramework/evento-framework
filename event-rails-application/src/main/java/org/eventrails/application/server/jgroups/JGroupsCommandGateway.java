package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.NodeNotFoundException;
import org.jgroups.JChannel;
import org.jgroups.ObjectMessage;
import org.jgroups.blocks.*;

import java.util.concurrent.CompletableFuture;

public class JGroupsCommandGateway implements CommandGateway {

	private final MessageDispatcher server;
	private final String serverName;

	private ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public JGroupsCommandGateway(JChannel jChannel, String serverName) {
		this.serverName = serverName;
		server = new MessageDispatcher(jChannel);
	}

	public JGroupsCommandGateway(String messageChannelName, String nodeName, String serverName) throws Exception {
		JChannel jChannel = new JChannel();
		jChannel.setName(nodeName);
		jChannel.connect(messageChannelName);
		this.serverName = serverName;
		server = new MessageDispatcher(jChannel);
	}

	@Override
	public <R> CompletableFuture<R> send(Command command) {
		try
		{

			return server.sendMessageWithFuture(
					new ObjectMessage(

							server.getChannel().getView().getMembers().stream()
							.filter(address -> serverName.equals(address.toString()))
							.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(serverName))),

							payloadMapper.writeValueAsString(command instanceof DomainCommand ? new DomainCommandMessage((DomainCommand) command) : new ServiceCommandMessage((ServiceCommand) command))

					),
					RequestOptions.SYNC());
		} catch (Exception e)
		{
			var c = new CompletableFuture<R>();
			c.completeExceptionally(e);
			return c;
		}
	}

	public void sendAsync(Command command) throws Exception {

			server.sendMessageWithFuture(
					new ObjectMessage(

							server.getChannel().getView().getMembers().stream()
									.filter(address -> serverName.equals(address.toString()))
									.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(serverName))),

							payloadMapper.writeValueAsString(command instanceof DomainCommand ? new DomainCommandMessage((DomainCommand) command) : new ServiceCommandMessage((ServiceCommand) command))

					),
					RequestOptions.ASYNC());

	}
}
