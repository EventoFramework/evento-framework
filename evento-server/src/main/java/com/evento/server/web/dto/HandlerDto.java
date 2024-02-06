package com.evento.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.core.Payload;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
	private Map<Integer, PayloadDto> invocations;
	private String path;
	private Integer line;


	public HandlerDto(Handler handler) {
		this.uuid = handler.getUuid();
		this.bundleId = handler.getComponent().getBundle().getId();
		this.componentName = handler.getComponent().getComponentName();
		this.componentType = handler.getComponent().getComponentType();
		this.handlerType = handler.getHandlerType();
		this.returnIsMultiple = handler.isReturnIsMultiple();
		this.handledPayload = handler.getHandledPayload() == null ? null : new PayloadDto(handler.getHandledPayload());
		this.returnType = handler.getReturnType() == null ? null : new PayloadDto(handler.getReturnType());
		this.invocations = new HashMap<>();
		for (Map.Entry<Integer, Payload> i : handler.getInvocations().entrySet())
		{
			this.invocations.put(i.getKey(), new PayloadDto(i.getValue()));
		}
		this.path = handler.getComponent().getPath();
		this.line = handler.getLine();
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

		private String domain;

		public PayloadDto(Payload payload) {
			this.name = payload.getName();
			this.type = payload.getType();
			this.domain = payload.getDomain();
		}

	}
}