-- Evento server: re-key the component catalog per bundle (N1), for schemas managed outside
-- the server (spring.sql.init.mode=never). With spring.sql.init.mode=always the first server of
-- the new version does the same thing at boot (AdvisoryLockedSchemaInitializer); do not run
-- both. Run it once, with every evento server stopped or already on the new version, against
-- a catalog whose core__component is still keyed by component_name.
-- Requires PostgreSQL 12 or later.
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE core__component, core__handler, core__consumer IN ACCESS EXCLUSIVE MODE;
ALTER TABLE core__component ADD COLUMN id varchar(512);
UPDATE core__component SET id = coalesce(bundle_id, '') || ':' || component_name;
ALTER TABLE core__handler ADD COLUMN IF NOT EXISTS component_id varchar(512);
UPDATE core__handler t SET component_id = c.id FROM core__component c WHERE c.component_name = t.component_component_name;
ALTER TABLE core__handler DROP COLUMN IF EXISTS component_component_name;
ALTER TABLE core__consumer ADD COLUMN IF NOT EXISTS component_id varchar(512);
UPDATE core__consumer t SET component_id = c.id FROM core__component c WHERE c.component_name = t.component_component_name;
ALTER TABLE core__consumer DROP COLUMN IF EXISTS component_component_name;
ALTER TABLE core__component DROP CONSTRAINT IF EXISTS core__component_pkey;
ALTER TABLE core__component ALTER COLUMN id SET NOT NULL;
ALTER TABLE core__component ADD CONSTRAINT core__component_pkey PRIMARY KEY (id);
ALTER TABLE core__handler ADD CONSTRAINT core__handler_component_id_fkey FOREIGN KEY (component_id) REFERENCES core__component (id);
ALTER TABLE core__consumer ADD CONSTRAINT core__consumer_component_id_fkey FOREIGN KEY (component_id) REFERENCES core__component (id);
COMMIT;
CREATE UNIQUE INDEX IF NOT EXISTS core__component_bundle_name_uidx ON core__component (bundle_id, component_name);
CREATE INDEX IF NOT EXISTS core__handler_component_id_idx ON core__handler (component_id);
CREATE INDEX IF NOT EXISTS core__consumer_component_id_idx ON core__consumer (component_id);
