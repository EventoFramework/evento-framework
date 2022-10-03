package org.eventrails.parser.model;

import org.eventrails.parser.model.node.*;
import org.eventrails.parser.model.payload.*;

import java.util.List;

public class RanchApplicationDescription {

	private final List<Node> nodes;
	private final List<PayloadDescription> payloadDescriptions;

	public RanchApplicationDescription(List<Node> nodes, List<PayloadDescription> payloadDescriptions) {
		this.nodes = nodes;
		this.payloadDescriptions = payloadDescriptions;
	}


	public List<Node> getNodes() {
		return nodes;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}
}
