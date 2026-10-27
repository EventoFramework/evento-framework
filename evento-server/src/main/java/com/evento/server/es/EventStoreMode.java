package com.evento.server.es;

/**
 * How the event store numbers events ({@code evento.es.mode}).
 */
public enum EventStoreMode {
    /**
     * Consecutive numbers from one PostgreSQL sequence under the global {@code es-lock}: sequence
     * order is commit order, so consumers read every event as soon as it is committed, on any
     * number of servers. The default.
     */
    CPES,
    /**
     * Snowflake ids taken by each server from its clock, committed in parallel; consumers read
     * only ids older than {@code evento.es.fetch.delay} and skip an event committed later than
     * that after its id was taken.
     *
     * @deprecated since 3.0. On one server CPES is as fast (benchmark B1, with group commit);
     * with several servers APES consumers can skip events (several clocks, MAC-derived node ids,
     * a commit slower than the fetch delay), and its only measured advantage, parallel commits
     * under synchronous replication (B2), needs that. An APES store switches to CPES when every
     * server is restarted with {@code evento.es.mode=CPES}: the sequence goes on above the last
     * APES id. To be removed in a later major version.
     */
    @Deprecated(since = "3.0")
    APES
}
