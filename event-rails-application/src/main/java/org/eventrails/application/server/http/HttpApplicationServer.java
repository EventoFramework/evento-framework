package org.eventrails.application.server.http;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.server.ApplicationServer;
import org.eventrails.modeling.messaging.invocation.*;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

@Deprecated
public class HttpApplicationServer implements ApplicationServer {

	private static final Logger logger = LogManager.getLogger(HttpApplicationServer.class);

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();
	private final ObjectMapper responseMapper = ObjectMapperUtils.getResultObjectMapper();
	private final HttpServer server;

	public HttpApplicationServer(int serverPort, EventRailsApplication application) throws IOException {

		this.server = HttpServer.create(new InetSocketAddress(serverPort), 0);
		server.createContext("/er/info", ex -> {
			String respText = responseMapper.writeValueAsString(application.getAppInfo());
			ex.getResponseHeaders().set("Content-Type", "application/json");
			ex.sendResponseHeaders(200, respText.getBytes().length);
			OutputStream output = ex.getResponseBody();
			output.write(respText.getBytes());
			output.flush();
			ex.close();
		});
		server.createContext("/er/invoke/domain-command", ex -> {

			if (!ex.getRequestMethod().equals("POST"))
			{
				ex.sendResponseHeaders(405, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var parts = ex.getRequestURI().getPath().split("/");
			if (parts.length != 5)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var handler = application.getAggregateMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			try
			{
				var hi = payloadMapper.readValue(ex.getRequestBody(), AggregateCommandHandlerInvocation.class);
				var event = handler.invoke(hi.getCommandMessage(), hi.getAggregateState(), hi.getEventStream(),
						application.getCommandGateway(),
						application.getQueryGateway()
						);
				String resp = payloadMapper.writeValueAsString(new DomainEventMessage(event));
				ex.getResponseHeaders().set("Content-Type", "application/json");
				ex.sendResponseHeaders(200, resp.getBytes().length);
				OutputStream output = ex.getResponseBody();
				output.write(resp.getBytes());
				output.flush();
			}catch (Throwable e){
				e.printStackTrace();
				var ew = new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace());
				String resp = payloadMapper.writeValueAsString(ew);
				ex.getResponseHeaders().set("Content-Type", "application/json");
				ex.sendResponseHeaders(500, resp.getBytes().length);
				OutputStream output = ex.getResponseBody();
				output.write(resp.getBytes());
				output.flush();
			}
			ex.close();
		});
		server.createContext("/er/invoke/service-command", ex -> {

			if (!ex.getRequestMethod().equals("POST"))
			{
				ex.sendResponseHeaders(405, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var parts = ex.getRequestURI().getPath().split("/");
			if (parts.length != 5)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var handler = application.getServiceMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			try
			{
				var hi = payloadMapper.readValue(ex.getRequestBody(), ServiceCommandHandlerInvocation.class);
				var event = handler.invoke(hi.getCommandMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(event);
				ex.getResponseHeaders().set("Content-Type", "application/json");
				ex.sendResponseHeaders(200, resp.getBytes().length);
				OutputStream output = ex.getResponseBody();
				output.write(resp.getBytes());
				output.flush();
			}catch (Exception e){
				e.printStackTrace();
				ex.sendResponseHeaders(500, -1);
			}
			ex.close();
		});
		server.createContext("/er/invoke/query", ex -> {

			if (!ex.getRequestMethod().equals("POST"))
			{
				ex.sendResponseHeaders(405, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var parts = ex.getRequestURI().getPath().split("/");
			if (parts.length != 5)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var handler = application.getProjectionMessageHandlers().getOrDefault(parts[4], null);
			if (handler == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			try
			{
				var hi = payloadMapper.readValue(ex.getRequestBody(), QueryHandlerInvocation.class);
				var event = handler.invoke(hi.getQueryMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(event);
				ex.getResponseHeaders().set("Content-Type", "application/json");
				ex.sendResponseHeaders(200, resp.getBytes().length);
				OutputStream output = ex.getResponseBody();
				output.write(resp.getBytes());
				output.flush();
			}catch (Exception e){
				e.printStackTrace();
				ex.sendResponseHeaders(500, -1);
			}
			ex.close();
		});
		server.createContext("/er/invoke/projector-event", ex -> {

			if (!ex.getRequestMethod().equals("POST"))
			{
				ex.sendResponseHeaders(405, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var parts = ex.getRequestURI().getPath().split("/");
			if (parts.length != 6)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var handlers = application.getProjectorMessageHandlers().getOrDefault(parts[4], null);
			if (handlers == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			var handler = handlers.getOrDefault(parts[5], null);
			if (handler == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			try
			{
				var hi = payloadMapper.readValue(ex.getRequestBody(), ProjectorEventHandlerInvocation.class);
				handler.invoke(hi.getEventMessage(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				ex.sendResponseHeaders(200,-1);
			}catch (Exception e){
				e.printStackTrace();
				ex.sendResponseHeaders(500, -1);
			}
			ex.close();
		});
		server.createContext("/er/invoke/saga-event", ex -> {

			if (!ex.getRequestMethod().equals("POST"))
			{
				ex.sendResponseHeaders(405, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var parts = ex.getRequestURI().getPath().split("/");
			if (parts.length != 6)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}
			var handlers = application.getSagaMessageHandlers().getOrDefault(parts[4], null);
			if (handlers == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			var handler = handlers.getOrDefault(parts[5], null);
			if (handler == null)
			{
				ex.sendResponseHeaders(404, -1);// 405 Method Not Allowed
				ex.close();
				return;
			}

			try
			{
				var hi = payloadMapper.readValue(ex.getRequestBody(), SagaEventHandlerInvocation.class);
				var state = handler.invoke(hi.getEventMessage(),
						hi.getSagaState(),
						application.getCommandGateway(),
						application.getQueryGateway()
				);
				String resp = payloadMapper.writeValueAsString(state);
				ex.getResponseHeaders().set("Content-Type", "application/json");
				ex.sendResponseHeaders(200, resp.getBytes().length);
				OutputStream output = ex.getResponseBody();
				output.write(resp.getBytes());
				output.flush();
			}catch (Exception e){
				e.printStackTrace();
				ex.sendResponseHeaders(500, -1);
			}
			ex.close();
		});

	}

	public void start(){

		server.setExecutor(null);
		server.start();
		logger.info("Server started");
	}

	public void stop() {
		server.stop(0);
	}
}
