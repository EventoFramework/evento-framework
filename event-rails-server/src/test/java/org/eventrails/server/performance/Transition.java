package org.eventrails.server.performance;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Transition {

    private long id;
    private String name;
    private Set<Post> target;

    public Transition(long id, String name) {
        this.id = id;
        this.name = name;
        target = new HashSet<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Post> getTarget() {
        return target;
    }

    public void setTarget(Set<Post> target) {
        this.target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transition that = (Transition) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
