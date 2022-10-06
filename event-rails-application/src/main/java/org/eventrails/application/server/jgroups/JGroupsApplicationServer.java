package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.application.EventRailsApplication;
import org.eventrails.application.reference.ProjectorReference;
import org.eventrails.application.server.ApplicationServer;
import org.eventrails.application.server.http.HttpApplicationServer;
import org.eventrails.application.service.RanchMessageHandlerImpl;
import org.eventrails.modeling.messaging.invocation.*;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.ServiceEventMessage;
import org.eventrails.modeling.ranch.RanchMessageHandler;
import org.eventrails.modeling.ranch.RanchHandlerResponse;
import org.eventrails.shared.ClusterMessage;
import org.eventrails.shared.ClusterMessageResponse;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.Response;
import org.jgroups.blocks.RpcDispatcher;

public class JGroupsApplicationServer implements ApplicationServer {

	private static final Logger logger = LogManager.getLogger(HttpApplicationServer.class);


	private final MessageDispatcher server;


	public JGroupsApplicationServer(JChannel channel, EventRailsApplication application) throws Exception {
		var ranchMessageHandler = new RanchMessageHandlerImpl(application);
		server = new RpcDispatcher(channel, ranchMessageHandler);
	}

	public void start() throws Exception {
		server.start();
		logger.info("Server started");
	}

	public void stop() {
		server.stop();
	}


}
