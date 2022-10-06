package org.eventrails.modeling.ranch;

public interface RanchMessageHandler {

	public String handleDomainCommand(String domainCommandName, String domainCommandPayload) throws Exception;
	public String handleServiceCommand(String serviceCommandName, String serviceCommandPayload) throws Exception;
	public String handleQuery(String queryName, String queryPayload) throws Exception;
	public void handleProjectorEvent(String eventName, String projectorName, String eventPayload) throws Exception;
	public void handleProjectorEvent(String eventName, String eventPayload) throws Exception;
	public String handleSagaEvent(String eventName, String sagaName, String eventPayload) throws Exception;
}
