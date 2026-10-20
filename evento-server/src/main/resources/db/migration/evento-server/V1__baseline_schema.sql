-- Evento server schema, version 1 (Evento 2.6.0): the schema.sql of 2.6.0, idempotent so that it also
-- adopts databases created by earlier servers (baselined at version 0). Never edit this file: Flyway
-- checks its checksum. A schema change is a new migration (V6__..., after the Java migrations V2-V5 in
-- AdvisoryLockedSchemaInitializer).

create table if not exists core__bundle
(
    id                     varchar(255) not null
        primary key,
    instance_id            varchar(255) null,
    contains_handlers      boolean      not null,
    description            text         null,
    detail                 text         null,
    line_prefix            varchar(255)         null,
    repository_url         text         null,
    updated_at             timestamp    null,
    version                bigint       not null
);

-- A component is identified per bundle: id = bundle_id || ':' || component_name (see
-- Component.idOf). The same class deployed in several bundles has one row per bundle.
-- Catalogs created before that keyed rows by component_name alone; the schema
-- initializer (AdvisoryLockedSchemaInitializer) migrates them in place.
create table if not exists core__component
(
    id             varchar(512) not null
        primary key,
    component_name varchar(255) not null,
    component_type varchar(255) null,
    description    text         null,
    detail         text         null,
    line           int          null,
    path           text         null,
    updated_at     timestamp    null,
    bundle_id      varchar(255) null,
    foreign key (bundle_id) references core__bundle (id)
);

create table if not exists core__consumer
(
    identifier varchar(512) not null
        primary key,
    component_id varchar(512) null,
    instance_id    varchar(255) null,
    consumer_id        varchar(255) null,
    foreign key (component_id) references core__component (id)
);

create table if not exists core__payload
(
    name                 varchar(255) not null
        primary key,
    description          text         null,
    detail               text         null,
    domain               varchar(255) null,
    is_valid_json_schema boolean      not null,
    json_schema          text         null,
    line                 int          null,
    path                 text         null,
    registered_in        varchar(255) null,
    type                 varchar(255) null,
    updated_at           timestamp    null
);

create table if not exists core__handler
(
    uuid                     varchar(255) not null
        primary key,
    association_property     varchar(255) null,
    handler_type             varchar(255) null,
    line                     int          null,
    executor                 varchar(255) null,
    return_is_multiple       boolean      not null,
    component_id             varchar(512) null,
    handled_payload_name     varchar(255) null,
    return_type_name         varchar(255) null,
    foreign key (return_type_name) references core__payload (name),
    foreign key (handled_payload_name) references core__payload (name),
    foreign key (component_id) references core__component (id)
);

create table if not exists core__handler__invocation
(
    handler_uuid     varchar(255) not null,
    invocations_name varchar(255) not null,
    invocations_key  int          not null,
    primary key (handler_uuid, invocations_key),
    foreign key (handler_uuid) references core__handler (uuid),
    foreign key (invocations_name) references core__payload (name)
);

-- Which evento-server holds the live session of each bundle instance. Several
-- servers share this catalog: when an instance fails over from server A to server B,
-- A notices the dead socket only later (heartbeat read-idle), and A's NodeLeft must
-- then leave B's fresh registration alone. Only this row can tell A that.
create table if not exists core__bundle_instance
(
    instance_id        varchar(255) not null
        primary key,
    bundle_id          varchar(255) not null,
    server_instance_id varchar(255) not null,
    -- timestamptz and written from the database clock: servers in different regions
    -- (JVM time zones, skewed clocks) compare these for liveness, see BundlePresence.
    updated_at         timestamptz  not null default now()
);

create index if not exists core__bundle_instance_bundle_id_index
    on core__bundle_instance (bundle_id);


CREATE TABLE IF NOT EXISTS int_lock
(
    lock_key     char(36)     NOT NULL,
    region       varchar(100) NOT NULL,
    client_id    char(36) DEFAULT NULL,
    created_date timestamp    NOT NULL,
    PRIMARY KEY (lock_key, region)
);

CREATE TABLE IF NOT EXISTS es__events
(
    event_sequence_number bigint       NOT NULL,
    context               varchar(100)          DEFAULT 'default',
    aggregate_id          varchar(100)          DEFAULT NULL,
    event_name            varchar(100) NOT NULL,
    created_at            timestamp    NOT NULL default  (now() at time zone 'utc'),
    event_message         text         NOT NULL,
    deleted_at            timestamp             DEFAULT NULL,
    -- The idempotency key of the command that produced the event (G5), NULL for none.
    -- Existing tables get the column, and every table its partial UNIQUE index
    -- es__events_idempotency_key_uidx, from AdvisoryLockedSchemaInitializer (built
    -- CONCURRENTLY, so a rolling upgrade keeps appending).
    idempotency_key       varchar(255)          DEFAULT NULL,
    -- The payload type of that command: with aggregate_id, the identity a retry must match
    -- (a key presented by another command is refused, never answered with this event).
    idempotency_command   varchar(255)          DEFAULT NULL,
    -- The aggregate's version after this event (C1): 1, 2, 3... per aggregate_id, NULL for
    -- events of service commands and of servers before the fence. The partial UNIQUE index
    -- es__events_aggregate_seq_uidx (aggregate_id, aggregate_seq), from
    -- AdvisoryLockedSchemaInitializer like the idempotency index, refuses a second append
    -- from the same story: the fence behind the aggregate lock.
    aggregate_seq         bigint                DEFAULT NULL
);

-- Idempotency claims (G5): one row per keyed service command while it executes (owner and a
-- lease on the database clock, so a duplicate waits on a row instead of pinning a lock
-- connection). Completed (completed_at) and kept once the event carrying the key is stored (in
-- the append statement itself) or the handler produced no event, so a later claim of the key
-- is refused by the row and needs no second lookup; deleted only after a failed execution.
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

CREATE TABLE IF NOT EXISTS es__snapshot
(
    aggregate_id          varchar(100) NOT NULL,
    aggregate_state       text,
    event_sequence_number bigint    DEFAULT NULL,
    updated_at            timestamp DEFAULT NULL,
    deleted_at            timestamp DEFAULT NULL,
    PRIMARY KEY (aggregate_id)
);

create table IF NOT EXISTS performance__handler_invocation_count
(
    id               varchar(255)               not null
        primary key,
    last_count       int              default 0 not null,
    mean_probability double precision default 0 not null
);

create table if NOT EXISTS performance__handler_service_time
(
    id                         varchar(255)               not null
        primary key,
    last_service_time          double precision default 0 not null,
    mean_service_time          double precision default 0 not null,
    aged_mean_arrival_interval double precision           not null,
    aged_mean_service_time     double precision           not null,
    count                      bigint                     not null,
    last_arrival               bigint                     not null,
    last_arrival_interval      bigint                     not null,
    max_arrival_interval       bigint                     not null,
    max_service_time           bigint                     not null,
    min_arrival_interval       bigint                     not null,
    min_service_time           bigint                     not null
);

create table if NOT EXISTS performance__handler_service_time_ts
(
    id        varchar(255)               not null,
    value     bigint default 0 not null,
    instance_id   varchar(255),
    timestamp bigint not null
);

create index if not exists performance__handler_service_time_ts_id_timestamp_index
    on performance__handler_service_time_ts (id asc, timestamp desc);

create table if NOT EXISTS performance__handler_invocation_count_ts
(
    id        varchar(255)               not null,
    instance_id   varchar(255),
    timestamp bigint not null
);

create index if not exists performance__handler_invocation_count_ts_id_timestamp_index
    on performance__handler_invocation_count_ts (id asc, timestamp desc);

create table if NOT EXISTS performance__aggregate_handler_invocation_count_ts
(
    id        varchar(255)               not null,
    aggregate_id        varchar(255)               not null,
    event_sequence_number bigint not null,
    instance_id   varchar(255),
    source_bundle_id   varchar(255),
    source_instance_id   varchar(255),
    timestamp bigint not null,
    total bigint not null,
    lock bigint not null,
    retrieve bigint not null,
    compute bigint not null,
    publish bigint not null
);

create index if not exists  performance__agg_handler_invocation_count_ts_timestamp_index
    on performance__aggregate_handler_invocation_count_ts (id asc, timestamp desc);



-- Add repository_url if upgrading from a schema created before this column existed
ALTER TABLE core__bundle ADD COLUMN IF NOT EXISTS repository_url text null;

-- Async consumers: name of the ConsumerExecutor an @EventHandler dispatches to ("" = inline)
ALTER TABLE core__handler ADD COLUMN IF NOT EXISTS executor varchar(255) null;

-- Drop deployment/autoscaling columns and tables if upgrading from a schema that still had them
ALTER TABLE core__bundle ADD COLUMN IF NOT EXISTS instance_id varchar(255) null;
ALTER TABLE core__bundle DROP COLUMN IF EXISTS artifact_coordinates;
ALTER TABLE core__bundle DROP COLUMN IF EXISTS artifact_original_name;
ALTER TABLE core__bundle DROP COLUMN IF EXISTS bucket_type;
ALTER TABLE core__bundle DROP COLUMN IF EXISTS autorun;
ALTER TABLE core__bundle DROP COLUMN IF EXISTS deployable;
DROP TABLE IF EXISTS core__bundle__environment;
DROP TABLE IF EXISTS core__bundle__vm_option;

create index if not exists es__events_aggregate_id_event_sequence_number_index
    on es__events (aggregate_id, event_sequence_number);

create index if not exists es__events_context_event_name_event_sequence_number_index
    on es__events (context, event_name, event_sequence_number);




create or replace view performance__handler_service_time_ts_agg
as select id,
          count(*),
          avg(value),
          (select sum(b * c)/sum(c)
           from (select floor(value / 3) * 3 as b, count(*) as c
                 from performance__handler_service_time_ts ts
                 where ts.id = ext.id
                 group by 1
                 order by 1) s) as w3_avg,
          (select sum(b * c)/sum(c)
           from (select floor(value / 5) * 5 as b, count(*) as c
                 from performance__handler_service_time_ts ts
                 where ts.id = ext.id
                 group by 1
                 order by 1) s) as w5_avg,
          stddev(value),
          min(value),
          max(value)
   from performance__handler_service_time_ts as ext
   group by id;

CREATE SEQUENCE IF NOT EXISTS event_sequence_number_serial START 1;