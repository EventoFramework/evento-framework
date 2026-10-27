-- Evento server: the aggregate fence (C1), for schemas managed outside the server
-- (spring.sql.init.mode=never). With spring.sql.init.mode=always the first server of the new
-- version does this at boot (AdvisoryLockedSchemaInitializer). Until the column and the index
-- both exist, servers append aggregate events unfenced (event=aggregate_fence_unsupported), as
-- before, and pick the fence up within a minute.
-- Run outside a transaction block (CREATE INDEX CONCURRENTLY cannot run inside one); reads and
-- appends continue during the index build. Existing rows have no version (NULL) and are outside
-- the partial index, so the build cannot fail on them.
-- The column is nullable with no default: a catalog-only change that needs ACCESS EXCLUSIVE for
-- an instant; the lock_timeout keeps it from queueing every query behind a long-running one.
-- Retry if it times out. Run it in the schema the server uses (its search_path).
SET lock_timeout = '3s';
ALTER TABLE es__events ADD COLUMN IF NOT EXISTS aggregate_seq bigint;
RESET lock_timeout;
-- If a previous attempt was interrupted, drop the INVALID leftover first:
--   DROP INDEX CONCURRENTLY IF EXISTS es__events_aggregate_seq_uidx;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS es__events_aggregate_seq_uidx
    ON es__events (aggregate_id, aggregate_seq) WHERE aggregate_seq IS NOT NULL;
