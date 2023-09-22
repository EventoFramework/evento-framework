package org.evento.parser.model.payload;

import java.io.Serializable;

public class PayloadDescription implements Serializable {
	private String name;

	private String domain;
	private String type;
	private String schema;

	private String description;
	private String detail;

	private String path;
	private int line;

	public PayloadDescription(String name, String domain, String type, String schema, int line) {
		this.name = name;
		this.type = type;
		this.schema = schema;
		this.domain = domain;
		this.line = line;
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

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getLine() {
		return line;
	}

	public void setLine(int line) {
		this.line = line;
	}
}
