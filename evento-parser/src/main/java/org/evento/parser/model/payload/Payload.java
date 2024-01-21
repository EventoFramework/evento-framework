package org.evento.parser.model.payload;

import java.io.Serializable;

/**
 * The Payload class represents a payload object.
 * It is Serializable to allow for object serialization and deserialization.
 */
public class Payload implements Serializable {

	private String name;

	private String domain;

	/**
	 * Constructs a new Payload object with the specified name.
	 *
	 * @param name the name of the payload
	 */
	public Payload(String name) {
		this.name = name;
	}

	/**
	 * The Payload class represents a payload object.
	 * It is Serializable to allow for object serialization and deserialization.
	 */
	public Payload() {
	}

	/**
	 * Gets the name of the payload.
	 *
	 * @return the name of the payload
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of the payload.
	 *
	 * @param name the name to be set for the payload
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieves the domain of the payload.
	 *
	 * @return the domain of the payload
	 */
	public String getDomain() {
		return domain;
	}

	/**
	 * Sets the domain of the payload.
	 *
	 * @param domain the domain to be set for the payload
	 */
	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
