package org.eventrails.server.domain.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "core__saga_state")
public class SagaState {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	private String sagaName;

	@Column(columnDefinition = "JSON")
	private String currentState;

	public SagaState(String sagaName) {
		this.sagaName = sagaName;
	}


}
