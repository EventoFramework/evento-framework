-- Evento server: idempotency keys for commands (G5), for schemas managed outside the server
-- (spring.sql.init.mode=never). With spring.sql.init.mode=always the first server of the new
-- version does this at boot (AdvisoryLockedSchemaInitializer). Until the index, the
-- idempotency_command column and the claims table all exist, servers execute keyed commands
-- without exactly-once (event=idempotency_key_unsupported) and pick them up within a minute.
-- Run outside a transaction block (CREATE INDEX CONCURRENTLY cannot run inside one); reads and
-- appends continue during the index build. Existing rows have no key (NULL) and are outside the
-- partial index, so the build cannot fail on them.
-- The columns are nullable with no default: catalog-only changes that need ACCESS EXCLUSIVE for
-- an instant; the lock_timeout keeps them from queueing every query behind a long-running one.
-- Retry if one times out. Run it in the schema the server uses (its search_path).
SET lock_timeout = '3s';
ALTER TABLE es__events ADD COLUMN IF NOT EXISTS idempotency_key varchar(255);
ALTER TABLE es__events ADD COLUMN IF NOT EXISTS idempotency_command varchar(255);
RESET lock_timeout;
CREATE TABLE IF NOT EXISTS es__idempotency_claims
(
    idempotency_key varchar(255) NOT NULL PRIMARY KEY,
    command_type    varchar(255) NOT NULL,
    aggregate_id    varchar(255)          DEFAULT NULL,
    owner           varchar(255) NOT NULL,
    lease_until     timestamptz  NOT NULL,
    completed_at    timestamptz           DEFAULT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now()
);
-- If a previous attempt was interrupted, drop the INVALID leftover first:
--   DROP INDEX CONCURRENTLY IF EXISTS es__events_idempotency_key_uidx;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS es__events_idempotency_key_uidx
    ON es__events (idempotency_key) WHERE idempotency_key IS NOT NULL;
