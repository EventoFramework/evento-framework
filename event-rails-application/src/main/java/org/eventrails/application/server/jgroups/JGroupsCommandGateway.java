package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.modeling.messaging.utils.RoundRobinAddressPicker;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.shared.messaging.JGroupsMessageBus;
import org.jgroups.JChannel;

import java.util.concurrent.CompletableFuture;

public class JGroupsCommandGateway implements CommandGateway {

	private final MessageBus messageBus;
	private final String serverName;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	public JGroupsCommandGateway(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
	}

	public JGroupsCommandGateway(String messageChannelName, String nodeName, String serverName) throws Exception {
		JChannel jChannel = new JChannel();

		this.messageBus = new JGroupsMessageBus(jChannel);
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);

		jChannel.setName(nodeName);
		jChannel.connect(messageChannelName);
		this.serverName = serverName;

		messageBus.enableBus();

	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command) {
		var future = new CompletableFuture<R>();
		try
		{
			messageBus.cast(
					roundRobinAddressPicker.pickNodeAddress(serverName),
					command instanceof DomainCommand ?
							new DomainCommandMessage((DomainCommand) command) :
							new ServiceCommandMessage((ServiceCommand) command),
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
