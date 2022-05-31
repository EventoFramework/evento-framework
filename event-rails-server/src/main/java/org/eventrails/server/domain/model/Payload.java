package org.eventrails.server.domain.model;

import lombok.*;
import org.eventrails.server.domain.model.types.PayloadType;
import org.hibernate.Hibernate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Payload {
	@Id
	private String name;
	private PayloadType type;
	@Column(columnDefinition = "JSON")
	private String schema;

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
