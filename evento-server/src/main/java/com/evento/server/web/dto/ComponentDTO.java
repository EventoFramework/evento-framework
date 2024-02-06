package com.evento.server.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Handler;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for Components
 */
@Data
@NoArgsConstructor
public class ComponentDTO {

    /**
     * Name of the component
     */
	private String componentName;

    /**
     * ID of the bundle to which component belongs
     */
	private String bundleId;

    /**
     * Type of the component
     */
	private ComponentType componentType;

    /**
     * Description of the component
     */
	private String description;

    /**
     * Detail of the component
     */
	private String detail;

    /**
     * Time when the component was last updated
     */
	private Instant updatedAt;

    /**
     * List of handlers associated with the component
     */
	private List<HandlerDto> handlers;

    /**
     * Path of the component in codebase
     */
	private String path;

    /**
     * Line number in code where component starts
     */
	private Integer line;

    /**
     * Constructor to create DTO from Component domain model and associated handlers
     *
     * @param c Component domain model
     * @param handlers List of associated handlers
     */
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