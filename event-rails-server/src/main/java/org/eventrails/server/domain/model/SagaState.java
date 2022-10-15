package org.eventrails.server.domain.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eventrails.modeling.state.SerializedSagaState;
import org.eventrails.server.config.JsonConverter;

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

	private boolean isEnded;
	@Column(columnDefinition = "JSON")
	private String sagaState;

	public SagaState(String sagaName, SerializedSagaState<?> serializedSagaState) {
		this.sagaName = sagaName;
		this.sagaState = serializedSagaState.getSerializedObject();
		this.isEnded = serializedSagaState.isEnded();
	}


	public SagaState(Long id, String sagaName, SerializedSagaState<?> serializedSagaState) {
		this(sagaName, serializedSagaState);
		this.id = id;

	}

	public SerializedSagaState<?> getSerializedSagaState() {
		var s =  new SerializedSagaState<>();
		s.setSerializedObject(sagaState);
		s.setEnded(isEnded);
		return s;
	}
}
