-- The events a command handler declares it may produce (3.0.1): an aggregate handler returning
-- DomainEvents lists them in produces, any other command handler its return type. The catalog's
-- interaction graph and the performance model draw one edge per row; a handler with no row (an
-- older bundle) falls back on core__handler.return_type_name.
CREATE TABLE IF NOT EXISTS core__handler__produced_event
(
    handler_uuid        varchar(255) NOT NULL,
    produced_event_name varchar(255) NOT NULL,
    PRIMARY KEY (handler_uuid, produced_event_name),
    FOREIGN KEY (handler_uuid) REFERENCES core__handler (uuid),
    FOREIGN KEY (produced_event_name) REFERENCES core__payload (name)
);
