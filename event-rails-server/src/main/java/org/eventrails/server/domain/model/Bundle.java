package org.eventrails.server.domain.model;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Table(name = "core__bundle")
public class Bundle {

	@Id
	private String name;
	@Enumerated(EnumType.STRING)
	private BucketType bucketType;
	private String artifactCoordinates;
	private String artifactOriginalName;
	private boolean containsHandlers;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Bundle that = (Bundle) o;
		return name != null && Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
