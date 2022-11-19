package org.eventrails.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.common.modeling.bundle.types.ComponentType;
import org.eventrails.common.modeling.bundle.types.HandlerType;
import org.eventrails.common.modeling.bundle.types.PayloadType;

import java.io.Serializable;
import java.util.List;

/**
 * A DTO for the {@link Payload} entity
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayloadDto implements Serializable {
	private String name;
	private List<HandlerDto> handlers;
	private PayloadType type;
	private String jsonSchema;


	/**
	 * A DTO for the {@link Handler} entity
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class HandlerDto implements Serializable {
		private String uuid;
		private String bundleId;
		private String componentName;
		private String returnTypeName;
		private ComponentType componentType;
		private HandlerType handlerType;
		private boolean returnIsMultiple;
	}
}