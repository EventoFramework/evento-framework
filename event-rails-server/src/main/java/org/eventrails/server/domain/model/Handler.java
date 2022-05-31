package org.eventrails.server.domain.model;

import lombok.*;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@IdClass(HandlerId.class)
public class Handler {
	@Id
	@ManyToOne(optional = false)
	@JoinColumn(name = "nano_service", nullable = false)
	private NanoService nanoService;
	@Id
	private String componentName;
	@Id
	private String handledAction;
	@Id
	private String returnType;

	private ComponentType componentType;
	private HandlerType handlerType;
	private boolean returnIsMultiple;

	@OneToMany
	@ToString.Exclude
	private Set<Payload> invocations;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Handler handler = (Handler) o;
		return nanoService != null && Objects.equals(nanoService, handler.nanoService)
				&& componentName != null && Objects.equals(componentName, handler.componentName)
				&& handledAction != null && Objects.equals(handledAction, handler.handledAction)
				&& returnType != null && Objects.equals(returnType, handler.returnType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nanoService,
				componentName,
				handledAction,
				returnType);
	}
}
