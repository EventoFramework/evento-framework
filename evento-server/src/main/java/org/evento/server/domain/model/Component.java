package org.evento.server.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.evento.common.modeling.bundle.types.ComponentType;

import javax.persistence.*;
import java.time.Instant;


@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "core__component")
@Entity
public class Component {
	@Id
	private String componentName;

	@ManyToOne()
	private Bundle bundle;

	@Enumerated(EnumType.STRING)
	private ComponentType componentType;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "LONGTEXT")
	private String detail;

	private Instant updatedAt;

	private String path;
	private Integer line;

}
