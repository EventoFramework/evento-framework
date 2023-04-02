package org.evento.server.domain.model;

import lombok.*;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Embeddable
@Table(name = "core__payload")
@AllArgsConstructor
public class Payload implements Serializable {
	@Id
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "LONGTEXT")
	private String detail;

	private String domain;
	@OneToMany(mappedBy = "handledPayload")
	@ToString.Exclude
	private List<Handler> handlers;

	@Enumerated(EnumType.STRING)
	private PayloadType type;
	@Column(columnDefinition = "LONGTEXT")
	private String jsonSchema;

	private String registeredIn;

	private Instant updatedAt;

	private boolean isValidJsonSchema;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Payload payload = (Payload) o;
		return name != null && Objects.equals(name, payload.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
