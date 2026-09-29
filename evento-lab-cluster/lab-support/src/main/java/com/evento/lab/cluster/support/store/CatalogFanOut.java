package com.evento.lab.cluster.support.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propagates one cross-region ({@code ALL}) event to every regional database.
 *
 * <p>The global lane is the only consumer of context {@code ALL} in the
 * cluster, so this is the only writer of {@code catalog_price} anywhere, and
 * every regional database gets its copy from here. Every target is attempted
 * even when an earlier one fails, so the healthy regions are served at once;
 * then, if any target failed, {@link FanOutIncompleteException} is thrown so
 * the consumer does not checkpoint the event and redelivers it. On the
 * redelivery the targets that already have it answer
 * {@link PriceTarget.Outcome#STALE}: exactly once per target, at the price of
 * a lane that stalls for <em>every</em> region while <em>one</em> region's
 * database is down (the trade-off S16 records).
 */
public class CatalogFanOut {

    private static final Logger log = LoggerFactory.getLogger(CatalogFanOut.class);

    /** Per target: {@code APPLIED}, {@code STALE} or the failure. */
    public record Result(Map<String, PriceTarget.Outcome> outcomes, Map<String, Exception> failures) {

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    /** Some targets did not get the event; the consumer must retry it. */
    public static class FanOutIncompleteException extends Exception {

        private final transient Result result;

        public FanOutIncompleteException(Result result) {
            super("fan-out incomplete, failed targets " + result.failures().keySet() + ", done " + result.outcomes());
            this.result = result;
            result.failures().values().forEach(this::addSuppressed);
        }

        public Result result() {
            return result;
        }
    }

    private final List<PriceTarget> targets;

    public CatalogFanOut(List<? extends PriceTarget> targets) {
        if (targets.isEmpty()) throw new IllegalArgumentException("no fan-out target");
        this.targets = List.copyOf(targets);
    }

    public List<String> targetNames() {
        return targets.stream().map(PriceTarget::name).toList();
    }

    /** Apply to every target; the result says what each one did. Never throws. */
    public Result attempt(PriceChange change) {
        var outcomes = new LinkedHashMap<String, PriceTarget.Outcome>();
        var failures = new LinkedHashMap<String, Exception>();
        for (var t : targets) {
            try {
                outcomes.put(t.name(), t.apply(change));
            } catch (Exception e) {
                failures.put(t.name(), e);
            }
        }
        return new Result(outcomes, failures);
    }

    /** Apply to every target; throw if any of them failed, after trying them all. */
    public Result apply(PriceChange change) throws FanOutIncompleteException {
        var result = attempt(change);
        log.info("event=lab_fanout seq={} sku={} outcomes={} failed={}", change.seq(), change.sku(),
                result.outcomes(), result.failures().keySet());
        if (!result.complete()) {
            throw new FanOutIncompleteException(result);
        }
        return result;
    }
}
