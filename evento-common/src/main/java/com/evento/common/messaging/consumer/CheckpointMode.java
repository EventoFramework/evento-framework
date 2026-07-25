package com.evento.common.messaging.consumer;

/**
 * When a consumer using parallel handlers persists its checkpoint.
 *
 * <p>Only meaningful for handlers dispatched to a {@link ConsumerExecutor}; a fully inline
 * consumer completes each event before moving on, so both modes behave identically.
 */
public enum CheckpointMode {

    /**
     * Commit as soon as a task <b>starts</b>. The checkpoint tracks the dispatch frontier,
     * so nothing is ever re-fetched and a restart resumes exactly where the last cycle
     * stopped.
     *
     * <p>The cost is that events whose handler was mid-flight when the JVM died are already
     * checkpointed and never redelivered — <b>at-most-once for the in-flight window</b>. A
     * graceful stop drains and loses nothing; a {@code kill -9} loses up to the executor's
     * capacity. This is the default because it never replays.
     */
    ON_START,

    /**
     * Commit the highest <b>contiguous completed</b> sequence, i.e. the point below which
     * every event is finished. Events still running sit above the watermark and are
     * redelivered after a crash, restoring <b>at-least-once</b>.
     *
     * <p>The fetch cursor still follows the dispatch frontier in memory, so no event is
     * processed twice within a run; only a crash replays, and only the window between the
     * watermark and the frontier (bounded by executor capacity). Duplicates on replay are
     * exactly what the idempotent handlers this feature targets already tolerate.
     *
     * <p>Two things to know. A handler that never returns pins the watermark, so the
     * persisted checkpoint stops advancing while the in-memory cursor runs on — the
     * processor logs when that gap grows. And because the watermark lags, the dashboard's
     * "last event" figure trails the true progress by up to the in-flight window.
     */
    WATERMARK
}
