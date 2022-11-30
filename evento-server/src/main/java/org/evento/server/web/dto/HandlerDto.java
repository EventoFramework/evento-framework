package org.evento.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
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
	private String bundleId;
	private String componentName;
	private PayloadDto returnType;
	private ComponentType componentType;
	private HandlerType handlerType;
	private boolean returnIsMultiple;
	private Map<Integer,PayloadDto> invocations;

	public HandlerDto(Handler handler) {
		this.uuid = handler.getUuid();
		this.bundleId = handler.getBundle().getId();
		this.componentName = handler.getComponentName();
		this.componentType = handler.getComponentType();
		this.handlerType = handler.getHandlerType();
		this.returnIsMultiple = handler.isReturnIsMultiple();
		this.handledPayload = handler.getHandledPayload() == null ? null : new PayloadDto(handler.getHandledPayload());
		this.returnType = handler.getReturnType() == null ? null :  new PayloadDto(handler.getReturnType());
		this.invocations = new HashMap<>();
		for (Map.Entry<Integer, Payload> i : handler.getInvocations().entrySet()) {
			this.invocations.put(i.getKey(), new PayloadDto(i.getValue()));
		}
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