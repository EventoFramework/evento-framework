package org.evento.server.performance.model;

import lombok.Getter;
import lombok.Setter;
import org.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;

@Setter
public class ServiceStation extends Node {

    @Getter
    private String bundle;
    @Getter
    private String component;
    @Getter
    private String componentType;
    @Getter
    private String action;
    @Getter
    private String actionType;

    private boolean async;
    @Getter
    private HashMap<Node, Double> target = new HashMap<>();
    @Getter
    private Integer numServers;

    @Getter
    private Double meanServiceTime;

    @Getter
    private String handlerId;

    public ServiceStation(long id, String bundle, String component, String componentType, String action,
                          String actionType,
                          boolean async,
                          Integer numServers, Double meanServiceTime,
                          String handlerId) {
        super(id);
        this.bundle = bundle;
        this.component = component;
        this.action = action;
        this.numServers = numServers;
        this.async = async;
        this.meanServiceTime = meanServiceTime;
        this.actionType = actionType;
        this.componentType = componentType;
        this.handlerId = handlerId;
    }

    public ServiceStation() {

    }


    public boolean getAsync() {
        return async;
    }


    public void addTarget(Node a, PerformanceStoreService performanceStoreService) {
        if (a instanceof ServiceStation ss) {
            var frequency = performanceStoreService.getInvocationProbability(bundle, component, action, ss.action);
            target.put(a, frequency);
        } else {
            target.put(a, null);
        }
    }
}
