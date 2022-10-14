package org.eventrails.server.domain.model;

import lombok.*;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "core__handler")
public class Handler {

	@Id
	private String uuid;

	@ManyToOne
	private Payload handledPayload;

	@ManyToOne(cascade = CascadeType.REMOVE)
	private Ranch ranch;

	private String componentName;

	@ManyToOne(fetch = FetchType.EAGER)
	private Payload returnType;


	@Enumerated(EnumType.STRING)
	private ComponentType componentType;

	@Enumerated(EnumType.STRING)
	private HandlerType handlerType;
	private boolean returnIsMultiple;

	@ManyToMany
	@ToString.Exclude
	@JoinTable(name = "core__handler_invocation")
	private Set<Payload> invocations;

	private String associationProperty;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Handler handler = (Handler) o;
		return ranch != null && Objects.equals(ranch, handler.ranch)
				&& componentName != null && Objects.equals(componentName, handler.componentName)
				&& handledPayload != null && Objects.equals(handledPayload, handler.handledPayload)
				&& returnType != null && Objects.equals(returnType, handler.returnType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ranch,
				componentName,
				handledPayload,
				returnType);
	}

	@SneakyThrows
	public void generateId() throws HibernateException {
		var str = this.getRanch() + this.getComponentName() + this.getComponentType() + this.getHandledPayload();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(
				str.getBytes(StandardCharsets.UTF_8));
		StringBuilder hexString = new StringBuilder(2 * hash.length);
		for (int i = 0; i < hash.length; i++)
		{
			String hex = Integer.toHexString(0xff & hash[i]);
			if (hex.length() == 1)
			{
				hexString.append('0');
			}
			hexString.append(hex);
		}
		setUuid(hexString.toString());
	}


}
