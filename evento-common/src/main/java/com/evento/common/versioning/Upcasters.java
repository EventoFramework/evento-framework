package com.evento.common.versioning;

import com.evento.common.modeling.common.SerializedObject;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.Message;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The upcasters of a bundle, chained (3.0). A payload read with an older shape (from the event
 * store, an aggregate snapshot, a dead-event queue, or sent by a bundle not yet upgraded) goes
 * through every step that starts where the previous one ended, then is deserialized into the
 * class the last step names. Stored payloads are never rewritten: upcasting happens on each
 * read, so the history stays as it was written and an upcaster fixed later applies to all of it.
 *
 * <p>Steps are keyed by the payload they start from (name and revision): one step per key, and
 * no chain may loop. A payload no step starts from is left as it is; one at a revision higher
 * than its class knows (written by a newer bundle) too, and Jackson reads what it can of it.
 *
 * <p>Immutable and thread-safe.
 */
public final class Upcasters {

    /** No upcasting: every payload is read as stored. */
    public static final Upcasters NONE = new Upcasters(List.of());

    /**
     * Reads and writes the payload JSON as a plain tree: no type handling of its own (the type
     * ids are part of the tree), and numbers kept exact, so a step that does not touch a
     * BigDecimal does not round it.
     */
    private static final ObjectMapper TREE_MAPPER = new ObjectMapper()
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private record Key(String name, int revision) {
    }

    private final Map<Key, Upcaster> steps;

    private Upcasters(Collection<Upcaster> upcasters) {
        var byKey = new HashMap<Key, Upcaster>();
        for (var u : upcasters) {
            var previous = byKey.putIfAbsent(new Key(u.fromName(), u.fromRevision()), u);
            if (previous != null) {
                throw new IllegalArgumentException("two upcasters start from " + u.fromName() + " revision "
                        + u.fromRevision() + ": " + previous + " and " + u);
            }
        }
        // A chain is at most one pass over every step; a longer one loops (A -> B -> A).
        for (var start : byKey.keySet()) {
            var key = start;
            for (int hops = 0; byKey.containsKey(key); hops++) {
                if (hops > byKey.size()) {
                    throw new IllegalArgumentException("upcasters loop, starting from " + start.name()
                            + " revision " + start.revision());
                }
                var step = byKey.get(key);
                key = new Key(step.toName(), step.toRevision());
            }
        }
        this.steps = Map.copyOf(byKey);
    }

    /** @return a chain of {@code upcasters} */
    public static Upcasters of(Collection<Upcaster> upcasters) {
        return upcasters == null || upcasters.isEmpty() ? NONE : new Upcasters(List.copyOf(upcasters));
    }

    /** @return a chain of {@code upcasters} */
    public static Upcasters of(Upcaster... upcasters) {
        return of(Arrays.asList(upcasters));
    }

    /** @return whether there is no step at all */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** @return the number of steps */
    public int size() {
        return steps.size();
    }

    /**
     * Upcasts a serialized payload in place: its JSON, its class and its revision become those
     * of the last step that applies.
     *
     * @return whether any step applied
     * @throws UpcastException when the stored JSON is not a typed payload or a step fails
     */
    public boolean upcast(SerializedObject<?> payload) {
        if (steps.isEmpty() || payload == null || payload.getObjectClass() == null
                || payload.getSerializedObject() == null) {
            return false;
        }
        String name = Revisions.payloadName(payload.getObjectClass());
        int revision = payload.getRevision();
        var step = steps.get(new Key(name, revision));
        if (step == null) return false;

        ArrayNode typed = parse(payload.getSerializedObject(), name, revision);
        String objectClass = payload.getObjectClass();
        while (step != null) {
            try {
                step.apply((ObjectNode) typed.get(1));
            } catch (RuntimeException e) {
                throw new UpcastException(name, revision, "upcaster " + step + " failed", e);
            }
            if (step.toClassName() != null) {
                typed.set(0, TextNode.valueOf(step.toClassName()));
                objectClass = "class " + step.toClassName();
            }
            name = step.toName();
            revision = step.toRevision();
            step = steps.get(new Key(name, revision));
        }
        try {
            payload.setSerializedObject(TREE_MAPPER.writeValueAsString(typed));
        } catch (Exception e) {
            throw new UpcastException(name, revision, "cannot write the upcast payload", e);
        }
        payload.setObjectClass(objectClass);
        payload.setRevision(revision);
        return true;
    }

    /** Upcasts the payload of {@code message} in place (see {@link #upcast(SerializedObject)}). */
    public boolean upcast(Message<?> message) {
        return message != null && upcast(message.getSerializedPayload());
    }

    /**
     * Upcasts a fetched event in place, its name included: a renamed event reaches the handlers
     * of its new name.
     */
    public boolean upcast(PublishedEvent event) {
        if (event == null || !upcast(event.getEventMessage())) return false;
        event.setEventName(event.getEventMessage().getEventName());
        return true;
    }

    /**
     * The payload names a step renames, directly or along its chain, to one of {@code handled},
     * each with the handled name it becomes: a bundle handling {@code OrderPlacedV2} also
     * receives what callers still send as {@code OrderPlaced}, and upcasts it on arrival.
     */
    public SortedMap<String, String> aliasesOf(Collection<String> handled) {
        var aliases = new TreeMap<String, String>();
        for (var start : steps.keySet()) {
            if (handled.contains(start.name())) continue;
            var key = start;
            while (steps.containsKey(key)) {
                var step = steps.get(key);
                key = new Key(step.toName(), step.toRevision());
            }
            if (handled.contains(key.name())) aliases.put(start.name(), key.name());
        }
        return aliases;
    }

    private static ArrayNode parse(String json, String name, int revision) {
        JsonNode tree;
        try {
            tree = TREE_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new UpcastException(name, revision, "the stored payload is not JSON", e);
        }
        // Default typing writes a payload as ["binary.class.Name", { fields }].
        if (tree instanceof ArrayNode array && array.size() == 2 && array.get(0).isTextual()
                && array.get(1).isObject()) {
            return array;
        }
        throw new UpcastException(name, revision, "the stored payload is not a typed JSON object", null);
    }

    @Override
    public String toString() {
        return "Upcasters" + new TreeSet<>(steps.values().stream().map(Upcaster::toString).toList());
    }
}
