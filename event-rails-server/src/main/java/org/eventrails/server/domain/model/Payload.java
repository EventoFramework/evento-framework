package org.eventrails.server.domain.model;

import lombok.*;
import org.eventrails.server.domain.model.types.PayloadType;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Embeddable
public class Payload implements Serializable {
	@Id
	@Column(name = "name")
	private String name;

	@OneToOne(cascade = CascadeType.ALL)
	@PrimaryKeyJoinColumn
	private Handler handler;

	@Enumerated(EnumType.STRING)
	private PayloadType type;
	@Column(columnDefinition = "JSON")
	private String jsonSchema;

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
