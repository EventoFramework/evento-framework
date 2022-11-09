package org.eventrails.server.service.performance;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Post {
    private long id;
    private String bundle;
    private String component;
    private String action;
    private Set<Transition> target;

    private long marking;

    public Post(long id, String bundle,String component,String action) {
        this(id, bundle, component, action, 0);
    }

    public Post(long id, String bundle,String component,String action, long initialMarking) {
        this.id = id;
        this.bundle = bundle;
        this.component = component;
        this.action = action;
        target = new HashSet<>();
        marking = initialMarking;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Set<Transition> getTarget() {
        return target;
    }

    public void setTarget(Set<Transition> target) {
        this.target = target;
    }

    public long getMarking() {
        return marking;
    }

    public void setMarking(long marking) {
        this.marking = marking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Post post = (Post) o;
        return id == post.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
