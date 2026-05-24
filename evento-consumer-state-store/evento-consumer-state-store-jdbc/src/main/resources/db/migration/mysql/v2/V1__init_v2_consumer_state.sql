-- Evento v2 consumer state schema (MySQL 8+).

CREATE TABLE IF NOT EXISTS evento_v2_consumer_state (
    consumer_id    VARCHAR(255) NOT NULL,
    kind           VARCHAR(32)  NOT NULL,
    last_sequence  BIGINT       NOT NULL,
    version        BIGINT       NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    in_error       BOOLEAN      NOT NULL DEFAULT FALSE,
    error_start_at TIMESTAMP    NULL,
    last_error_at  TIMESTAMP    NULL,
    error_count    BIGINT       NOT NULL DEFAULT 0,
    error          TEXT,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evento_v2_saga_state (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    saga_name    VARCHAR(255) NOT NULL,
    state        JSON         NOT NULL,
    associations JSON         NOT NULL,
    ended        BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX evento_v2_saga_state_name_idx (saga_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evento_v2_dead_event (
    consumer_id           VARCHAR(255) NOT NULL,
    event_sequence_number BIGINT       NOT NULL,
    event_name            VARCHAR(255) NOT NULL,
    aggregate_id          VARCHAR(255),
    context               VARCHAR(255),
    event                 JSON         NOT NULL,
    exception             JSON         NOT NULL,
    retry                 BOOLEAN      NOT NULL DEFAULT FALSE,
    dead_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_id, event_sequence_number),
    INDEX evento_v2_dead_event_retry_idx (consumer_id, retry)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evento_v2_dedupe (
    consumer_id VARCHAR(255) NOT NULL,
    event_id    VARCHAR(255) NOT NULL,
    claimed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_id, event_id),
    INDEX evento_v2_dedupe_claimed_at_idx (claimed_at)
) ENGINE=InnoDB;
