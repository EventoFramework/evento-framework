package org.eventrails.server.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "core__component_event_consuming_state")
public class ComponentEventConsumingState {

	@Id
	private String componentName;

	private Long lastEventSequenceNumber;

	@Column(columnDefinition = "JSON")
	private String currentState;

}
