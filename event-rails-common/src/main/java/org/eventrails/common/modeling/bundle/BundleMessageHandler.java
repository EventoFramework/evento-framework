package org.eventrails.common.modeling.bundle;

public interface BundleMessageHandler {

	public String handleDomainCommand(String domainCommandName, String domainCommandPayload) throws Throwable;
	public String handleServiceCommand(String serviceCommandName, String serviceCommandPayload) throws Throwable;
	public String handleQuery(String queryName, String queryPayload) throws Throwable;
	public void handleProjectorEvent(String eventName, String projectorName, String payload) throws Throwable;
	public String handleSagaEvent(String eventName, String sagaName, String payload) throws Throwable;
}
