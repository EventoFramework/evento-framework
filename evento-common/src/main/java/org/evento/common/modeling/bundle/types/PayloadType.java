package org.evento.common.modeling.bundle.types;

/**
 * Represents the types of payloads that can be used in an application.
 */
public enum PayloadType {
	Command,
	Event,
	Query,
	View,
	DomainCommand,
	DomainEvent,
	ServiceCommand,
	ServiceEvent,
	Invocation
}
