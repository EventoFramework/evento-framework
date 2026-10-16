package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.query.ConsistencyToken;

import java.time.Duration;
import java.util.Objects;

/**
 * How to ask a query, beyond the query itself. Immutable; start from {@link #defaults()}
 * and refine:
 *
 * <pre>{@code
 * gateway.queryWith(new FindOrder(id), QueryOptions.defaults().readYourWrites());
 * gateway.stream(new ExportOrders(customer), QueryOptions.defaults().timeout(Duration.ofMinutes(5)).direct());
 * }</pre>
 *
 * <ul>
 *   <li>{@link #timeout}: how long to wait for the answer; for a stream, for the first
 *       views and then between two batches.</li>
 *   <li>{@link #after(ConsistencyToken)} / {@link #readYourWrites()}: the writes the answer
 *       must reflect (read-your-writes), see {@link ConsistencyToken}.</li>
 *   <li>{@link #window(int)} (streams): how many frames may travel ahead of the caller's
 *       consumption; the flow-control credit.</li>
 *   <li>{@link #direct()} (streams): take the stream on a direct connection to the
 *       answering bundle when it offers one, instead of through the broker; falls back to
 *       the broker when it does not or when the connection cannot be made.</li>
 * </ul>
 */
public final class QueryOptions {

    /** Default stream window: frames in flight ahead of the consumer. */
    public static final int DEFAULT_WINDOW = 16;

    private static final QueryOptions DEFAULTS = new QueryOptions(null, null, ConsistencyToken.NONE, false,
            DEFAULT_WINDOW, false);

    private final Duration timeout;
    private final Metadata metadata;
    private final ConsistencyToken consistency;
    private final boolean readYourWrites;
    private final int window;
    private final boolean direct;

    private QueryOptions(Duration timeout, Metadata metadata, ConsistencyToken consistency, boolean readYourWrites,
                         int window, boolean direct) {
        this.timeout = timeout;
        this.metadata = metadata;
        this.consistency = consistency == null ? ConsistencyToken.NONE : consistency;
        this.readYourWrites = readYourWrites;
        this.window = window;
        this.direct = direct;
    }

    /** No metadata, the gateway's default timeout, no consistency requirement, a window of 16, via the broker. */
    public static QueryOptions defaults() {
        return DEFAULTS;
    }

    /** How long to wait for the answer (for a stream: for each batch). */
    public QueryOptions timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be > 0");
        return new QueryOptions(timeout, metadata, consistency, readYourWrites, window, direct);
    }

    public QueryOptions metadata(Metadata metadata) {
        return new QueryOptions(timeout, metadata, consistency, readYourWrites, window, direct);
    }

    /** The answer must reflect the writes of {@code token} (merged with any already required). */
    public QueryOptions after(ConsistencyToken token) {
        return new QueryOptions(timeout, metadata, consistency.merge(token), readYourWrites, window, direct);
    }

    /** The answer must reflect every write this bundle's commands made so far. */
    public QueryOptions readYourWrites() {
        return new QueryOptions(timeout, metadata, consistency, true, window, direct);
    }

    /** Stream window: frames that may travel ahead of the consumer (1..1024). */
    public QueryOptions window(int frames) {
        if (frames < 1 || frames > 1024) throw new IllegalArgumentException("window must be in 1..1024: " + frames);
        return new QueryOptions(timeout, metadata, consistency, readYourWrites, frames, direct);
    }

    /** Take a stream on a direct connection to the answering bundle when it offers one. */
    public QueryOptions direct() {
        return new QueryOptions(timeout, metadata, consistency, readYourWrites, window, true);
    }

    /** The timeout set, or null for the gateway's default. */
    public Duration getTimeout() { return timeout; }
    public Metadata getMetadata() { return metadata; }
    /** The writes required explicitly (without this bundle's own, see {@link #isReadYourWrites()}). */
    public ConsistencyToken getConsistency() { return consistency; }
    public boolean isReadYourWrites() { return readYourWrites; }
    public int getWindow() { return window; }
    public boolean isDirect() { return direct; }

    /** Whether the answer has to reflect some writes. */
    public boolean requiresConsistency() {
        return readYourWrites || !consistency.isEmpty();
    }

    /** {@link #getTimeout()} in milliseconds, or the gateway's default when unset. */
    public long timeoutMillis(Gateway gateway) {
        return timeout != null ? timeout.toMillis()
                : gateway.getDefaultTimeoutUnit().toMillis(gateway.getDefaultTimeoutTime());
    }

    @Override
    public String toString() {
        return "QueryOptions{timeout=" + timeout + ", consistency=" + consistency + ", readYourWrites=" + readYourWrites
                + ", window=" + window + ", direct=" + direct + '}';
    }
}
