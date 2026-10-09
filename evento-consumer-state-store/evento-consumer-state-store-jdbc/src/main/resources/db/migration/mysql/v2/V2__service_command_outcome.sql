-- Evento v2: outcomes of keyed service commands (G5 residual window), kept by the service
-- bundle (JdbcServiceCommandOutcomeStore). One row per idempotency key: the command type that
-- owns the key and the handler's serialized result (NULL for a handler that returned nothing).
-- Idempotent (the index is declared inline, MySQL has no CREATE INDEX IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS evento_v2_service_outcome (
    idempotency_key VARCHAR(255) NOT NULL,
    command_type    VARCHAR(255) NOT NULL,
    result          LONGBLOB,
    recorded_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (idempotency_key),
    INDEX evento_v2_service_outcome_recorded_at_idx (recorded_at)
) ENGINE=InnoDB;
