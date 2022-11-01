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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A DTO for the {@link Handler} entity
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HandlerDto implements Serializable {
	private String uuid;
	private PayloadDto handledPayload;
	private String bundleName;
	private String componentName;
	private PayloadDto returnType;
	private ComponentType componentType;
	private HandlerType handlerType;
	private boolean returnIsMultiple;
	private Set<PayloadDto> invocations;

	public HandlerDto(Handler handler) {
		this.uuid = handler.getUuid();
		this.bundleName = handler.getBundle().getName();
		this.componentName = handler.getComponentName();
		this.componentType = handler.getComponentType();
		this.handlerType = handler.getHandlerType();
		this.returnIsMultiple = handler.isReturnIsMultiple();
		this.handledPayload = handler.getHandledPayload() == null ? null : new PayloadDto(handler.getHandledPayload());
		this.returnType = handler.getReturnType() == null ? null :  new PayloadDto(handler.getReturnType());
		this.invocations = handler.getInvocations().stream().map(PayloadDto::new).collect(Collectors.toSet());
	}

	/**
	 * A DTO for the {@link Payload} entity
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PayloadDto implements Serializable {
		private String name;
		private PayloadType type;

		public PayloadDto(Payload payload) {
			this.name = payload.getName();
			this.type = payload.getType();
		}

	}
}