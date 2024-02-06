package com.evento.server.web.dto.performance;

import lombok.Getter;
import lombok.Setter;
import com.evento.server.performance.model.Node;
import com.evento.server.performance.model.ServiceStation;
import com.evento.server.performance.model.Source;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
public class NodeDTO implements Serializable {

	@Getter
	private long id;
	@Getter
	private String bundle;
	@Getter
	private String component;
	@Getter
	private String componentType;
	@Getter
	private String action;
	@Getter
	private String actionType;

	private boolean async;
	@Getter
	private Map<Long, Double> target = new HashMap<>();
	@Getter
	private Integer numServers;
	@Getter
	private String type;

	@Getter
	private String name;
	@Getter
	private Double meanServiceTime;

	@Getter
	private String handlerId;

	@Getter
	private String path;
	@Getter
	private List<Integer> lines;

	public NodeDTO(Node node) {
		this.id = node.getId();
		this.type = node.getClass().getSimpleName();
		this.path = node.getPath();
		this.lines = node.getLines();
		if (node instanceof ServiceStation s)
		{
			this.bundle = s.getBundle();
			this.component = s.getComponent();
			this.action = s.getAction();
			this.async = s.getAsync();
			this.actionType = s.getActionType();
			this.target = new HashMap<>();
			s.getTarget().forEach((k, v) -> this.target.put(k.getId(), v));
			this.numServers = s.getNumServers();
			this.meanServiceTime = s.getMeanServiceTime();
			this.componentType = s.getComponentType();
			this.handlerId = s.getHandlerId();
		} else if (node instanceof Source s)
		{
			this.target = new HashMap<>();
			s.getTarget().forEach((k, v) -> this.target.put(k.getId(), v));
			this.name = s.getName();
			this.action = s.getName();
			this.actionType = s.getType();
			this.handlerId = s.getHandlerId();
		}
	}

	public NodeDTO() {
	}

	public boolean getAsync() {
		return async;
	}


}
