-- Console actions on a consumer connected to another server (G17): the server that receives the
-- action writes it here for the server that holds the consumer's instance (core__bundle_instance),
-- which runs it and writes the answer back. Servers never call each other: the database relays.
CREATE TABLE IF NOT EXISTS core__admin_relay
(
    id                 uuid         NOT NULL PRIMARY KEY,
    server_instance_id varchar(255) NOT NULL,
    consumer_id        varchar(1024) NOT NULL,
    body               text         NOT NULL,
    status             varchar(16)  NOT NULL DEFAULT 'PENDING',
    result             text                  DEFAULT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    completed_at       timestamptz           DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS core__admin_relay_pending_idx
    ON core__admin_relay (server_instance_id, created_at) WHERE status = 'PENDING';
