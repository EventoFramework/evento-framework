-- Evento v2: outcomes of keyed service commands (G5 residual window), kept by the service
-- bundle (JdbcServiceCommandOutcomeStore). One row per idempotency key: the command type that
-- owns the key and the handler's serialized result (NULL for a handler that returned nothing).
-- Idempotent, so it can also be run by hand on a database that already has the table.
CREATE TABLE IF NOT EXISTS evento_v2_service_outcome (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    command_type    VARCHAR(255) NOT NULL,
    result          BYTEA,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS evento_v2_service_outcome_recorded_at_idx
    ON evento_v2_service_outcome (recorded_at);
