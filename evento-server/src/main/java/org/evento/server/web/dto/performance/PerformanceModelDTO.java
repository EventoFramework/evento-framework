package org.evento.server.web.dto.performance;

import org.evento.server.domain.performance.model.PerformanceModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PerformanceModelDTO implements Serializable {

	private List<NodeDTO> nodes = new ArrayList<>();

	public PerformanceModelDTO(PerformanceModel performanceModel) {
		this.nodes = performanceModel.getNodes().stream().map(NodeDTO::new).collect(Collectors.toList());
	}

	public PerformanceModelDTO() {
	}

	public List<NodeDTO> getNodes() {
		return nodes;
	}

	public void setNodes(List<NodeDTO> nodes) {
		this.nodes = nodes;
	}
}
