package com.evento.common.utils;

/**
 * Well-known event context values.
 *
 * <p>A context is a plain string carried by every event ({@code Event#getContext}). A
 * consumer (projector, saga, observer) reads the contexts configured for it with
 * {@code EventoBundle.Builder.setComponentContexts}; each configured value is either an
 * exact context name or a pattern in which {@code *} stands for any run of characters.
 *
 * <p><b>{@code "*"} versus {@code "ALL"}.</b> {@link #WILDCARD} ({@code "*"}) is the
 * pattern that matches every context. A context literally named {@code "ALL"} is just a
 * context like {@code "EUROPA"}: a consumer configured with {@code "ALL"} reads only the
 * events published with context {@code "ALL"}. Applications that want a context for
 * cross-region events (a global price list, say) can name it {@code "ALL"}, but must
 * declare that name as a constant of their own and never use {@link #ALL} for it, which
 * despite its name is the wildcard.
 */
public class Context {
    /**
     * The context of an event whose publisher did not set one ({@code "default"}).
     */
    public static final String DEFAULT = "default";

    /**
     * The pattern that matches every context: {@code "*"}. A consumer configured with it
     * reads all events regardless of their context, and is what a consumer with no
     * configured contexts reads.
     */
    public static final String WILDCARD = "*";

    /**
     * The wildcard {@code "*"}, under its historical name.
     *
     * @deprecated This is <b>not</b> a context named {@code "ALL"}: it is {@link #WILDCARD},
     * the pattern that matches every context. Configuring a consumer with it reads all
     * events; publishing an event with it gives the event the literal context {@code "*"}.
     * Use {@link #WILDCARD} for the pattern, and a constant of your own (for example
     * {@code GLOBAL = "ALL"}) for a context literally named {@code "ALL"}.
     */
    @Deprecated(since = "2.5.0")
    public static final String ALL = WILDCARD;
}
