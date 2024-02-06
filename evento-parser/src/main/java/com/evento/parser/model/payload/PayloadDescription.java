package com.evento.parser.model.payload;

import java.io.Serializable;

/**
 * The PayloadDescription class represents the description of a payload.
 * It contains information such as name, domain, type, schema, description, detail, path, and line number.
 * It implements the Serializable interface to support serialization.
 */
public class PayloadDescription implements Serializable {
	private String name;

	private String domain;
	private String type;
	private String schema;

	private String description;
	private String detail;

	private String path;
	private int line;

	/**
	 * Constructs a new PayloadDescription object with the specified name, domain, type, schema, and line number.
	 *
	 * @param name   the name of the payload
	 * @param domain the domain of the payload
	 * @param type   the type of the payload
	 * @param schema the schema of the payload
	 * @param line   the line number where the payload occurs
	 */
	public PayloadDescription(String name, String domain, String type, String schema, int line) {
		this.name = name;
		this.type = type;
		this.schema = schema;
		this.domain = domain;
		this.line = line;
	}

	/**
	 * The PayloadDescription class represents the description of a payload.
	 * It contains information such as name, domain, type, schema, description, detail, path, and line number.
	 */
	public PayloadDescription() {
	}

	/**
	 * Retrieves the name of the payload.
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
	 * Retrieves the type of the payload.
	 *
	 * @return the type of the payload
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets the type of the payload.
	 *
	 * @param type the type to be set for the payload
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Retrieves the schema of the payload.
	 *
	 * @return the schema of the payload
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * Sets the schema of the payload.
	 *
	 * @param schema the schema to be set for the payload
	 */
	public void setSchema(String schema) {
		this.schema = schema;
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

	/**
	 * Retrieves the description of the payload.
	 *
	 * @return the description of the payload
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description of the payload.
	 *
	 * @param description the description to be set for the payload
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Retrieves the detail of the payload.
	 *
	 * @return the detail of the payload
	 */
	public String getDetail() {
		return detail;
	}

	/**
	 * Sets the detail of the payload.
	 *
	 * @param detail the detail to be set for the payload
	 */
	public void setDetail(String detail) {
		this.detail = detail;
	}

	/**
	 * Retrieves the path of the payload.
	 *
	 * @return the path of the payload
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the path of the payload.
	 *
	 * @param path the path to be set for the payload
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Retrieves the line number where the payload occurs.
	 *
	 * @return the line number where the payload occurs
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Sets the line number where the payload occurs.
	 *
	 * @param line the line number to be set for the payload
	 */
	public void setLine(int line) {
		this.line = line;
	}
}
