package org.eventrails.server.domain.model;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Ranch {

	@Id
	private String name;
	@Enumerated(EnumType.STRING)
	private BucketType bucketType;
	private String artifactCoordinates;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Ranch that = (Ranch) o;
		return name != null && Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
