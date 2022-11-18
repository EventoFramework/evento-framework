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
@Table(name = "core__event_consumer_state")
public class EventConsumerState {

	@Id
	private String componentName;

	private String bundleName;

	private Long lastEventSequenceNumber;

}
