package org.eventrails.server.domain.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class HandlerId implements Serializable {
	private NanoService nanoService;
	private String componentName;
	private String handledAction;
	private String returnType;
}
