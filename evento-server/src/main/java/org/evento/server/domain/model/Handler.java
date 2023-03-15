package org.evento.server.domain.model;

import lombok.*;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;

import javax.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "core__handler")
public class Handler implements Serializable {

	@Id
	private String uuid;

	@ManyToOne
	private Payload handledPayload;


	@ManyToOne(fetch = FetchType.EAGER)
	private Payload returnType;


	@ManyToOne(fetch = FetchType.EAGER)
	private Component component;

	@Enumerated(EnumType.STRING)
	private HandlerType handlerType;
	private boolean returnIsMultiple;

	@ManyToMany
	@ToString.Exclude
	@JoinTable(name = "core__handler__invocation")
	private Map<Integer, Payload> invocations;

	private String associationProperty;


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Handler handler)) return false;

		return getUuid() != null ? getUuid().equals(handler.getUuid()) : handler.getUuid() == null;
	}

	@Override
	public int hashCode() {
		return getUuid() != null ? getUuid().hashCode() : 0;
	}

	@SneakyThrows
	public void generateId() throws HibernateException {
		setUuid(generateId(
				this.getComponent().getBundle().getId(), this.getComponent().getComponentName(), this.getHandledPayload().getName()
		));
	}

	public static String generateId(String bundleId, String componentName, String handledPayloadName) throws NoSuchAlgorithmException {
		var str = bundleId + componentName + handledPayloadName;
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
		return hexString.toString();
	}


}
