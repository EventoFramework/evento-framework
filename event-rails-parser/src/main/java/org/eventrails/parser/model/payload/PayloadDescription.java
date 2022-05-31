package org.eventrails.parser.model.payload;

import com.google.gson.JsonObject;

public class PayloadDescription {
	private String name;
	private String type;
	private JsonObject schema;

	public PayloadDescription(String name, String type, JsonObject schema) {
		this.name = name;
		this.type = type;
		this.schema = schema;
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

	public JsonObject getSchema() {
		return schema;
	}

	public void setSchema(JsonObject schema) {
		this.schema = schema;
	}
}
