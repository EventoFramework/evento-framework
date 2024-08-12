package com.evento.server.domain.model.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * The Consumer class represents a consumer in the system.
 * A consumer is a component that consumes resources or services provided by another component.
 * <p>
 * Consumers have the following properties:
 * - identifier: The unique identifier of the consumer.
 * - component: The component that the consumer belongs to.
 * - nodeId: The identifier of the node where the consumer is located.
 * - consumerId: The identifier of the consumer within the component.
 * <p>
 * The Consumer class is annotated with the JPA annotations @Entity and @Table to specify that it is a database entity
 * and the name of the corresponding database table respectively.
 * It also makes use of Lombok annotations such as @Getter, @Setter, and @RequiredArgsConstructor to generate getter and setter methods
 * and a constructor with required arguments for the class.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "core__consumer")
@Entity
public class Consumer {
    @Id
    private String identifier;

    /**
     *
     */
    @ManyToOne
    private Component component;
    private String instanceId;
    private String consumerId;
}
