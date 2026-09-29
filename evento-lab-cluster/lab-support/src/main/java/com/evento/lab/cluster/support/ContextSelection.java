package com.evento.lab.cluster.support;

import com.evento.lab.cluster.api.Contexts;
import com.evento.lab.cluster.api.Regions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The contexts a deployment consumes, parsed from {@code EVENTO_CONTEXTS}.
 *
 * <p>Syntax: a comma-separated list of region names ({@code EUROPA,AFRICA},
 * case-insensitive, {@code sud-america} accepted), the literal global context
 * {@code ALL}, or the single wildcard {@code *}. The bundle starts one
 * consumer engine per listed context for each consumer component
 * ({@code EventoBundle.Builder.setComponentContexts}), and each engine
 * fetches only its context's events.
 *
 * <p>{@code ALL} and {@code *} are different things (see {@link Contexts}):
 * {@code ALL} is a context of its own, carried by cross-region events;
 * {@code *} is every context. Writing {@code all} in lower case still means the
 * literal {@code ALL}, never the wildcard.
 *
 * <p>Rejected, so a typo can never become a silent lane that consumes
 * nothing or everything: an empty list, an unknown name, and {@code *}
 * combined with anything else.
 *
 * @param contexts the normalised contexts, in configuration order, no duplicates
 */
public record ContextSelection(List<String> contexts) {

    public ContextSelection {
        contexts = List.copyOf(contexts);
    }

    public static ContextSelection wildcard() {
        return new ContextSelection(List.of(Contexts.WILDCARD));
    }

    public static ContextSelection parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("EVENTO_CONTEXTS is empty: list regions (EUROPA,AFRICA), "
                    + Contexts.GLOBAL + " or " + Contexts.WILDCARD);
        }
        var out = new LinkedHashSet<String>();
        for (var part : raw.split(",")) {
            var token = part.trim();
            if (token.isEmpty()) continue;
            out.add(normalize(token));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("EVENTO_CONTEXTS has no context: '" + raw + "'");
        }
        if (out.contains(Contexts.WILDCARD) && out.size() > 1) {
            throw new IllegalArgumentException("EVENTO_CONTEXTS: '" + Contexts.WILDCARD
                    + "' already means every context and cannot be combined with others: '" + raw + "'");
        }
        return new ContextSelection(new ArrayList<>(out));
    }

    private static String normalize(String token) {
        if (token.equals(Contexts.WILDCARD)) return Contexts.WILDCARD;
        var upper = token.toUpperCase(Locale.ROOT).replace('-', '_');
        if (upper.equals(Contexts.GLOBAL)) return Contexts.GLOBAL;
        if (Regions.isRegion(upper)) return upper;
        throw new IllegalArgumentException("EVENTO_CONTEXTS: unknown context '" + token + "', expected one of "
                + Regions.ALL + ", " + Contexts.GLOBAL + " (the literal global context) or "
                + Contexts.WILDCARD + " (every context)");
    }

    public boolean isWildcard() {
        return contexts.equals(List.of(Contexts.WILDCARD));
    }

    public boolean includesGlobal() {
        return contexts.contains(Contexts.GLOBAL);
    }

    /**
     * The contexts for a component that consumes regional traffic: everything
     * configured except the literal {@code ALL}. The wildcard is kept.
     */
    public List<String> regional() {
        return contexts.stream().filter(c -> !Contexts.isGlobal(c)).toList();
    }

    /**
     * The contexts for a component that consumes only cross-region events:
     * {@code [ALL]} when {@code ALL} is listed explicitly, otherwise none. The
     * wildcard deliberately does not enable it: the global lane must be one
     * lane cluster-wide, so it runs only where an operator asked for it by name.
     */
    public List<String> global() {
        return includesGlobal() ? List.of(Contexts.GLOBAL) : List.of();
    }

    /** The regions this selection covers ({@link Regions#ALL} for the wildcard). */
    public List<String> regions() {
        if (isWildcard()) return Regions.ALL;
        return contexts.stream().filter(Regions::isRegion).toList();
    }

    @Override
    public String toString() {
        return String.join(",", contexts);
    }
}
