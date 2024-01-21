package org.evento.common.modeling.bundle.types;

/**
 * Represents the types of payloads that can be used in an application.
 */
public enum PayloadType {
	/**
	 * Represents a command that can be executed within an application.
	 */
	Command,
	/**
	 * Represents an event that occurred within an application.
	 */
	Event,
	/**
	 * Represents a Query object used to retrieve data from a data source.
	 */
	Query,
	/**
	 * Represents a View in an application.
	 */
	View,
	/**
	 * Represents a command within the domain of an application.
	 */
	DomainCommand,
	/**
	 * Represents a domain event that occurred within an application.
	 */
	DomainEvent,
	/**
	 * Represents a command to be executed within a service.
	 */
	ServiceCommand,
	/**
	 * Represents an event that occurred within a service.
	 */
	ServiceEvent,
	/**
	 * Represents an invocation within an application.
	 */
	Invocation
}
