package com.evento.common.versioning;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One step of a payload's history (2.6): turns the JSON of payload {@code fromName} at revision
 * {@code fromRevision} into the JSON of the next shape. Steps are chained by {@link Upcasters}
 * until no step starts where the previous one ended.
 *
 * <p>Two kinds of step, combinable:
 * <ul>
 *   <li><b>Revision</b>: same class, its JSON reshaped. The class changed in place and carries a
 *       higher {@link Revision}.
 *       <pre>{@code Upcaster.from(OrderPlaced.class, 0).to(1, json -> json.set("customerId", json.remove("clientId")));}</pre></li>
 *   <li><b>Rename</b>: the payload becomes another class, e.g. {@code OrderPlaced} replaced by
 *       {@code OrderPlacedV2}. Handlers then only handle the new class, and the old one can be
 *       deleted: its stored payloads become the new class when read. The old class is named by
 *       its payload name, so it need not exist any more.
 *       <pre>{@code Upcaster.from("OrderPlaced", 0).to(OrderPlacedV2.class, 0, json -> json.put("channel", "web"));}</pre></li>
 * </ul>
 *
 * <p>The transform receives the payload's fields as Jackson wrote them: values whose declared
 * type is not final carry their class, as {@code ["java.util.ArrayList", [ ... ]]} or
 * {@code ["java.math.BigDecimal", 1.10]} (lists, maps, BigDecimal, non-final classes); a
 * string, a primitive or its box, a record or an enum does not. A step that adds such a value
 * writes it the same way. It must be deterministic and free of side effects: it runs on
 * every read, on every replay, in every bundle that reads the payload.
 */
public final class Upcaster {

    private final String fromName;
    private final int fromRevision;
    private final String toClassName;
    private final String toName;
    private final int toRevision;
    private final Consumer<ObjectNode> transform;

    private Upcaster(String fromName, int fromRevision, String toClassName, int toRevision,
                     Consumer<ObjectNode> transform) {
        this.fromName = fromName;
        this.fromRevision = fromRevision;
        this.toClassName = toClassName;
        this.toName = toClassName == null ? fromName : Revisions.payloadName(toClassName);
        this.toRevision = toRevision;
        this.transform = transform;
        if (toRevision < 0) {
            throw new IllegalArgumentException("revision must be 0 or more: " + toRevision);
        }
        if (toName.equals(fromName) && toRevision <= fromRevision) {
            throw new IllegalArgumentException("an upcaster of " + fromName + " must raise its revision: "
                    + fromRevision + " -> " + toRevision);
        }
    }

    /**
     * Starts a step from a payload as it was stored.
     *
     * @param payloadName the simple class name of the payload, as stored (the class may no longer exist)
     * @param revision    the revision it was stored with ({@code 0} for payloads written before revisions)
     */
    public static From from(String payloadName, int revision) {
        Objects.requireNonNull(payloadName, "payloadName");
        if (payloadName.isBlank() || payloadName.indexOf('.') >= 0) {
            throw new IllegalArgumentException("payloadName is the simple class name, e.g. \"OrderPlaced\": "
                    + payloadName);
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be 0 or more: " + revision);
        }
        return new From(payloadName, revision);
    }

    /** Starts a step from a payload class at {@code revision}. */
    public static From from(Class<?> payloadType, int revision) {
        return from(Revisions.payloadName(payloadType), revision);
    }

    /** The payload a step starts from; {@code to(...)} says what it becomes. */
    public static final class From {
        private final String name;
        private final int revision;

        private From(String name, int revision) {
            this.name = name;
            this.revision = revision;
        }

        /** Same class, a later revision: {@code transform} reshapes the JSON in place. */
        public Upcaster to(int revision, Consumer<ObjectNode> transform) {
            return new Upcaster(name, this.revision, null, revision,
                    Objects.requireNonNull(transform, "transform"));
        }

        /**
         * Another class, its JSON unchanged.
         *
         * @param revision the revision of {@code type} the stored JSON already matches; further
         *                 steps of {@code type} start from there
         */
        public Upcaster to(Class<?> type, int revision) {
            return new Upcaster(name, this.revision, Objects.requireNonNull(type, "type").getName(), revision, null);
        }

        /** Another class, at {@code revision}, with {@code transform} reshaping the JSON in place. */
        public Upcaster to(Class<?> type, int revision, Consumer<ObjectNode> transform) {
            return new Upcaster(name, this.revision, Objects.requireNonNull(type, "type").getName(), revision,
                    Objects.requireNonNull(transform, "transform"));
        }
    }

    /** @return the simple name of the payload this step starts from */
    public String fromName() {
        return fromName;
    }

    /** @return the revision this step starts from */
    public int fromRevision() {
        return fromRevision;
    }

    /** @return the simple name of the payload this step produces */
    public String toName() {
        return toName;
    }

    /** @return the revision this step produces */
    public int toRevision() {
        return toRevision;
    }

    /** @return the binary class name this step renames the payload to, or null when it keeps its class */
    public String toClassName() {
        return toClassName;
    }

    void apply(ObjectNode json) {
        if (transform != null) transform.accept(json);
    }

    @Override
    public String toString() {
        return fromName + "@" + fromRevision + " -> " + toName + "@" + toRevision;
    }
}
