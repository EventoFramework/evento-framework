package com.evento.server.domain.model.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.evento.common.modeling.bundle.types.HandlerType;
import org.hibernate.HibernateException;

import jakarta.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	/**
	 * The events a command handler declares it may produce (3.0.1): for an aggregate handler
	 * returning {@code DomainEvents} its {@code produces}, otherwise its return type. Empty for a
	 * handler registered by an older bundle; {@link #emittedEvents()} then falls back on the
	 * return type.
	 */
	@ManyToMany(fetch = FetchType.EAGER)
	@ToString.Exclude
	@JoinTable(name = "core__handler__produced_event",
			joinColumns = @JoinColumn(name = "handler_uuid"),
			inverseJoinColumns = @JoinColumn(name = "produced_event_name"))
	private Set<Payload> producedEvents = new HashSet<>();

	private String associationProperty;

	private Integer line;

	/**
	 * Name of the {@code ConsumerExecutor} this handler dispatches to, or {@code null}/empty
	 * when it consumes inline. Non-empty means the handler runs in parallel, which changes
	 * its ordering and delivery guarantees — worth surfacing in the dashboard.
	 */
	private String executor;

	public static String generateId(String bundleId, String componentName, String handledPayloadName) throws RuntimeException {
		var str = bundleId + componentName + handledPayloadName;
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		byte[] hash = digest.digest(
				str.getBytes(StandardCharsets.UTF_8));
		StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
		return hexString.toString();
	}

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


	public void generateId() throws HibernateException {
		setUuid(generateId(
				this.getComponent().getBundle().getId(), this.getComponent().getComponentName(), this.getHandledPayload().getName()
		));
	}

	/**
	 * The events this handler may produce, as the graph and the performance model draw them: its
	 * declared produced events, or for a command handler registered without them (an older
	 * bundle) its return type. Empty for any other handler: a query handler's return type is
	 * the view it answers with, not an event.
	 */
	public List<Payload> emittedEvents() {
		if (producedEvents != null && !producedEvents.isEmpty()) {
			return producedEvents.stream().sorted(java.util.Comparator.comparing(Payload::getName)).toList();
		}
		if (returnType != null && (handlerType == HandlerType.AggregateCommandHandler
				|| handlerType == HandlerType.CommandHandler)) {
			return List.of(returnType);
		}
		return List.of();
	}


}
