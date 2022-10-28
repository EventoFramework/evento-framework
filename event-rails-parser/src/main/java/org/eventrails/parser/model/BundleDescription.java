package org.eventrails.parser.model;

import org.eventrails.parser.model.node.*;
import org.eventrails.parser.model.payload.*;

import java.io.Serializable;
import java.util.List;

public class BundleDescription implements Serializable {

	private List<Node> nodes;
	private List<PayloadDescription> payloadDescriptions;

	public BundleDescription(List<Node> nodes, List<PayloadDescription> payloadDescriptions) {
		this.nodes = nodes;
		this.payloadDescriptions = payloadDescriptions;
	}

	public BundleDescription() {
	}

	public List<Node> getNodes() {
		return nodes;
	}

	public List<PayloadDescription> getPayloadDescriptions() {
		return payloadDescriptions;
	}
}
