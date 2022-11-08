package org.eventrails.server.performance;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Post {
    private long id;
    private String name;
    private Set<Transition> target;

    private long marking;

    public Post(long id, String name) {
        this(id, name, 0);
    }

    public Post(long id, String name, long initialMarking) {
        this.id = id;
        this.name = name;
        target = new HashSet<>();
        marking = initialMarking;
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
}
