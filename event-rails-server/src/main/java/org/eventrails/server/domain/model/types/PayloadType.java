package org.eventrails.server.domain.model.types;

public enum PayloadType {
	Command,
	Event,
	Query,
	View,
	DomainCommand,
	DomainEvent,
	ServiceCommand,
	ServiceEvent
}
