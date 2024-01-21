package org.evento.server.performance.model;

import lombok.Getter;
import lombok.Setter;

/**
 * ActionNode is an abstract class that represents a node in a graph with an associated action and action type.
 */
@Setter
@Getter
public abstract class ActionNode extends Node {


    private String action;
    private String actionType;

    /**
     * Constructs a new instance of the {@code ActionNode} class with the specified identifier.
     *
     * @param id The identifier of the action node.
     */
    public ActionNode(long id) {
        super(id);
    }

    /**
     * ActionNode is an abstract class that represents a node in a graph with an associated action and action type.
     */
    public ActionNode() {
    }
}
