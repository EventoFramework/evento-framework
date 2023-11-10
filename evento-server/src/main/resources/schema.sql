create table if not exists core__bundle
(
    id                     varchar(255) not null
        primary key,
    artifact_coordinates   varchar(255) null,
    artifact_original_name varchar(255) null,
    autorun                boolean      not null,
    bucket_type            varchar(255) null,
    contains_handlers      boolean      not null,
    description            text         null,
    detail                 text         null,
    max_instances          int          not null,
    min_instances          int          not null,
    updated_at             timestamp    null,
    version                bigint       not null
);

create table if not exists core__bundle__environment
(
    bundle_id       varchar(255) not null,
    environment     varchar(255) null,
    environment_key varchar(255) not null,
    primary key (bundle_id, environment_key),
    foreign key (bundle_id) references core__bundle (id)
);

create table if not exists core__bundle__vm_option
(
    bundle_id      varchar(255) not null,
    vm_options     varchar(255) null,
    vm_options_key varchar(255) not null,
    primary key (bundle_id, vm_options_key),
    foreign key (bundle_id) references core__bundle (id)
);

create table if not exists core__component
(
    component_name varchar(255) not null
        primary key,
    component_type varchar(255) null,
    description    text         null,
    detail         text         null,
    line           int          null,
    path           varchar(255) null,
    updated_at     timestamp    null,
    bundle_id      varchar(255) null,
    foreign key (bundle_id) references core__bundle (id)
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
    path                 varchar(255) null,
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
    return_is_multiple       boolean      not null,
    component_component_name varchar(255) null,
    handled_payload_name     varchar(255) null,
    return_type_name         varchar(255) null,
    foreign key (return_type_name) references core__payload (name),
    foreign key (handled_payload_name) references core__payload (name),
    foreign key (component_component_name) references core__component (component_name)
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
    created_at            timestamp    NOT NULL default current_timestamp,
    event_message         text         NOT NULL,
    deleted_at            timestamp             DEFAULT NULL
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
    timestamp timestamp        default current_timestamp
);

create table if NOT EXISTS performance__handler_invocation_count_ts
(
    id        varchar(255)               not null,
    timestamp timestamp        default current_timestamp
);


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