-- Evento server: add PRIMARY KEY (event_sequence_number) to es__events, for schemas managed
-- outside the server (spring.sql.init.mode=never). With spring.sql.init.mode=always the first
-- server of the new version does this at boot (AdvisoryLockedSchemaInitializer).
-- Run outside a transaction block (CREATE INDEX CONCURRENTLY cannot run inside one); reads and
-- appends continue during the index build. First check for duplicates, which make it fail:
--   SELECT event_sequence_number, count(*) FROM es__events GROUP BY 1 HAVING count(*) > 1;
-- If a previous attempt was interrupted, drop the INVALID leftover first:
--   DROP INDEX CONCURRENTLY IF EXISTS es__events_seq_uidx;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS es__events_seq_uidx ON es__events (event_sequence_number);
-- Needs ACCESS EXCLUSIVE for an instant; the lock_timeout keeps it from queueing every query
-- behind a long-running one. Retry it if it times out.
SET lock_timeout = '3s';
ALTER TABLE es__events ADD CONSTRAINT es__events_pkey PRIMARY KEY USING INDEX es__events_seq_uidx;
RESET lock_timeout;
