# S10: Cold start of 3 servers on an empty event store

- Label: `before`
- Gaps exercised: G14
- Started: 2026-09-29T12:28:35.284327800Z, finished: 2026-09-29T12:31:15.558124500Z (160 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s10-before-260686-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- iterations: `3`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| all three servers start, every time | **KNOWN_DEFECT_REPRODUCED** | 3/3 servers healthy on every iteration, no DDL race in the logs | occasional boot failures with pg_type_typname_nsp_index / tuple concurrently updated (G14) | 1/3 iterations with a failed server; 20 schema-race log lines |

- _all three servers start, every time_: A clean run does not prove absence of the race; raise S10_ITERATIONS for evidence.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "iterations" : [ {
    "servers" : {
      "evento-eu" : {
        "waitedSeconds" : 22,
        "state" : "running",
        "markers" : {
          "duplicate key" : 9
        },
        "ready" : true
      },
      "evento-am" : {
        "waitedSeconds" : 5,
        "state" : "running",
        "markers" : { },
        "ready" : true
      },
      "evento-apac" : {
        "waitedSeconds" : 0,
        "state" : "running",
        "markers" : {
          "deadlock detected" : 1
        },
        "ready" : true
      },
      "allStarted" : true
    },
    "iteration" : 1
  }, {
    "servers" : {
      "evento-eu" : {
        "waitedSeconds" : 22,
        "state" : "running",
        "markers" : { },
        "ready" : true
      },
      "evento-am" : {
        "waitedSeconds" : 0,
        "state" : "exited",
        "markers" : {
          "pg_type_typname_nsp_index" : 1,
          "duplicate key" : 1,
          "ScriptStatementFailedException" : 1
        },
        "ready" : false
      },
      "evento-apac" : {
        "waitedSeconds" : 0,
        "state" : "running",
        "markers" : {
          "duplicate key" : 4,
          "deadlock detected" : 3
        },
        "ready" : true
      },
      "allStarted" : false
    },
    "iteration" : 2
  }, {
    "servers" : {
      "evento-eu" : {
        "waitedSeconds" : 22,
        "state" : "running",
        "markers" : { },
        "ready" : true
      },
      "evento-am" : {
        "waitedSeconds" : 5,
        "state" : "running",
        "markers" : { },
        "ready" : true
      },
      "evento-apac" : {
        "waitedSeconds" : 0,
        "state" : "running",
        "markers" : { },
        "ready" : true
      },
      "allStarted" : true
    },
    "iteration" : 3
  } ]
}
```

## Timeline

- +5,9 s: scenario start
- +6,0 s: iteration 1: stop servers
- +17,0 s: iteration 1: start the 3 servers together on an empty schema
- +46,0 s: iteration 1: allStarted=true
- +46,0 s: iteration 2: stop servers
- +57,0 s: iteration 2: start the 3 servers together on an empty schema
- +80,3 s: iteration 2: evento-am failed to start; restarting it
- +103,2 s: iteration 2: allStarted=false
- +103,2 s: iteration 3: stop servers
- +114,1 s: iteration 3: start the 3 servers together on an empty schema
- +142,5 s: iteration 3: allStarted=true
- +142,5 s: cleanup start
- +142,5 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +160,3 s: cleanup done

### iteration 1 evento-eu

```
20260929122914657 WARN  1 --- [bus-business-5                          ] error                                    : ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
```

### iteration 1 evento-apac

```
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
```

### iteration 2 evento-am

```
Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
Caused by: org.springframework.jdbc.datasource.init.ScriptStatementFailedException: Failed to execute SQL script statement #10 of URL [jar:nested:/app/app.jar/!BOOT-INF/classes/!/schema.sql]: create table IF NOT EXISTS performance__handler_invocation_count ( id varchar(255) not null primary key, last_count int default 0 not null, mean_probability double precision default 0 not null )
```

### iteration 2 evento-apac

```
20260929123000332 WARN  1 --- [bus-business-10                         ] error                                    : ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: duplicate key value violates unique constraint "core__bundle_pkey"
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
```

### stack actions

```
2026-09-29T12:28:41.238751200Z docker stop evento-am
2026-09-29T12:28:41.238751200Z docker stop evento-apac
2026-09-29T12:28:41.238751200Z docker stop evento-eu
2026-09-29T12:28:51.950255800Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T12:28:52.266474700Z docker start evento-am
2026-09-29T12:28:52.266474700Z docker start evento-eu
2026-09-29T12:28:52.266985200Z docker start evento-apac
2026-09-29T12:29:21.299917500Z docker stop evento-am
2026-09-29T12:29:21.299917500Z docker stop evento-apac
2026-09-29T12:29:21.299917500Z docker stop evento-eu
2026-09-29T12:29:32.040930300Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T12:29:32.321798500Z docker start evento-am
2026-09-29T12:29:32.321798500Z docker start evento-apac
2026-09-29T12:29:32.321798500Z docker start evento-eu
2026-09-29T12:29:55.627664800Z docker restart evento-am
2026-09-29T12:30:18.488122400Z docker stop evento-am
2026-09-29T12:30:18.488630100Z docker stop evento-apac
2026-09-29T12:30:18.488630100Z docker stop evento-eu
2026-09-29T12:30:29.067662500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T12:30:29.335847200Z docker start evento-am
2026-09-29T12:30:29.335847200Z docker start evento-apac
2026-09-29T12:30:29.335847200Z docker start evento-eu
2026-09-29T12:30:57.812673900Z docker stop gateway-america
2026-09-29T12:30:57.812673900Z docker stop gateway-sud-america
2026-09-29T12:30:57.812673900Z docker stop order-command-am
2026-09-29T12:30:57.812673900Z docker stop order-command-eu
2026-09-29T12:30:57.812673900Z docker stop order-query-eu
2026-09-29T12:30:57.812673900Z docker stop order-query-global-eu
2026-09-29T12:30:57.812673900Z docker stop order-query-wildcard
2026-09-29T12:30:57.813189100Z docker stop gateway-europa
2026-09-29T12:30:57.813189100Z docker stop order-command-apac
2026-09-29T12:30:57.813189100Z docker stop order-observer
2026-09-29T12:30:57.813189100Z docker stop order-query-cina
2026-09-29T12:30:57.813189100Z docker stop order-saga-eu
2026-09-29T12:30:57.813467Z docker stop order-query-apac
2026-09-29T12:30:57.817514100Z docker stop order-query-global-apac
2026-09-29T12:30:57.879369100Z docker stop gateway-africa
2026-09-29T12:30:57.889358800Z docker stop order-saga-apac
2026-09-29T12:30:57.900307300Z docker stop gateway-cina
2026-09-29T12:30:57.917307Z docker stop order-query-am
2026-09-29T12:30:57.943425900Z docker stop gateway-india
2026-09-29T12:30:57.949327600Z docker stop order-saga-am
2026-09-29T12:30:58.050126400Z docker stop gateway-australia-nz
2026-09-29T12:31:09.874901700Z docker start order-command-eu
2026-09-29T12:31:09.874901700Z docker start order-observer
2026-09-29T12:31:09.874901700Z docker start order-query-cina
2026-09-29T12:31:09.874901700Z docker start order-query-global-eu
2026-09-29T12:31:09.874901700Z docker start order-query-wildcard
2026-09-29T12:31:09.875435900Z docker start order-query-eu
2026-09-29T12:31:09.875435900Z docker start order-saga-eu
2026-09-29T12:31:09.875945700Z docker start gateway-africa
2026-09-29T12:31:09.881048300Z docker start order-command-am
2026-09-29T12:31:09.885385Z docker start order-query-am
2026-09-29T12:31:09.892418700Z docker start gateway-europa
2026-09-29T12:31:09.898419500Z docker start order-saga-am
2026-09-29T12:31:09.905692700Z docker start gateway-sud-america
2026-09-29T12:31:09.913692700Z docker start order-command-apac
2026-09-29T12:31:09.922808800Z docker start gateway-america
2026-09-29T12:31:09.931237800Z docker start order-query-apac
2026-09-29T12:31:09.939068200Z docker start order-query-global-apac
2026-09-29T12:31:09.948153400Z docker start order-saga-apac
2026-09-29T12:31:09.957608100Z docker start gateway-cina
2026-09-29T12:31:09.964946400Z docker start gateway-australia-nz
2026-09-29T12:31:09.977980200Z docker start gateway-india
```

