package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.server.ApplicationServer;
import org.eventrails.application.server.http.HttpApplicationServer;
import org.eventrails.modeling.messaging.invocation.*;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.ServiceEventMessage;
import org.eventrails.shared.ClusterMessage;
import org.eventrails.shared.ClusterMessageResponse;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.Response;

public class JGroupsApplicationServer implements Receiver, RequestHandler, ApplicationServer {

	private static final Logger logger = LogManager.getLogger(HttpApplicationServer.class);

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	private final MessageDispatcher server;
	private final EventRailsApplication application;


	public JGroupsApplicationServer(JChannel channel, EventRailsApplication application) throws Exception {
		server = new MessageDispatcher(channel, this);
		this.application = application;

	}

	public void start() throws Exception {
		server.start();
		logger.info("Server started");
	}

	public void stop() {
		server.stop();
	}

	@Override
	public void viewAccepted(View new_view) {
		Receiver.super.viewAccepted(new_view);
	}

	@Override
	public Object handle(Message msg) throws Exception {
		ClusterMessage clusterMessage = msg.getObject();
		if(clusterMessage.getAction().startsWith("/er/invoke/domain-command")){
			var parts = clusterMessage.getAction().split("/");
			if (parts.length != 5)
			{
				return new ClusterMessageResponse(404, null);
			}
			var handler = application.getAggregateMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				return new ClusterMessageResponse(404, null);
			}
			try
			{
				var hi = payloadMapper.readValue(clusterMessage.getBody(), AggregateCommandHandlerInvocation.class);
				var event = handler.invoke(hi.getCommandMessage(), hi.getAggregateState(), hi.getEventStream(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(new DomainEventMessage(event));
				return new ClusterMessageResponse(200, resp);
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				return new ClusterMessageResponse(500, resp);
			}
		}
		else if(clusterMessage.getAction().startsWith("/er/invoke/service-command")){
			var parts = clusterMessage.getAction().split("/");
			if (parts.length != 5)
			{
				return new ClusterMessageResponse(404, null);
			}
			var handler = application.getServiceMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				return new ClusterMessageResponse(404, null);
			}
			try
			{
				var hi = payloadMapper.readValue(clusterMessage.getBody(), ServiceCommandHandlerInvocation.class);
				var event = handler.invoke(hi.getCommandMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(new ServiceEventMessage(event));
				return new ClusterMessageResponse(200, resp);
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				return new ClusterMessageResponse(500, resp);
			}
		}
		else if(clusterMessage.getAction().startsWith("/er/invoke/query")){
			var parts = clusterMessage.getAction().split("/");
			if (parts.length != 5)
			{
				return new ClusterMessageResponse(404, null);
			}
			var handler = application.getProjectionMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				return new ClusterMessageResponse(404, null);
			}
			try
			{
				var hi = payloadMapper.readValue(clusterMessage.getBody(), QueryHandlerInvocation.class);
				var result = handler.invoke(hi.getQueryMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(result);
				return new ClusterMessageResponse(200, resp);
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				return new ClusterMessageResponse(500, resp);
			}
		}
		else if(clusterMessage.getAction().startsWith("/er/invoke/projector-event")){
			var parts = clusterMessage.getAction().split("/");
			if (parts.length != 5)
			{
				return new ClusterMessageResponse(404, null);
			}
			var handlers = application.getProjectorMessageHandlers().getOrDefault(parts[4], null);
			if (handlers == null)
			{
				return new ClusterMessageResponse(404, null);
			}

			var handler = handlers.getOrDefault(parts[5], null);
			if (handler == null)
			{
				return new ClusterMessageResponse(404, null);
			}

			try
			{
				var hi = payloadMapper.readValue(clusterMessage.getBody(), ProjectorEventHandlerInvocation.class);
				handler.invoke(hi.getEventMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				return new ClusterMessageResponse(200, null);
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				return new ClusterMessageResponse(500, resp);
			}
		}
		else if(clusterMessage.getAction().startsWith("/er/invoke/saga-event")){
			var parts = clusterMessage.getAction().split("/");
			if (parts.length != 5)
			{
				return new ClusterMessageResponse(404, null);
			}
			var handlers = application.getSagaMessageHandlers().getOrDefault(parts[4], null);
			if (handlers == null)
			{
				return new ClusterMessageResponse(404, null);
			}

			var handler = handlers.getOrDefault(parts[5], null);
			if (handler == null)
			{
				return new ClusterMessageResponse(404, null);
			}

			try
			{
				var hi = payloadMapper.readValue(clusterMessage.getBody(), SagaEventHandlerInvocation.class);
				var state = handler.invoke(hi.getEventMessage(),
						hi.getSagaState(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(state);
				return new ClusterMessageResponse(200, resp);
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				return new ClusterMessageResponse(500, resp);
			}
		}
		return new ClusterMessageResponse(404, null);
	}

	@Override
	public void handle(Message request, Response response) throws JsonProcessingException {
		try
		{
			var resp = this.handle(request);
			response.send(resp, false);
		} catch (Exception e)
		{
			e.printStackTrace();
			var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
			String resp = payloadMapper.writeValueAsString(ew);
			response.send( new ClusterMessageResponse(500, resp), true);
		}
	}
}
