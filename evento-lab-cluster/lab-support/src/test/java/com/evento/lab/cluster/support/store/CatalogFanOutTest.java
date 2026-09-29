package com.evento.lab.cluster.support.store;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fan-out loop and its idempotency contract, over in-memory targets that
 * apply the same rule as {@link CatalogStore}'s SQL (write only when the event
 * sequence is above the stored one). {@link CatalogStoreIT} checks the SQL
 * itself on a real Postgres.
 */
class CatalogFanOutTest {

    /** A regional database in memory: sku -> last applied seq, plus counters; can be "down". */
    static final class MemoryTarget implements PriceTarget {
        final String name;
        final Map<String, Long> lastSeq = new HashMap<>();
        final Map<String, Integer> applied = new HashMap<>();
        final Map<String, Integer> stale = new HashMap<>();
        boolean down;

        MemoryTarget(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Outcome apply(PriceChange c) throws Exception {
            if (down) throw new java.sql.SQLTransientConnectionException(name + " is down");
            var stored = lastSeq.get(c.sku());
            if (stored != null && stored >= c.seq()) {
                stale.merge(c.sku(), 1, Integer::sum);
                return Outcome.STALE;
            }
            lastSeq.put(c.sku(), c.seq());
            applied.merge(c.sku(), 1, Integer::sum);
            return Outcome.APPLIED;
        }
    }

    private final MemoryTarget eu = new MemoryTarget("eu");
    private final MemoryTarget am = new MemoryTarget("am");
    private final MemoryTarget apac = new MemoryTarget("apac");
    private final CatalogFanOut fanOut = new CatalogFanOut(List.of(eu, am, apac));

    private static PriceChange change(long seq, String sku) {
        return new PriceChange(seq, sku, 100 + seq, "EUR", "ALL");
    }

    @Test
    void reachesEveryTargetOnce() throws Exception {
        var r = fanOut.apply(change(10, "sku-1"));
        assertThat(r.outcomes()).containsExactly(
                Map.entry("eu", PriceTarget.Outcome.APPLIED),
                Map.entry("am", PriceTarget.Outcome.APPLIED),
                Map.entry("apac", PriceTarget.Outcome.APPLIED));
        for (var t : List.of(eu, am, apac)) {
            assertThat(t.applied).containsEntry("sku-1", 1);
            assertThat(t.lastSeq).containsEntry("sku-1", 10L);
        }
    }

    @Test
    void aRedeliveredEventIsStaleEverywhere() throws Exception {
        fanOut.apply(change(10, "sku-1"));
        var replay = fanOut.apply(change(10, "sku-1"));
        assertThat(replay.outcomes().values()).containsOnly(PriceTarget.Outcome.STALE);
        for (var t : List.of(eu, am, apac)) {
            assertThat(t.applied).containsEntry("sku-1", 1);
            assertThat(t.stale).containsEntry("sku-1", 1);
        }
    }

    @Test
    void anOlderEventNeverOverwritesANewerOne() throws Exception {
        fanOut.apply(change(20, "sku-1"));
        var older = fanOut.apply(change(15, "sku-1"));
        assertThat(older.outcomes().values()).containsOnly(PriceTarget.Outcome.STALE);
        assertThat(eu.lastSeq).containsEntry("sku-1", 20L);
    }

    @Test
    void oneTargetDownServesTheOthersAndFailsTheEventForRedelivery() throws Exception {
        apac.down = true;
        assertThatThrownBy(() -> fanOut.apply(change(30, "sku-2")))
                .isInstanceOf(CatalogFanOut.FanOutIncompleteException.class)
                .hasMessageContaining("[apac]")
                .satisfies(e -> {
                    var result = ((CatalogFanOut.FanOutIncompleteException) e).result();
                    assertThat(result.outcomes()).containsOnlyKeys("eu", "am");
                    assertThat(result.failures()).containsOnlyKeys("apac");
                    assertThat(e.getSuppressed()).hasSize(1);
                });
        // the healthy regions already have it
        assertThat(eu.applied).containsEntry("sku-2", 1);
        assertThat(am.applied).containsEntry("sku-2", 1);
        assertThat(apac.applied).doesNotContainKey("sku-2");

        // the lane retries the same event while apac is down: nothing is written twice
        assertThatThrownBy(() -> fanOut.apply(change(30, "sku-2")))
                .isInstanceOf(CatalogFanOut.FanOutIncompleteException.class);

        // apac heals: the redelivery completes it, still exactly once per target
        apac.down = false;
        var healed = fanOut.apply(change(30, "sku-2"));
        assertThat(healed.outcomes()).containsExactly(
                Map.entry("eu", PriceTarget.Outcome.STALE),
                Map.entry("am", PriceTarget.Outcome.STALE),
                Map.entry("apac", PriceTarget.Outcome.APPLIED));
        for (var t : List.of(eu, am, apac)) {
            assertThat(t.applied).containsEntry("sku-2", 1);
            assertThat(t.lastSeq).containsEntry("sku-2", 30L);
        }
        assertThat(eu.stale).containsEntry("sku-2", 2);
    }

    @Test
    void attemptNeverThrows() {
        eu.down = true;
        am.down = true;
        apac.down = true;
        var r = fanOut.attempt(change(1, "sku-3"));
        assertThat(r.complete()).isFalse();
        assertThat(r.failures()).containsOnlyKeys("eu", "am", "apac");
        assertThat(fanOut.targetNames()).containsExactly("eu", "am", "apac");
    }

    @Test
    void needsATarget() {
        assertThatThrownBy(() -> new CatalogFanOut(List.of())).hasMessageContaining("no fan-out target");
    }
}
