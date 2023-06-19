package org.evento.parser.model.payload;

import java.io.Serializable;

public class PayloadDescription implements Serializable {
	private String name;
	private String type;
	private String schema;

	public PayloadDescription(String name, String type, String schema) {
		this.name = name;
		this.type = type;
		this.schema = schema;
	}

	public PayloadDescription() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}
}
