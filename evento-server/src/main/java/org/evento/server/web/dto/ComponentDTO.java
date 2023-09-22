package org.evento.server.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.server.domain.model.Component;
import org.evento.server.domain.model.Handler;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class ComponentDTO {

	private String componentName;

	private String bundleId;

	private ComponentType componentType;

	private String description;

	private String detail;

	private Instant updatedAt;

	private List<HandlerDto> handlers;

	private String path;
	private Integer line;


	public ComponentDTO(Component c, List<Handler> handlers) {
		this.componentName = c.getComponentName();
		this.componentType = c.getComponentType();
		this.bundleId = c.getBundle().getId();
		this.description = c.getDescription();
		this.detail = c.getDetail();
		this.updatedAt = c.getUpdatedAt();
		this.handlers = handlers.stream().map(HandlerDto::new).toList();
		this.path = c.getPath();
		this.line = c.getLine();
	}
}
