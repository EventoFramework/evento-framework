package org.eventrails.modeling.ranch;

public interface RanchMessageHandler {

	public String handleDomainCommand(String domainCommandName, String domainCommandPayload) throws Throwable;
	public String handleServiceCommand(String serviceCommandName, String serviceCommandPayload) throws Throwable;
	public String handleQuery(String queryName, String queryPayload) throws Throwable;
	public void handleProjectorEvent(String eventName, String projectorName, String payload) throws Throwable;
	public String handleSagaEvent(String eventName, String sagaName, String payload) throws Throwable;
}
