package com.evento.server.web.dto.performance;

import lombok.Getter;
import lombok.Setter;
import com.evento.server.performance.model.PerformanceModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Setter
@Getter
public class PerformanceModelDTO implements Serializable {

	private List<NodeDTO> nodes = new ArrayList<>();

	public PerformanceModelDTO(PerformanceModel performanceModel) {
		this.nodes = performanceModel.getNodes().stream().map(NodeDTO::new).collect(Collectors.toList());
	}

	public PerformanceModelDTO() {
	}

}
