package org.eventrails.modeling.gateway;


public interface MessageGateway {
	String handleInvocation(String payload) throws Exception;

	void handleEvent(PublishedEvent event) throws Exception;
}
