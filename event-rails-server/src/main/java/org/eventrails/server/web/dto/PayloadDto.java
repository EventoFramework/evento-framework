package org.eventrails.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.model.types.PayloadType;

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
		private String bundleName;
		private String componentName;
		private String returnTypeName;
		private ComponentType componentType;
		private HandlerType handlerType;
		private boolean returnIsMultiple;
	}
}