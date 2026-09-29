package com.evento.lab.cluster.api;

/**
 * The context vocabulary of the lab, on top of the seven region contexts of
 * {@link Regions}.
 *
 * <h2>The {@code ALL} trap</h2>
 * {@link com.evento.common.utils.Context#ALL} is <b>not</b> a context called
 * "ALL": it is the <em>wildcard</em> {@code "*"}, which the event store turns
 * into {@code LIKE '%'}. A consumer registered for {@code Context.ALL}
 * receives every event of every context, every region's orders included.
 *
 * <p>The lab needs the opposite as well: a real, literal context that marks
 * events meant for the whole world (price lists, catalog, promotions),
 * consumed by exactly one lane and by nothing else. That context is
 * {@link #GLOBAL}, whose value happens to be the string {@code "ALL"}. Never
 * write {@code Context.ALL} where {@link #GLOBAL} is meant, and never write
 * {@code "*"} where {@code "ALL"} is meant:
 *
 * <table>
 *   <caption>What a consumer registered for each value receives</caption>
 *   <tr><th>value</th><th>constant</th><th>receives</th></tr>
 *   <tr><td>{@code "ALL"}</td><td>{@link #GLOBAL}</td><td>only events whose context is exactly {@code ALL}</td></tr>
 *   <tr><td>{@code "*"}</td><td>{@link #WILDCARD} (= {@code Context.WILDCARD}, deprecated alias {@code Context.ALL})</td><td>every event: all regions and {@code ALL}</td></tr>
 *   <tr><td>{@code "EUROPA"}</td><td>{@link Regions#ALL}</td><td>only EUROPA's events</td></tr>
 * </table>
 *
 * <p>{@code ALL} contains neither {@code *} nor {@code _}, so the event
 * store's LIKE match (G15) treats it as an exact match on every framework
 * version.
 */
public final class Contexts {

    /** The literal cross-region context: events for every region, consumed by one global lane. */
    public static final String GLOBAL = "ALL";

    /** The consumer-side wildcard {@code "*"}: every context. The same value as {@code Context.WILDCARD} (formerly {@code Context.ALL}). */
    public static final String WILDCARD = com.evento.common.utils.Context.WILDCARD;

    private Contexts() {
    }

    public static boolean isGlobal(String context) {
        return GLOBAL.equals(context);
    }

    public static boolean isWildcard(String context) {
        return WILDCARD.equals(context);
    }
}
