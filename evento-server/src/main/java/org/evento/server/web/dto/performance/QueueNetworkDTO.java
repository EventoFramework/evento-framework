package org.evento.server.web.dto.performance;

import org.evento.server.domain.performance.queue.QueueNetwork;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QueueNetworkDTO implements Serializable {

	private List<NodeDTO> nodes = new ArrayList<>();

	public QueueNetworkDTO(QueueNetwork queueNetwork) {
		this.nodes = queueNetwork.getNodes().stream().map(NodeDTO::new).collect(Collectors.toList());
	}

	public QueueNetworkDTO() {
	}

	public List<NodeDTO> getNodes() {
		return nodes;
	}

	public void setNodes(List<NodeDTO> nodes) {
		this.nodes = nodes;
	}
}
