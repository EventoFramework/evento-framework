package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.MessageGateway;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.NodeNotFoundException;
import org.jgroups.Event;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.blocks.*;

import java.util.concurrent.CompletableFuture;

public class JGroupsCommandGateway implements CommandGateway {

	private final RpcDispatcher server;
	private final String serverName;

	private ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public JGroupsCommandGateway(RpcDispatcher dispatcher, String serverName) {
		this.serverName = serverName;
		server = dispatcher;
	}

	public JGroupsCommandGateway(String messageChannelName, String nodeName, String serverName) throws Exception {
		JChannel jChannel = new JChannel(){
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
		jChannel.setName(nodeName);
		jChannel.connect(messageChannelName);
		this.serverName = serverName;
		server = new RpcDispatcher(jChannel, null){
			@Override
			public Object handle(Message req) throws Exception {
				System.out.println("MESSAGE: " + req);
				return super.handle(req);
			}
		};
	}

	@Override
	public <R> CompletableFuture<R> send(Command command) {
		try
		{

			return server.callRemoteMethodWithFuture(
					server.getChannel().getView().getMembers().stream()
							.filter(address -> serverName.equals(address.toString()))
							.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(serverName))),

					new MethodCall(
							MessageGateway.class.getMethod("handleInvocation", String.class),
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

		server.callRemoteMethod(
				server.getChannel().getView().getMembers().stream()
						.filter(address -> serverName.equals(address.toString()))
						.findAny().orElseThrow(() -> new NodeNotFoundException("Node %s not found".formatted(serverName))),

				new MethodCall(
						MessageGateway.class.getMethod("handleInvocation", String.class),
						payloadMapper.writeValueAsString(command instanceof DomainCommand ? new DomainCommandMessage((DomainCommand) command) : new ServiceCommandMessage((ServiceCommand) command))
				),
				RequestOptions.ASYNC());

	}
}
