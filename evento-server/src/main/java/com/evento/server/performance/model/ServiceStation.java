package com.evento.server.performance.model;

import lombok.Getter;
import lombok.Setter;
import com.evento.server.service.performance.PerformanceStoreService;

import java.util.HashMap;

@Setter
public class ServiceStation extends ActionNode implements HasTarget {

    @Getter
    private String bundle;
    @Getter
    private String component;
    @Getter
    private String componentType;

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
        super.setAction(action);
        this.numServers = numServers;
        this.async = async;
        this.meanServiceTime = meanServiceTime;
        this.setActionType(actionType);
        this.componentType = componentType;
        this.handlerId = handlerId;
    }

    public ServiceStation() {
        super();
    }


    public boolean getAsync() {
        return async;
    }


    public void addTarget(ActionNode a, PerformanceStoreService performanceStoreService) {
        if (a instanceof ServiceStation ss) {
            var frequency = performanceStoreService.getInvocationProbability(bundle, component, getAction(), ss.getAction());
            target.put(a, frequency);
        } else {
            target.put(a, null);
        }
    }
}
