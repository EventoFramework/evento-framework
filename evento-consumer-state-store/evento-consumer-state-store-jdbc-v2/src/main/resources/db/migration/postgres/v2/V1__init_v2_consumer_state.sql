-- Evento v2 consumer state schema (Postgres).
--
-- Tables created fresh by v2; v1 schema is untouched. All names are prefixed
-- with `evento_v2_` so a deployment can run side-by-side during a migration
-- soak.

-- 1. Per-consumer bookkeeping: checkpoint + version + admin (enabled / error)
CREATE TABLE IF NOT EXISTS evento_v2_consumer_state (
    consumer_id    TEXT         PRIMARY KEY,
    kind           VARCHAR(32)  NOT NULL,
    last_sequence  BIGINT       NOT NULL,
    version        BIGINT       NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    in_error       BOOLEAN      NOT NULL DEFAULT FALSE,
    error_start_at TIMESTAMPTZ,
    last_error_at  TIMESTAMPTZ,
    error_count    BIGINT       NOT NULL DEFAULT 0,
    error          TEXT,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. Saga instance storage with flat associations for fast lookup.
CREATE TABLE IF NOT EXISTS evento_v2_saga_state (
    id           BIGSERIAL    PRIMARY KEY,
    saga_name    VARCHAR(255) NOT NULL,
    state        JSONB        NOT NULL,
    associations JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ended        BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS evento_v2_saga_state_name_idx
    ON evento_v2_saga_state (saga_name);
CREATE INDEX IF NOT EXISTS evento_v2_saga_state_assoc_gin_idx
    ON evento_v2_saga_state USING gin (associations);

-- 3. Per-consumer dead-letter queue.
CREATE TABLE IF NOT EXISTS evento_v2_dead_event (
    consumer_id           TEXT         NOT NULL,
    event_sequence_number BIGINT       NOT NULL,
    event_name            VARCHAR(255) NOT NULL,
    aggregate_id          VARCHAR(255),
    context               VARCHAR(255),
    event                 JSONB        NOT NULL,
    exception             JSONB        NOT NULL,
    retry                 BOOLEAN      NOT NULL DEFAULT FALSE,
    dead_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_id, event_sequence_number)
);
CREATE INDEX IF NOT EXISTS evento_v2_dead_event_retry_idx
    ON evento_v2_dead_event (consumer_id, retry);

-- 4. Observer dedupe table.
CREATE TABLE IF NOT EXISTS evento_v2_dedupe (
    consumer_id TEXT        NOT NULL,
    event_id    TEXT        NOT NULL,
    claimed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_id, event_id)
);
CREATE INDEX IF NOT EXISTS evento_v2_dedupe_claimed_at_idx
    ON evento_v2_dedupe (claimed_at);
