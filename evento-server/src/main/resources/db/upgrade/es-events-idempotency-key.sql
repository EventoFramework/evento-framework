-- Evento server: idempotency keys for commands (G5), for schemas managed outside the server
-- (spring.sql.init.mode=never). With spring.sql.init.mode=always the first server of the new
-- version does this at boot (AdvisoryLockedSchemaInitializer). Until the index exists and is
-- valid, servers execute keyed commands without exactly-once (event=idempotency_key_unsupported)
-- and pick the index up within a minute of its creation.
-- Run outside a transaction block (CREATE INDEX CONCURRENTLY cannot run inside one); reads and
-- appends continue during the index build. Existing rows have no key (NULL) and are outside the
-- partial index, so the build cannot fail on them.
-- The column is nullable with no default: a catalog-only change that needs ACCESS EXCLUSIVE for an
-- instant; the lock_timeout keeps it from queueing every query behind a long-running one. Retry
-- it if it times out.
SET lock_timeout = '3s';
ALTER TABLE es__events ADD COLUMN IF NOT EXISTS idempotency_key varchar(255);
RESET lock_timeout;
-- If a previous attempt was interrupted, drop the INVALID leftover first:
--   DROP INDEX CONCURRENTLY IF EXISTS es__events_idempotency_key_uidx;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS es__events_idempotency_key_uidx
    ON es__events (idempotency_key) WHERE idempotency_key IS NOT NULL;
