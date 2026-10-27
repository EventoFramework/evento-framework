-- Evento server 3.0: the table of data subject keys (@PersonalData), for schemas managed outside
-- the server (spring.sql.init.mode=never). With spring.sql.init.mode=always the server creates it
-- (Flyway V7). Safe to run more than once.
-- Keys of data subjects (3.0, crypto-shredding). The bundles encrypt the @PersonalData fields of
-- payloads with the key of their subject; forgetting the subject deletes its row, and every copy of
-- those fields can no longer be read. Every server reads and writes the keys here: none keeps a key
-- the others do not see. key_material is wrapped with evento.server.personal-data.master-key when
-- that is set (wrapped = true).
CREATE TABLE IF NOT EXISTS es__subject_keys
(
    subject      varchar(1024) NOT NULL PRIMARY KEY,
    key_material bytea         NOT NULL,
    wrapped      boolean       NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now()
);
